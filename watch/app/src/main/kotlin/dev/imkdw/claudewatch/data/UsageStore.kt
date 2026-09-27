package dev.imkdw.claudewatch.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.imkdw.claudewatch.notify.AlertMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import java.time.Instant

/** 계정별 캐시 + 선택 계정 + ETag. 타일과 컴플리케이션은 이것만 읽는다 */
class UsageStore(private val dataStore: DataStore<Preferences>) {

    data class Stored(
        val accounts: Map<String, UsageFile>,
        val selected: String?,
        val etag: String?,
        val lastFetchAt: Instant?,
    )

    val data: Flow<Stored> = dataStore.data.map { prefs ->
        val accounts = prefs.asMap().mapNotNull { (key, value) ->
            val label = key.name.removePrefix(ACCOUNT_PREFIX).takeIf { key.name.startsWith(ACCOUNT_PREFIX) }
            val file = (value as? String)?.let(GistParser::parseFile)
            if (label != null && file != null) label to file else null
        }.toMap()
        Stored(accounts, prefs[SELECTED], prefs[ETAG], prefs[LAST_FETCH_AT]?.let(Instant::ofEpochMilli))
    }

    suspend fun current(): Stored = data.first()

    /** Gist 스냅샷으로 통째로 바꾼다. Gist에서 사라진 계정은 지운다 */
    suspend fun saveSnapshot(accounts: Map<String, UsageFile>, etag: String?, fetchedAt: Instant) {
        dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(ACCOUNT_PREFIX) }.forEach { prefs.remove(it) }
            for ((label, file) in accounts) {
                prefs[stringPreferencesKey(ACCOUNT_PREFIX + label)] = UsageJson.encodeToString(UsageFile.serializer(), file)
            }
            if (etag != null) prefs[ETAG] = etag else prefs.remove(ETAG)
            prefs[LAST_FETCH_AT] = fetchedAt.toEpochMilli()
        }
    }

    suspend fun select(label: String) {
        dataStore.edit { it[SELECTED] = label }
    }

    suspend fun alertMemory(): AlertMemory {
        val json = dataStore.data.first()[ALERTS] ?: return AlertMemory()
        return try {
            UsageJson.decodeFromString(AlertMemory.serializer(), json)
        } catch (_: SerializationException) {
            AlertMemory()
        }
    }

    suspend fun saveAlertMemory(memory: AlertMemory) {
        dataStore.edit { it[ALERTS] = UsageJson.encodeToString(AlertMemory.serializer(), memory) }
    }

    private companion object {
        const val ACCOUNT_PREFIX = "account:"
        val SELECTED = stringPreferencesKey("selected")
        val ETAG = stringPreferencesKey("etag")
        val LAST_FETCH_AT = longPreferencesKey("lastFetchAt")
        val ALERTS = stringPreferencesKey("alerts")
    }
}
