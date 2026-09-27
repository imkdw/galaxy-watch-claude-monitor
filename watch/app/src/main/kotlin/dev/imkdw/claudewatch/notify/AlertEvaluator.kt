package dev.imkdw.claudewatch.notify

import dev.imkdw.claudewatch.data.UsageFile
import dev.imkdw.claudewatch.data.UsageStore
import dev.imkdw.claudewatch.domain.DisplayCalculator
import java.time.Clock

fun interface AlertCheck {
    suspend fun check(accounts: Map<String, UsageFile>)
}

fun interface AlertSink {
    fun post(alerts: List<Alert>)
}

class AlertEvaluator(
    private val store: UsageStore,
    private val sink: AlertSink,
    private val clock: Clock,
) : AlertCheck {

    override suspend fun check(accounts: Map<String, UsageFile>) {
        var memory = store.alertMemory()
        val alerts = mutableListOf<Alert>()
        for ((label, file) in accounts) {
            val state = DisplayCalculator.toDisplay(file, clock.instant(), clock.zone).copy(account = label)
            val (found, next) = AlertPolicy.decide(memory, state, sessionKey(label, file))
            alerts += found
            memory = next
        }
        store.saveAlertMemory(AlertMemory(memory.entries.filterKeys { it in accounts }))
        if (alerts.isNotEmpty()) sink.post(alerts)
    }

    companion object {
        fun sessionKey(label: String, file: UsageFile) = "$label:${file.session?.resetsAt.orEmpty()}"
    }
}
