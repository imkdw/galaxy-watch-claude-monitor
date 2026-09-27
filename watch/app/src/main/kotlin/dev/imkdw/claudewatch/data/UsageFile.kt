package dev.imkdw.claudewatch.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Gist의 usage-<label>.json (PRD 8.3). 수집기 collector/src/types.ts와 손으로 맞춘다 */
@Serializable
data class Window(val pct: Int, val resetsAt: String)

@Serializable
data class ModelPct(val model: String, val pct: Int)

@Serializable
data class UsageFile(
    val account: String,
    val session: Window? = null,
    val weekly: Window? = null,
    val weeklyByModel: List<ModelPct> = emptyList(),
    val changedAt: String,
    val status: String = "ok",
    val source: String? = null,
)

/** 수집기가 필드를 추가해도 워치가 안 깨지게 */
val UsageJson = Json { ignoreUnknownKeys = true }

/** `Z`와 `+00:00`, 마이크로초가 붙은 원본 형식(PRD 8.2) 모두 받는다 */
fun parseInstant(value: String): Instant? = try {
    OffsetDateTime.parse(value).toInstant()
} catch (_: DateTimeParseException) {
    null
}
