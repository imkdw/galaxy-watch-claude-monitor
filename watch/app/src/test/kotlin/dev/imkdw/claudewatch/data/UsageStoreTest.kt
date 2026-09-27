package dev.imkdw.claudewatch.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.testing.tempDataStore
import dev.imkdw.claudewatch.testing.usageFile
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class UsageStoreTest {
    private val dataStore = tempDataStore()
    private val store = UsageStore(dataStore)
    private val personal = usageFile(account = "personal")
    private val work = usageFile(account = "work", session = null)
    private val t = Instant.parse("2026-09-27T12:00:00Z")

    @Test
    fun `DS1 계정 2개를 저장하고 읽는다`() = runTest {
        store.saveSnapshot(mapOf("personal" to personal, "work" to work), "etag-1", t)
        val s = store.current()
        assertThat(s.accounts).containsExactly("personal", personal, "work", work)
        assertThat(s.etag).isEqualTo("etag-1")
        assertThat(s.lastFetchAt).isEqualTo(t)
    }

    @Test
    fun `DS2 새 스냅샷에서 사라진 계정은 지운다`() = runTest {
        store.saveSnapshot(mapOf("personal" to personal, "work" to work), "e1", t)
        store.saveSnapshot(mapOf("work" to work), null, t)
        val s = store.current()
        assertThat(s.accounts.keys).containsExactly("work")
        assertThat(s.etag).isNull()
    }

    @Test
    fun `DS3 선택 계정 저장과 읽기`() = runTest {
        assertThat(store.current().selected).isNull()
        store.select("work")
        assertThat(store.current().selected).isEqualTo("work")
    }

    @Test
    fun `DS4 깨진 JSON은 그 계정만 무시한다`() = runTest {
        store.saveSnapshot(mapOf("personal" to personal), null, t)
        dataStore.edit { it[stringPreferencesKey("account:broken")] = "{nope" }
        assertThat(store.current().accounts.keys).containsExactly("personal")
    }
}
