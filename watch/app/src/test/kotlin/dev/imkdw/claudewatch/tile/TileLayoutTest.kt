package dev.imkdw.claudewatch.tile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.ActionBuilders.LaunchAction
import androidx.wear.protolayout.ActionBuilders.LoadAction
import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.data.ModelPct
import dev.imkdw.claudewatch.domain.DisplayState
import dev.imkdw.claudewatch.domain.Level
import dev.imkdw.claudewatch.testing.collectClickables
import dev.imkdw.claudewatch.testing.collectTexts
import dev.imkdw.claudewatch.testing.watchDevice
import dev.imkdw.claudewatch.ui.AccountPickerActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TileLayoutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val normal = DisplayState(
        account = "personal",
        sessionPct = 42,
        sessionReset = "1시간 12분 후 리셋",
        weeklyPct = 18,
        weeklyReset = "수 12:00 리셋",
        models = emptyList(),
        footer = "3분 전 변경",
        level = Level.NORMAL,
    )

    @Test
    fun `TL1 정상 상태 텍스트`() {
        val texts = collectTexts(tileLayout(context, normal, watchDevice))
        assertThat(texts).containsAtLeast("personal ▾", "세션", "42%", "1시간 12분 후 리셋", "주간", "18%", "수 12:00 리셋", "3분 전 변경", "새로고침")
    }

    @Test
    fun `TL2 데이터 없음 상태`() {
        val layout = tileLayout(context, DisplayState.Empty, watchDevice)
        assertThat(collectTexts(layout)).containsAtLeast("Claude 사용량", "데이터 없음. 수집기 확인")
        assertThat(collectTexts(layout)).doesNotContain("세션")
        assertThat(collectClickables(layout).keys).containsExactly(TileIds.REFRESH)
    }

    @Test
    fun `TL3 새로고침은 LoadAction, 계정 칩은 계정 선택 화면 LaunchAction`() {
        val clickables = collectClickables(tileLayout(context, normal, watchDevice))
        assertThat(clickables[TileIds.REFRESH]).isInstanceOf(LoadAction::class.java)
        val launch = clickables[TileIds.ACCOUNT] as LaunchAction
        assertThat(launch.androidActivity?.className).isEqualTo(AccountPickerActivity::class.java.name)
        assertThat(launch.androidActivity?.packageName).isEqualTo(context.packageName)
    }

    @Test
    fun `TL7 모델별 주간 한도는 타일에 보이지 않는다`() {
        val texts = collectTexts(tileLayout(context, normal.copy(models = listOf(ModelPct("Fable", 7))), watchDevice))
        assertThat(texts.none { it.contains("Fable") }).isTrue()
    }

    @Test
    fun `값이 없는 항목은 -- 로 보인다`() {
        val texts = collectTexts(tileLayout(context, normal.copy(sessionPct = null, sessionReset = null), watchDevice))
        assertThat(texts).contains("--")
    }
}
