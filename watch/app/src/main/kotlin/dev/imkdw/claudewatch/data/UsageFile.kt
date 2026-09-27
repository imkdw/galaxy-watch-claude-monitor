package dev.imkdw.claudewatch.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

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

val UsageJson = Json { ignoreUnknownKeys = true }

fun parseInstant(value: String): Instant? = try {
    OffsetDateTime.parse(value).toInstant()
} catch (_: DateTimeParseException) {
    null
}
