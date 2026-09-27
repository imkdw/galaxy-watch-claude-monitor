package dev.imkdw.claudewatch.domain

import dev.imkdw.claudewatch.data.ModelPct

enum class Level { NORMAL, WARN, DANGER }

fun levelOf(pct: Int?): Level = when {
    pct == null -> Level.NORMAL
    pct >= 85 -> Level.DANGER
    pct >= 60 -> Level.WARN
    else -> Level.NORMAL
}

data class DisplayState(
    val account: String?,
    val sessionPct: Int?,
    val sessionReset: String?,
    val weeklyPct: Int?,
    val weeklyReset: String?,
    val models: List<ModelPct>,
    val footer: String,
    val level: Level,
    val sessionResetPassed: Boolean = false,
) {
    companion object {
        const val NO_DATA = "데이터 없음. 수집기 확인"
        val Empty = DisplayState(null, null, null, null, null, emptyList(), NO_DATA, Level.NORMAL)
    }
}
