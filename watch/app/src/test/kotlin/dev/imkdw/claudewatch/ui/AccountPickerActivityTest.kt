package dev.imkdw.claudewatch.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.Graph
import dev.imkdw.claudewatch.testing.FakeUiUpdater
import dev.imkdw.claudewatch.testing.FakeUsageSource
import dev.imkdw.claudewatch.testing.usageFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AccountPickerActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val ui = FakeUiUpdater()
    private val source = FakeUsageSource(
        mapOf("personal" to usageFile(account = "personal"), "work" to usageFile(account = "work")),
        selected = "personal",
    )

    @Before
    fun setUp() = Graph.override(source = source, uiUpdater = ui)

    @After
    fun tearDown() = Graph.reset()

    @Test
    fun `UI4 work를 탭하면 저장, 화면 갱신 1회, 화면 닫힘`() {
        val scenario = ActivityScenario.launch(AccountPickerActivity::class.java)
        compose.onNodeWithText("work").performClick()
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(source.selections).containsExactly("work")
        assertThat(runBlocking { source.observe().first().selected }).isEqualTo("work")
        assertThat(ui.count).isEqualTo(1)
        var finishing = false
        scenario.onActivity { finishing = it.isFinishing }
        assertThat(finishing).isTrue()
    }
}
