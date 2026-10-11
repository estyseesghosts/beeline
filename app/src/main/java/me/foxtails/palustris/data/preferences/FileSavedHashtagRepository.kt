package me.foxtails.palustris.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.SavedHashtagRepository
import me.foxtails.palustris.domain.SavedHashtags
import me.foxtails.palustris.domain.hashtagBody
import me.foxtails.palustris.domain.hashtagIdentity
import org.json.JSONArray
import org.json.JSONObject

/** Stores validated, non-secret saved hashtags in no-backup storage. */
@Singleton
class FileSavedHashtagRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SavedHashtagRepository {
    private val file = File(context.noBackupFilesDir, "photo-grid-preferences.json")
    private val mutex = Mutex()
    private val values = MutableStateFlow<Map<String, SavedHashtags>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loaded = false

    init {
        scope.launch { mutex.withLock { loadLocked() } }
    }

    override fun observe(accountId: AccountId): Flow<SavedHashtags> = values
        .map { it[keyFor(accountId)] ?: SavedHashtags() }
        .distinctUntilChanged()

    override suspend fun update(
        accountId: AccountId,
        transform: (SavedHashtags) -> SavedHashtags,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val key = keyFor(accountId)
            val next = normalize(transform(values.value[key] ?: SavedHashtags()))
            val updated = values.value.toMutableMap().apply { put(key, next) }.toMap()
            persist(updated)
            values.value = updated
        }
    }

    override suspend fun remove(accountId: AccountId) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val key = keyFor(accountId)
            if (key !in values.value) return@withLock
            val updated = values.value.toMutableMap().apply { remove(key) }.toMap()
            persist(updated)
            values.value = updated
        }
    }

    private fun loadLocked() {
        if (loaded) return
        val loadedValues = runCatching {
            if (!file.exists()) return@runCatching emptyMap()
            val accounts = JSONObject(file.readText(Charsets.UTF_8)).optJSONObject("accounts") ?: return@runCatching emptyMap()
            buildMap {
                accounts.keys().forEach { key ->
                    val tags = accounts.optJSONObject(key)?.optJSONArray("hashtags")?.let(::validHashtags).orEmpty()
                    if (tags.isNotEmpty()) put(key, SavedHashtags(tags))
                }
            }
        }.getOrDefault(emptyMap())
        values.value = loadedValues
        loaded = true
    }

    private fun persist(values: Map<String, SavedHashtags>) {
        file.parentFile?.mkdirs()
        val accounts = JSONObject()
        values.forEach { (key, preferences) ->
            accounts.put(key, JSONObject().put("hashtags", JSONArray(preferences.tags)))
        }
        val root = JSONObject().put("version", 1).put("accounts", accounts)
        val temporary = File("${file.path}.new")
        FileOutputStream(temporary).use { stream ->
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private fun keyFor(accountId: AccountId): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        "${accountId.connection.origin}\u0000${accountId.connection.protocol.name}\u0000${accountId.localId}"
            .toByteArray(Charsets.UTF_8),
    )
}

/** Stores a tag without its `#`. A value saved before this rule keeps its mark on disk and loads without it. */
private fun storedTag(value: String): String? = runCatching { hashtagBody(value) }.getOrNull()

private fun normalize(preferences: SavedHashtags): SavedHashtags = SavedHashtags(
    preferences.tags.mapNotNull(::storedTag).distinctBy(::hashtagIdentity),
)

private fun validHashtags(values: JSONArray): List<String> = (0 until values.length())
    .mapNotNull { index -> values.optString(index).takeIf(String::isNotBlank) }
    .mapNotNull(::storedTag)
    .distinctBy(::hashtagIdentity)

class InMemorySavedHashtagRepository : SavedHashtagRepository {
    private val values = MutableStateFlow<Map<AccountId, SavedHashtags>>(emptyMap())
    private val mutex = Mutex()

    override fun observe(accountId: AccountId): Flow<SavedHashtags> = values
        .map { it[accountId] ?: SavedHashtags() }
        .distinctUntilChanged()

    override suspend fun update(
        accountId: AccountId,
        transform: (SavedHashtags) -> SavedHashtags,
    ) = mutex.withLock {
        val next = normalize(transform(values.value[accountId] ?: SavedHashtags()))
        values.value = values.value + (accountId to next)
    }

    override suspend fun remove(accountId: AccountId) = mutex.withLock {
        values.value = values.value - accountId
    }
}
