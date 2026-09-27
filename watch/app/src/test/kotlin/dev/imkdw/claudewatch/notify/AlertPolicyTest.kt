package dev.imkdw.claudewatch.notify

import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.domain.DisplayState
import dev.imkdw.claudewatch.domain.Level
import org.junit.Test

class AlertPolicyTest {
    private val s1 = "personal:2026-09-27T15:00:00Z"
    private val s2 = "personal:2026-09-27T20:00:00Z"

    private fun state(pct: Int?, account: String? = "personal", resetPassed: Boolean = false) =
        DisplayState(account, pct, null, 10, null, emptyList(), "", Level.NORMAL, sessionResetPassed = resetPassed)

    private fun feed(vararg steps: Pair<DisplayState, String>, start: AlertMemory = AlertMemory()): Pair<List<Alert>, AlertMemory> {
        var memory = start
        val all = mutableListOf<Alert>()
        for ((st, key) in steps) {
            val (alerts, next) = AlertPolicy.decide(memory, st, key)
            all += alerts
            memory = next
        }
        return all to memory
    }

    @Test
    fun `AL1 70에서 82로 오르면 80 알림 1개`() {
        val (alerts, _) = feed(state(70) to s1, state(82) to s1)
        assertThat(alerts).containsExactly(Alert.Threshold("personal", 80, 82))
    }

    @Test
    fun `AL2 같은 세션에서 82에서 85는 알림 없음`() {
        val (_, memory) = feed(state(70) to s1, state(82) to s1)
        val (alerts, _) = feed(state(85) to s1, start = memory)
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `AL3 70에서 96으로 뛰면 95 알림만`() {
        val (alerts, _) = feed(state(70) to s1, state(96) to s1)
        assertThat(alerts).containsExactly(Alert.Threshold("personal", 95, 96))
    }

    @Test
    fun `AL3 80 알림 뒤 95를 넘으면 95 알림`() {
        val (alerts, _) = feed(state(82) to s1, state(97) to s1, state(99) to s1)
        assertThat(alerts).containsExactly(Alert.Threshold("personal", 80, 82), Alert.Threshold("personal", 95, 97)).inOrder()
    }

    @Test
    fun `AL4 새 세션에서 81이면 80 알림 다시`() {
        val (alerts, _) = feed(state(90) to s1, state(81) to s2)
        assertThat(alerts).containsExactly(Alert.Threshold("personal", 80, 90), Alert.Threshold("personal", 80, 81)).inOrder()
    }

    @Test
    fun `AL5 100을 찍은 세션이 새 세션으로 바뀌면 리셋 알림`() {
        val (alerts, _) = feed(state(100) to s1, state(3) to s2)
        assertThat(alerts).containsExactly(Alert.Threshold("personal", 95, 100), Alert.Reset("personal")).inOrder()
    }

    @Test
    fun `AL5 Mac이 꺼져 워치가 리셋을 계산해도 리셋 알림 1번`() {
        val (alerts, _) = feed(
            state(100) to s1,
            state(0, resetPassed = true) to s1,
            state(0, resetPassed = true) to s1,
            state(2) to s2,
        )
        assertThat(alerts).containsExactly(Alert.Threshold("personal", 95, 100), Alert.Reset("personal")).inOrder()
    }

    @Test
    fun `100을 못 찍은 세션은 리셋 알림 없음`() {
        val (alerts, _) = feed(state(96) to s1, state(0, resetPassed = true) to s1, state(1) to s2)
        assertThat(alerts).containsExactly(Alert.Threshold("personal", 95, 96))
    }

    @Test
    fun `AL6 계정별로 따로 판정한다`() {
        val work = "work:2026-09-27T15:00:00Z"
        val (alerts, memory) = feed(state(90) to s1, state(50, account = "work") to work)
        assertThat(alerts).containsExactly(Alert.Threshold("personal", 80, 90))
        val (more, _) = feed(state(85, account = "work") to work, start = memory)
        assertThat(more).containsExactly(Alert.Threshold("work", 80, 85))
    }

    @Test
    fun `AL7 첫 실행에 이미 90이면 80 알림 1개`() {
        val (alerts, _) = feed(state(90) to s1)
        assertThat(alerts).containsExactly(Alert.Threshold("personal", 80, 90))
    }

    @Test
    fun `데이터가 없으면 알림도 기억 변화도 없다`() {
        val memory = AlertMemory()
        assertThat(AlertPolicy.decide(memory, state(null), s1)).isEqualTo(emptyList<Alert>() to memory)
        assertThat(AlertPolicy.decide(memory, DisplayState.Empty, s1)).isEqualTo(emptyList<Alert>() to memory)
    }
}
