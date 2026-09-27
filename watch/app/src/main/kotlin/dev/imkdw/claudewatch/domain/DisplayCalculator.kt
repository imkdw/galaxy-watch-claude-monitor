package dev.imkdw.claudewatch.domain

import dev.imkdw.claudewatch.data.UsageFile
import dev.imkdw.claudewatch.data.Window
import dev.imkdw.claudewatch.data.parseInstant
import java.time.Instant
import java.time.ZoneId

object DisplayCalculator {
    const val RESET_DONE = "리셋됨"

    private data class Shown(val pct: Int?, val reset: String?, val passed: Boolean)

    private fun show(window: Window?, now: Instant, zone: ZoneId): Shown {
        if (window == null) return Shown(null, null, false)
        val resetAt = parseInstant(window.resetsAt) ?: return Shown(window.pct, null, false)
        return if (!now.isBefore(resetAt)) {
            Shown(0, RESET_DONE, true)
        } else {
            Shown(window.pct, TimeText.resetText(now, resetAt, zone), false)
        }
    }

    fun toDisplay(file: UsageFile?, now: Instant, zone: ZoneId): DisplayState {
        if (file == null) return DisplayState.Empty
        val session = show(file.session, now, zone)
        val weekly = show(file.weekly, now, zone)
        val models = if (weekly.passed) file.weeklyByModel.map { it.copy(pct = 0) } else file.weeklyByModel
        val footer = when (file.status) {
            "ok" -> parseInstant(file.changedAt)?.let { TimeText.changedText(it, now) } ?: ""
            "auth_error" -> "수집 오류 (Claude Code 실행 필요)"
            else -> "수집 오류"
        }
        return DisplayState(
            account = file.account,
            sessionPct = session.pct,
            sessionReset = session.reset,
            weeklyPct = weekly.pct,
            weeklyReset = weekly.reset,
            models = models,
            footer = footer,
            level = levelOf(session.pct),
            sessionResetPassed = session.passed,
        )
    }
}
