package dev.imkdw.claudewatch.testing

import dev.imkdw.claudewatch.data.ModelPct
import dev.imkdw.claudewatch.data.UsageFile
import dev.imkdw.claudewatch.data.Window

object Fixtures {
    fun text(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "픽스처 없음: $name" }.readText()
}

fun usageFile(
    account: String = "personal",
    session: Window? = Window(42, "2026-09-27T15:00:00Z"),
    weekly: Window? = Window(18, "2026-10-01T03:00:00Z"),
    weeklyByModel: List<ModelPct> = emptyList(),
    changedAt: String = "2026-09-27T12:05:00Z",
    status: String = "ok",
) = UsageFile(account, session, weekly, weeklyByModel, changedAt, status, "oauth-usage-v1")
