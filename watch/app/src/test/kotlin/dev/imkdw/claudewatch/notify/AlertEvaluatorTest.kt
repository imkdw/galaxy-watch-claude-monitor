package dev.imkdw.claudewatch.notify

import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.data.UsageStore
import dev.imkdw.claudewatch.data.Window
import dev.imkdw.claudewatch.testing.tempDataStore
import dev.imkdw.claudewatch.testing.usageFile
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class AlertEvaluatorTest {
    private val store = UsageStore(tempDataStore())
    private val posted = mutableListOf<List<Alert>>()
    private val clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val evaluator = AlertEvaluator(store, { posted += it }, clock)

    private fun file(account: String, pct: Int, reset: String = "2026-09-27T15:00:00Z") =
        usageFile(account = account, session = Window(pct, reset))

    @Test
    fun `모든 계정을 판정하고 기억을 저장해 같은 알림을 반복하지 않는다`() = runTest {
        evaluator.check(mapOf("personal" to file("personal", 82), "work" to file("work", 96)))
        assertThat(posted.single()).containsExactly(Alert.Threshold("personal", 80, 82), Alert.Threshold("work", 95, 96))

        evaluator.check(mapOf("personal" to file("personal", 84), "work" to file("work", 97)))
        assertThat(posted).hasSize(1)
        assertThat(store.alertMemory().entries.keys).containsExactly("personal", "work")
    }

    @Test
    fun `파일의 account가 아니라 Gist 라벨로 기억한다`() = runTest {
        evaluator.check(mapOf("side" to file("personal", 90)))
        assertThat(posted.single()).containsExactly(Alert.Threshold("side", 80, 90))
    }

    @Test
    fun `사라진 계정의 기억은 지운다`() = runTest {
        evaluator.check(mapOf("personal" to file("personal", 10), "work" to file("work", 10)))
        evaluator.check(mapOf("work" to file("work", 10)))
        assertThat(store.alertMemory().entries.keys).containsExactly("work")
        assertThat(posted).isEmpty()
    }

    @Test
    fun `세션 키는 라벨과 session resetsAt`() {
        assertThat(AlertEvaluator.sessionKey("personal", file("personal", 1))).isEqualTo("personal:2026-09-27T15:00:00Z")
        assertThat(AlertEvaluator.sessionKey("x", usageFile(session = null))).isEqualTo("x:")
    }
}
