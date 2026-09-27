package dev.imkdw.claudewatch.domain

import dev.imkdw.claudewatch.data.UsageFile
import dev.imkdw.claudewatch.data.parseInstant
import java.text.Collator
import java.time.Instant
import java.util.Locale

object AccountResolver {
    fun resolve(selected: String?, accounts: Map<String, UsageFile>): String? {
        if (selected != null && selected in accounts) return selected
        return accounts.maxByOrNull { (_, file) -> parseInstant(file.changedAt) ?: Instant.EPOCH }?.key
    }

    fun sortedLabels(labels: Collection<String>): List<String> {
        val collator = Collator.getInstance(Locale.KOREAN)
        return labels.sortedWith(collator)
    }
}
