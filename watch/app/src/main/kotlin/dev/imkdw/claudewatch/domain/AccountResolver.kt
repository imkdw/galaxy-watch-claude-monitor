package dev.imkdw.claudewatch.domain

import dev.imkdw.claudewatch.data.UsageFile
import dev.imkdw.claudewatch.data.parseInstant
import java.text.Collator
import java.time.Instant
import java.util.Locale

object AccountResolver {
    /** 선택 계정이 없거나 Gist에서 사라졌으면 changedAt이 가장 최근인 계정 (PRD 6 여러 계정) */
    fun resolve(selected: String?, accounts: Map<String, UsageFile>): String? {
        if (selected != null && selected in accounts) return selected
        return accounts.maxByOrNull { (_, file) -> parseInstant(file.changedAt) ?: Instant.EPOCH }?.key
    }

    /** 라벨 가나다순 (PRD 9.3) */
    fun sortedLabels(labels: Collection<String>): List<String> {
        val collator = Collator.getInstance(Locale.KOREAN)
        return labels.sortedWith(collator)
    }
}
