package me.foxtails.palustris.data.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.data.notifications.NotificationSyncController
import me.foxtails.palustris.data.notifications.push.PushRegistrationManager
import me.foxtails.palustris.data.directmessages.DirectMessageStore
import me.foxtails.palustris.data.directmessages.DirectMessageWriteAuthority
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.domain.PhotoGridPreferencesRepository
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiCatalogRepository
import me.foxtails.palustris.domain.EmojiPickerPreferencesRepository
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.PushSessionState
import me.foxtails.palustris.di.IoDispatcher
import org.json.JSONObject

/** Owns durable account transitions and the resources that follow a session lifetime. */
@Singleton
class SessionLifecycle @Inject constructor(
    private val store: SessionStore,
    private val sourceFactory: SocialSourceFactory,
    private val notificationSync: NotificationSyncController,
    private val push: PushRegistrationManager,
    private val streams: NotificationStreamController,
    private val postPreferences: PostPreferencesRepository,
    private val photoGridPreferences: PhotoGridPreferencesRepository,
    private val directMessages: DirectMessageStore,
    private val directMessageWriters: DirectMessageWriteAuthority,
    private val emoji: EmojiCatalogRepository,
    private val emojiPicker: EmojiPickerPreferencesRepository,
    private val drafts: DraftStore,
    private val draftWriters: DraftWriteAuthority,
    private val capabilityCache: CapabilityCache,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val transitions = Mutex()

    sealed interface Failure {
        data class Storage(val cause: Exception) : Failure
        data class AccountMissing(val accountId: AccountId) : Failure
    }

    data class Activation(
        val session: Session,
        val account: Account,
        val source: SocialSource,
        val registryToken: NotificationSyncToken,
        val directMessageGeneration: Long,
        val draftGeneration: Long,
    )

    data class Restoration(
        val index: AccountIndex,
        val active: Activation?,
        val pending: PendingLogin?,
    )

    data class LoginResult(val index: AccountIndex, val activation: Activation)
    data class SwitchResult(val index: AccountIndex, val activation: Activation)
    data class RemovalResult(val index: AccountIndex, val next: Activation?)
    data class ProfileResult(val index: AccountIndex, val account: Account)

    suspend fun readPending(now: Long = System.currentTimeMillis()): PendingLogin? = withContext(io) {
        store.readPending()?.takeIf { it.isFresh(now) }
    }

    suspend fun writePending(value: PendingLogin) = withContext(io) { store.writePending(value) }

    suspend fun clearPending() = withContext(io) { store.clearPending() }

    suspend fun restore(): Restoration = transitions.withLock {
        try {
            withContext(io) {
                val index = store.readIndex()
                val activeId = index.activeAccountId ?: index.accounts.firstOrNull()?.accountId
                val sessions = index.accounts.mapNotNull { store.read(it.accountId) }
                val registrations = sessions.associate { it.accountId to register(it) }
                val activeSession = activeId?.let { id -> sessions.firstOrNull { it.accountId == id } }
                val active = activeSession?.let {
                    activateExisting(
                        registrations.getValue(it.accountId),
                        index.accounts.firstOrNull { ref -> ref.accountId == it.accountId }?.toAccount() ?: fallback(it),
                    )
                }
                Restoration(index.copy(activeAccountId = activeId), active, store.readPending()?.takeIf { it.isFresh(System.currentTimeMillis()) })
            }
        } catch (e: Exception) {
            throw LifecycleException(Failure.Storage(e), e)
        }
    }

    suspend fun login(result: LoginSession): LoginResult = transitions.withLock {
        withContext(io) {
            val account = result.account
            val (session, index) = store.transaction {
                val previous = store.read(account.id)
                val session = Session(
                    accountId = account.id,
                    token = result.token,
                    capabilities = result.capabilities.copy(canPublish = result.capabilities.canPublish || result.canPublish),
                    access = result.access,
                    pushInstanceName = previous?.pushInstanceName,
                    sessionRevision = (previous?.sessionRevision ?: 0L) + 1L,
                    pushState = previous?.pushState ?: PushSessionState(),
                )
                store.write(account.id, session)
                store.writeProfile(account.id, result.user)
                val updatedIndex = store.readIndex().withAccount(account).copy(activeAccountId = account.id)
                store.writeIndex(updatedIndex)
                store.clearPending()
                session to updatedIndex
            }
            LoginResult(index, activate(session, account))
        }
    }

    suspend fun switch(accountId: AccountId): SwitchResult? = transitions.withLock {
        withContext(io) {
            val session = store.read(accountId) ?: throw LifecycleException(
                Failure.AccountMissing(accountId),
                IllegalStateException("The account is not available."),
            )
            val index = store.readIndex().copy(activeAccountId = accountId)
            store.writeIndex(index)
            SwitchResult(index, activate(session, index.accounts.firstOrNull { it.accountId == accountId }?.toAccount() ?: fallback(session)))
        }
    }

    suspend fun remove(accountId: AccountId): RemovalResult = transitions.withLock {
        withContext(io) {
            streams.stop(accountId)
            push.disable(accountId)
            notificationSync.removeAccount(accountId)
            capabilityCache.invalidate(accountId)
            postPreferences.remove(accountId)
            photoGridPreferences.remove(accountId)
            emoji.remove(accountId)
            emojiPicker.remove(accountId)
            directMessageWriters.invalidateAndDelete(accountId) { directMessages.delete(accountId) }
            draftWriters.invalidateAndDelete(accountId) { drafts.deleteAll(accountId) }
            val (index, next) = store.transaction {
                val old = store.readIndex()
                store.delete(accountId)
                val accounts = old.accounts.filterNot { it.accountId == accountId }
                val removedActive = old.activeAccountId == accountId
                val nextId = if (removedActive) accounts.firstOrNull()?.accountId else old.activeAccountId
                val index = old.copy(accounts = accounts, activeAccountId = nextId)
                store.writeIndex(index)
                val nextSession = if (removedActive) {
                    nextId?.let { id -> store.read(id)?.let { it to accounts.first { ref -> ref.accountId == id }.toAccount() } }
                } else {
                    null
                }
                index to nextSession
            }
            RemovalResult(index, next?.let { (session, account) -> activate(session, account) })
        }
    }

    suspend fun updateProfile(account: Account): ProfileResult? = transitions.withLock {
        withContext(io) {
            store.transaction {
                val index = store.readIndex()
                if (index.accounts.none { it.accountId == account.id }) return@transaction null
                val updated = index.withAccount(account)
                store.writeIndex(updated)
                store.writeProfile(account.id, account.toProfileJson())
                ProfileResult(updated, account)
            }
        }
    }

    private fun register(session: Session): Activation {
        val source = sourceFactory.create(session)
        val token = notificationSync.register(session.accountId, source)
        push.onSessionAvailable(session.accountId)
        return Activation(session, fallback(session), source, token, 0, 0)
    }

    private suspend fun activate(session: Session, account: Account): Activation {
        val source = sourceFactory.create(session)
        val token = notificationSync.register(session.accountId, source)
        push.onSessionAvailable(session.accountId)
        return Activation(session, account, source, token, directMessageWriters.activate(session.accountId), draftWriters.activate(session.accountId))
    }

    private suspend fun activateExisting(registration: Activation, account: Account): Activation = registration.copy(
        account = account,
        directMessageGeneration = directMessageWriters.activate(registration.session.accountId),
        draftGeneration = draftWriters.activate(registration.session.accountId),
    )

    private fun fallback(session: Session) = Account(session.accountId, session.accountId.localId, session.accountId.localId)
}

class LifecycleException(val failure: SessionLifecycle.Failure, cause: Exception) : Exception(cause)

private fun AccountIndex.withAccount(account: Account): AccountIndex = copy(accounts = accounts.filterNot { it.accountId == account.id } + AccountRef(
    accountId = account.id, handle = account.handle, avatarUrl = account.avatarUrl, displayName = account.displayName,
    biography = account.biography, profileFields = account.profileFields, bannerUrl = account.bannerUrl,
    followersCount = account.followersCount, followingCount = account.followingCount, postsCount = account.postsCount,
    locked = account.locked, bot = account.bot,
))

private fun Account.toProfileJson() = JSONObject().apply {
    put("id", id.localId); put("username", handle.removePrefix("@").substringBefore('@'))
    put("host", handle.removePrefix("@").substringAfter('@', id.connection.origin.removePrefix("https://")))
    put("name", displayName); put("display_name", displayName); put("description", biography); put("note", biography)
    put("followersCount", followersCount); put("followingCount", followingCount); put("notesCount", postsCount)
    put("statuses_count", postsCount); put("isLocked", locked); put("isBot", bot)
    put("fields", org.json.JSONArray(profileFields.map { JSONObject().put("name", it.name).put("value", it.value) }))
    avatarUrl?.let { put("avatarUrl", it); put("avatar", it) }
    put("bannerUrl", bannerUrl)
    bannerUrl?.let { put("banner", it) }
}
