package me.foxtails.palustris.ui.session

import kotlinx.coroutines.CoroutineDispatcher
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.AuthGateway
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.auth.DraftWriteAuthority
import me.foxtails.palustris.data.auth.InMemoryDraftStore
import me.foxtails.palustris.data.auth.SessionLifecycle
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.directmessages.DirectMessageWriteAuthority
import me.foxtails.palustris.data.directmessages.InMemoryDirectMessageStore
import me.foxtails.palustris.data.emoji.InMemoryEmojiCatalogRepository
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.notifications.NoOpNotificationStreamController
import me.foxtails.palustris.data.notifications.NoOpNotificationSyncController
import me.foxtails.palustris.data.notifications.NotificationSyncController
import me.foxtails.palustris.data.notifications.push.NoOpPushRegistrationManager
import me.foxtails.palustris.data.preferences.InMemoryEmojiPickerPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.data.transport.HttpClientPool
import me.foxtails.palustris.ui.UiStrings

internal fun accountManagerFixture(
    store: SessionStore,
    auth: AuthGateway,
    ioDispatcher: CoroutineDispatcher,
    draftStore: DraftStore = InMemoryDraftStore(),
    draftWriteAuthority: DraftWriteAuthority = DraftWriteAuthority(),
    notificationSync: NotificationSyncController = NoOpNotificationSyncController(),
    uiStrings: UiStrings = UiStrings.Default,
    capabilityCache: CapabilityCache = CapabilityCache(),
    sourceFactory: SocialSourceFactory = SocialSourceFactory(HttpClientPool(), store, capabilityCache),
): AccountManager = AccountManager(
    auth = auth,
    lifecycle = SessionLifecycle(
        store = store,
        sourceFactory = sourceFactory,
        notificationSync = notificationSync,
        push = NoOpPushRegistrationManager(),
        streams = NoOpNotificationStreamController(),
        postPreferences = InMemoryPostPreferencesRepository(),
        photoGridPreferences = InMemoryPhotoGridPreferencesRepository(),
        directMessages = InMemoryDirectMessageStore(),
        directMessageWriters = DirectMessageWriteAuthority(),
        emoji = InMemoryEmojiCatalogRepository(),
        emojiPicker = InMemoryEmojiPickerPreferencesRepository(),
        drafts = draftStore,
        draftWriters = draftWriteAuthority,
        capabilityCache = capabilityCache,
        io = ioDispatcher,
    ),
    uiStrings = uiStrings,
)
