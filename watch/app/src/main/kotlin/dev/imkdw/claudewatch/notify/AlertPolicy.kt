package dev.imkdw.claudewatch.notify

import dev.imkdw.claudewatch.domain.DisplayState
import kotlinx.serialization.Serializable

sealed interface Alert {
    val account: String

    data class Threshold(override val account: String, val threshold: Int, val pct: Int) : Alert

    data class Reset(override val account: String) : Alert
}

@Serializable
data class AlertEntry(val sessionKey: String, val notified: Int = 0, val hit100: Boolean = false)

@Serializable
data class AlertMemory(val entries: Map<String, AlertEntry> = emptyMap())

object AlertPolicy {
    private val THRESHOLDS = listOf(95, 80)

    fun decide(prev: AlertMemory, state: DisplayState, sessionKey: String): Pair<List<Alert>, AlertMemory> {
        val label = state.account ?: return emptyList<Alert>() to prev
        val pct = state.sessionPct ?: return emptyList<Alert>() to prev
        val alerts = mutableListOf<Alert>()
        val previous = prev.entries[label]
        val newSession = previous != null && previous.sessionKey != sessionKey

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
