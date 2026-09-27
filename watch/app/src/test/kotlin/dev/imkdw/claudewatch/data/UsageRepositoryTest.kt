package dev.imkdw.claudewatch.data

import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.testing.FakeGistSource
import dev.imkdw.claudewatch.testing.Fixtures
import dev.imkdw.claudewatch.testing.tempDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UsageRepositoryTest {
    private val store = UsageStore(tempDataStore())
    private val now = Instant.parse("2026-09-27T12:10:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val body = Fixtures.text("gist-response.json")

    @Test
    fun `RP1 Updated면 계정을 저장하고 etag를 갱신한다`() = runTest {
        val repo = UsageRepository(FakeGistSource(FetchResult.Updated(body, "e1")), store, clock)
        assertThat(repo.refresh()).isEqualTo(RefreshOutcome.Updated)
        val s = store.current()
        assertThat(s.accounts.keys).containsExactly("personal", "work")
        assertThat(s.etag).isEqualTo("e1")
        assertThat(s.lastFetchAt).isEqualTo(now)
    }

    @Test
    fun `RP2 NotModified면 store 변화 없음, 다음 요청에 etag를 보낸다`() = runTest {
        val gist = FakeGistSource(FetchResult.Updated(body, "e1"), FetchResult.NotModified)
        val repo = UsageRepository(gist, store, clock)
        repo.refresh()
        val before = store.current()
        assertThat(repo.refresh()).isEqualTo(RefreshOutcome.Unchanged)
        assertThat(store.current()).isEqualTo(before)
        assertThat(gist.etags).containsExactly(null, "e1").inOrder()
    }

    @Test
    fun `RP3 Failed면 캐시를 유지한다`() = runTest {
        val gist = FakeGistSource(FetchResult.Updated(body, "e1"), FetchResult.Failed(FailReason.NETWORK))
        val repo = UsageRepository(gist, store, clock)
        repo.refresh()
        val before = store.current()
        assertThat(repo.refresh()).isEqualTo(RefreshOutcome.Failed(FailReason.NETWORK))
        assertThat(store.current()).isEqualTo(before)
    }

    @Test
    fun `RP3 Gist 응답 형식이 깨지면 PARSE 실패, 캐시 유지`() = runTest {
        val gist = FakeGistSource(FetchResult.Updated(body, "e1"), FetchResult.Updated("<html>", "e2"))
        val repo = UsageRepository(gist, store, clock)
        repo.refresh()
        assertThat(repo.refresh()).isEqualTo(RefreshOutcome.Failed(FailReason.PARSE))
        assertThat(store.current().accounts.keys).containsExactly("personal", "work")
        assertThat(store.current().etag).isEqualTo("e1")
    }

    @Test
    fun `RP4 select 후 observe에 선택 라벨이 반영된다`() = runTest {
        val repo = UsageRepository(FakeGistSource(FetchResult.Updated(body, "e1")), store, clock)
        repo.refresh()
        // 선택 전에는 changedAt 최신(personal 12:05 > work 11:40)
        assertThat(repo.observe().first().selected).isEqualTo("personal")
        repo.select("work")
        val snap = repo.observe().first()
        assertThat(snap.selected).isEqualTo("work")
        assertThat(snap.selectedFile?.account).isEqualTo("work")
    }

    @Test
    fun `캐시가 비었으면 etag를 보내지 않는다 (304로 빈 화면 방지)`() = runTest {
        store.saveSnapshot(emptyMap(), "stale", now)
        val gist = FakeGistSource(FetchResult.Updated(body, "e1"))
        UsageRepository(gist, store, clock).refresh()
        assertThat(gist.etags).containsExactly(null)
    }
}
