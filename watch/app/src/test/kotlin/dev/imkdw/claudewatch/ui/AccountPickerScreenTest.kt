package dev.imkdw.claudewatch.ui

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.data.Snapshot
import dev.imkdw.claudewatch.data.Window
import dev.imkdw.claudewatch.testing.usageFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class AccountPickerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val items = listOf(
        AccountItem("personal", "세션 42% / 주간 18%", selected = true),
        AccountItem("work", "세션 10% / 주간 5%", selected = false),
    )

    @Test
    fun `UI1 가나다순, 선택 계정에 체크`() {
        val snapshot = Snapshot(
            mapOf("work" to usageFile(account = "work"), "personal" to usageFile(account = "personal")),
            selected = "personal",
        )
        val built = accountItems(snapshot, Instant.parse("2026-09-27T12:00:00Z"), ZoneId.of("Asia/Seoul"))
        assertThat(built.map { it.label }).containsExactly("personal", "work").inOrder()
        assertThat(built.map { it.selected }).containsExactly(true, false).inOrder()

        compose.setContent { AccountPickerScreen(built, onSelect = {}) }
        compose.onNodeWithText("personal").assertIsSelected()
        compose.onNodeWithText("work").assertIsNotSelected()
        val personalTop = compose.onNodeWithText("personal").getBoundsInRoot().top
        val workTop = compose.onNodeWithText("work").getBoundsInRoot().top
        assertThat(personalTop < workTop).isTrue()
    }

    @Test
    fun `UI2 부제는 리셋 계산이 반영된 값`() {
        val snapshot = Snapshot(
            mapOf(
                "personal" to usageFile(session = Window(42, "2026-09-27T11:00:00Z"), weekly = Window(18, "2026-10-01T03:00:00Z")),
                "work" to usageFile(account = "work", session = null, weekly = null),
            ),
            selected = "personal",
        )
        val built = accountItems(snapshot, Instant.parse("2026-09-27T12:00:00Z"), ZoneId.of("Asia/Seoul"))
        assertThat(built.map { it.subtitle }).containsExactly("세션 0% / 주간 18%", "세션 -- / 주간 --").inOrder()

        compose.setContent { AccountPickerScreen(built, onSelect = {}) }
        compose.onNodeWithText("세션 0% / 주간 18%", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `UI3 work를 탭하면 onSelect(work) 1회`() {
        val selected = mutableListOf<String>()
        compose.setContent { AccountPickerScreen(items, onSelect = { selected += it }) }
        compose.onNodeWithText("work").performClick()
        assertThat(selected).containsExactly("work")
    }

    @Test
    fun `UI5 계정이 없으면 데이터 없음`() {
        compose.setContent { AccountPickerScreen(emptyList(), onSelect = {}) }
        compose.onNodeWithText("데이터 없음. 수집기 확인").assertExists()
    }
}
