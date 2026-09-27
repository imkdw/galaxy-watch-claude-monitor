package dev.imkdw.claudewatch.domain

import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.testing.usageFile
import org.junit.Test

class AccountResolverTest {
    private val accounts = mapOf(
        "work" to usageFile(account = "work", changedAt = "2026-09-27T12:00:00Z"),
        "personal" to usageFile(account = "personal", changedAt = "2026-09-27T12:30:00Z"),
        "side" to usageFile(account = "side", changedAt = "2026-09-27T09:00:00Z"),
    )

    @Test
    fun `AR1 선택 계정이 있으면 그대로`() {
        assertThat(AccountResolver.resolve("work", accounts)).isEqualTo("work")
    }

    @Test
    fun `AR2 선택이 없으면 changedAt이 가장 최근인 계정`() {
        assertThat(AccountResolver.resolve(null, accounts)).isEqualTo("personal")
    }

    @Test
    fun `AR3 선택 계정이 사라졌으면 가장 최근 계정`() {
        assertThat(AccountResolver.resolve("gone", accounts)).isEqualTo("personal")
    }

    @Test
    fun `AR4 계정이 없으면 null`() {
        assertThat(AccountResolver.resolve("work", emptyMap())).isNull()
    }

    @Test
    fun `AR5 라벨은 가나다순`() {
        assertThat(AccountResolver.sortedLabels(setOf("work", "개인", "personal", "Side", "가족")))
            .containsExactly("personal", "Side", "work", "가족", "개인").inOrder()
    }

    @Test
    fun `changedAt이 깨진 계정은 가장 오래된 것으로 본다`() {
        val m = mapOf("a" to usageFile(account = "a", changedAt = "bad"), "b" to usageFile(account = "b", changedAt = "2020-01-01T00:00:00Z"))
        assertThat(AccountResolver.resolve(null, m)).isEqualTo("b")
    }
}
