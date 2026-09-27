package dev.imkdw.claudewatch.notify

import dev.imkdw.claudewatch.domain.DisplayState
import kotlinx.serialization.Serializable

sealed interface Alert {
    val account: String

    /** F9: 세션 80%, 95% 도달 */
    data class Threshold(override val account: String, val threshold: Int, val pct: Int) : Alert

    /** F10: 100%를 찍은 세션이 리셋됨 */
    data class Reset(override val account: String) : Alert
}

/** 계정 하나의 현재 세션에서 이미 보낸 알림 */
@Serializable
data class AlertEntry(val sessionKey: String, val notified: Int = 0, val hit100: Boolean = false)

@Serializable
data class AlertMemory(val entries: Map<String, AlertEntry> = emptyMap())

object AlertPolicy {
    private val THRESHOLDS = listOf(95, 80)

    /**
     * 계정별, 세션당 한 번씩. 여러 단계를 한 번에 넘으면 가장 높은 단계만 보낸다.
     * 첫 실행에 이미 넘어 있으면 그 단계 알림 1개 (계획 AL7).
     * [sessionKey]는 계정 라벨 + session.resetsAt이라 리셋되면 바뀐다.
     */
    fun decide(prev: AlertMemory, state: DisplayState, sessionKey: String): Pair<List<Alert>, AlertMemory> {
        val label = state.account ?: return emptyList<Alert>() to prev
        val pct = state.sessionPct ?: return emptyList<Alert>() to prev
        val alerts = mutableListOf<Alert>()
        val previous = prev.entries[label]
        val newSession = previous != null && previous.sessionKey != sessionKey

        // Mac이 꺼져 있으면 새 resetsAt이 안 오므로, 워치가 계산한 리셋도 리셋으로 본다
        val wasReset = previous != null && previous.hit100 && (newSession || state.sessionResetPassed)
        if (wasReset) alerts += Alert.Reset(label)

        var entry = when {
            previous == null || newSession -> AlertEntry(sessionKey)
            wasReset -> previous.copy(hit100 = false)
            else -> previous
        }
        val threshold = THRESHOLDS.firstOrNull { pct >= it && it > entry.notified }
        if (threshold != null) {
            alerts += Alert.Threshold(label, threshold, pct)
            entry = entry.copy(notified = threshold)
        }
        if (pct >= 100) entry = entry.copy(hit100 = true)

        return alerts to prev.copy(entries = prev.entries + (label to entry))
    }
}
