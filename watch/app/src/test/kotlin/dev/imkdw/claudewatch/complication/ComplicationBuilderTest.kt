package dev.imkdw.claudewatch.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.Graph
import dev.imkdw.claudewatch.data.Window
import dev.imkdw.claudewatch.domain.DisplayCalculator
import dev.imkdw.claudewatch.domain.DisplayState
import dev.imkdw.claudewatch.domain.Level
import dev.imkdw.claudewatch.testing.FakeUsageSource
import dev.imkdw.claudewatch.testing.usageFile
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ComplicationBuilderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tap = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
    private val state = DisplayState("personal", 42, "1시간 12분 후 리셋", 18, "수 12:00 리셋", emptyList(), "3분 전 변경", Level.NORMAL)

    private fun ComplicationText?.str() = this?.getTextAt(context.resources, Instant.EPOCH)?.toString()

    @After
    fun tearDown() = Graph.reset()

    @Test
    fun `CP1 42퍼센트 링`() {
        val data = ComplicationBuilder.buildRanged(state, tap)
        assertThat(data.type).isEqualTo(ComplicationType.RANGED_VALUE)
        assertThat(data.value).isEqualTo(42f)
        assertThat(data.min).isEqualTo(0f)
        assertThat(data.max).isEqualTo(100f)
        assertThat(data.text.str()).isEqualTo("42%")
        assertThat(data.title.str()).isEqualTo("personal")
        assertThat(data.tapAction).isEqualTo(tap)
        assertThat(data.contentDescription.str()).isEqualTo("personal 세션 42%")
    }

    @Test
    fun `CP2 리셋이 지나면 0`() {
        val file = usageFile(session = Window(70, "2026-09-27T11:00:00Z"))
        val passed = DisplayCalculator.toDisplay(file, Instant.parse("2026-09-27T11:00:00Z"), ZoneId.of("Asia/Seoul"))
        assertThat(ComplicationBuilder.buildRanged(passed, tap).value).isEqualTo(0f)
    }

    @Test
    fun `CP3 데이터 없음`() {
        val data = ComplicationBuilder.buildRanged(DisplayState.Empty, null)
        assertThat(data.value).isEqualTo(0f)
        assertThat(data.text.str()).isEqualTo("--")
        assertThat(data.contentDescription.str()).isEqualTo("데이터 없음")
    }

    @Test
    fun `CP4 RANGED_VALUE가 아닌 요청은 null`() {
        assertThat(ComplicationBuilder.build(ComplicationType.SHORT_TEXT, state, tap)).isNull()
        assertThat(ComplicationBuilder.build(ComplicationType.RANGED_VALUE, state, tap)).isInstanceOf(RangedValueComplicationData::class.java)
    }

    @Test
    fun `CP5 미리보기 데이터`() {
        val preview = UsageComplicationService().getPreviewData(ComplicationType.RANGED_VALUE) as RangedValueComplicationData
        assertThat(preview.value).isEqualTo(42f)
        assertThat(UsageComplicationService().getPreviewData(ComplicationType.SHORT_TEXT)).isNull()
    }

    @Test
    fun `색상 단계를 링 색 구간으로 넘긴다`() {
        val ramp = ComplicationBuilder.buildRanged(state, tap).colorRamp!!
        assertThat(ramp.colors.size).isEqualTo(20)
        assertThat(ramp.interpolated).isFalse()
    }

    @Test
    fun `서비스는 선택 계정의 리셋 계산 값을 쓴다`() = runTest {
        Graph.override(
            source = FakeUsageSource(
                mapOf(
                    "personal" to usageFile(account = "personal", session = Window(42, "2026-09-27T15:00:00Z")),
                    "work" to usageFile(account = "work", session = Window(70, "2026-09-27T11:00:00Z")),
                ),
                selected = "work",
            ),
            clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneId.of("Asia/Seoul")),
        )
        val data = ComplicationBuilder.forSelected(context, ComplicationType.RANGED_VALUE) as RangedValueComplicationData
        assertThat(data.title.str()).isEqualTo("work")
        assertThat(data.value).isEqualTo(0f)
    }
}
