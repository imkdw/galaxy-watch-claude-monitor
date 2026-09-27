package dev.imkdw.claudewatch.domain

import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.data.ModelPct
import dev.imkdw.claudewatch.data.Window
import dev.imkdw.claudewatch.testing.usageFile
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DisplayCalculatorTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-09-27T13:48:00Z")
    private val file = usageFile(
        session = Window(42, "2026-09-27T15:00:00Z"),
        weekly = Window(18, "2026-09-30T03:00:00Z"),
        weeklyByModel = listOf(ModelPct("Fable", 7)),
        changedAt = "2026-09-27T13:45:00Z",
    )

    @Test
    fun `C1 리셋 전이면 저장값 그대로`() {
        val s = DisplayCalculator.toDisplay(file, now, zone)
        assertThat(s.account).isEqualTo("personal")
        assertThat(s.sessionPct).isEqualTo(42)
        assertThat(s.sessionReset).isEqualTo("1시간 12분 후 리셋")
        assertThat(s.weeklyPct).isEqualTo(18)
        assertThat(s.weeklyReset).isEqualTo("수 12:00 리셋")
        assertThat(s.models).containsExactly(ModelPct("Fable", 7))
        assertThat(s.footer).isEqualTo("3분 전 변경")
        assertThat(s.sessionResetPassed).isFalse()
    }

    @Test
    fun `C2 리셋 시각과 정확히 같으면 지난 것으로 본다`() {
        val s = DisplayCalculator.toDisplay(file, Instant.parse("2026-09-27T15:00:00Z"), zone)
        assertThat(s.sessionPct).isEqualTo(0)
        assertThat(s.sessionReset).isEqualTo("리셋됨")
        assertThat(s.sessionResetPassed).isTrue()
    }

    @Test
    fun `C3 세션만 리셋 지남`() {
        val s = DisplayCalculator.toDisplay(file, Instant.parse("2026-09-27T16:00:00Z"), zone)
        assertThat(s.sessionPct).isEqualTo(0)
        assertThat(s.weeklyPct).isEqualTo(18)
        assertThat(s.models).containsExactly(ModelPct("Fable", 7))
    }

    @Test
    fun `C4 Mac을 껐을 때 표 - 19시59분 70, 20시 0, 다음날 9시 0`() {
        val f = usageFile(session = Window(70, "2026-09-27T11:00:00Z"), changedAt = "2026-09-27T09:00:00Z")
        fun at(iso: String) = DisplayCalculator.toDisplay(f, Instant.parse(iso), zone).sessionPct
        assertThat(at("2026-09-27T10:59:00Z")).isEqualTo(70)
        assertThat(at("2026-09-27T11:00:00Z")).isEqualTo(0)
        assertThat(at("2026-09-28T00:00:00Z")).isEqualTo(0)
    }

    @Test
    fun `C5 주간 리셋이 지나면 모델별 값도 0`() {
        val s = DisplayCalculator.toDisplay(file, Instant.parse("2026-09-30T03:00:00Z"), zone)
        assertThat(s.weeklyPct).isEqualTo(0)
        assertThat(s.weeklyReset).isEqualTo("리셋됨")
        assertThat(s.models).containsExactly(ModelPct("Fable", 0))
    }

    @Test
    fun `C6 파일이 없으면 데이터 없음`() {
        val s = DisplayCalculator.toDisplay(null, now, zone)
        assertThat(s.account).isNull()
        assertThat(s.sessionPct).isNull()
        assertThat(s.weeklyPct).isNull()
        assertThat(s.sessionReset).isNull()
        assertThat(s.footer).isEqualTo("데이터 없음. 수집기 확인")
        assertThat(s.level).isEqualTo(Level.NORMAL)
    }

    @Test
    fun `C7 auth_error는 값 유지 + Claude Code 실행 안내`() {
        val s = DisplayCalculator.toDisplay(file.copy(status = "auth_error"), now, zone)
        assertThat(s.sessionPct).isEqualTo(42)
        assertThat(s.footer).isEqualTo("수집 오류 (Claude Code 실행 필요)")
    }

    @Test
    fun `C8 api_error는 값 유지 + 수집 오류`() {
        val s = DisplayCalculator.toDisplay(file.copy(status = "api_error"), now, zone)
        assertThat(s.sessionPct).isEqualTo(42)
        assertThat(s.footer).isEqualTo("수집 오류")
    }

    @Test
    fun `C9 알 수 없는 status는 수집 오류`() {
        assertThat(DisplayCalculator.toDisplay(file.copy(status = "weird"), now, zone).footer).isEqualTo("수집 오류")
    }

    @Test
    fun `C10 색상 단계`() {
        fun level(pct: Int) = DisplayCalculator.toDisplay(file.copy(session = Window(pct, "2026-09-27T15:00:00Z")), now, zone).level
        assertThat(listOf(59, 60, 84, 85, 100).map(::level))
            .containsExactly(Level.NORMAL, Level.WARN, Level.WARN, Level.DANGER, Level.DANGER).inOrder()
        assertThat(levelOf(null)).isEqualTo(Level.NORMAL)
    }

    @Test
    fun `C11 리셋으로 0이 되면 NORMAL`() {
        val f = file.copy(session = Window(95, "2026-09-27T15:00:00Z"))
        assertThat(DisplayCalculator.toDisplay(f, now, zone).level).isEqualTo(Level.DANGER)
        assertThat(DisplayCalculator.toDisplay(f, Instant.parse("2026-09-27T15:00:01Z"), zone).level).isEqualTo(Level.NORMAL)
    }

    @Test
    fun `session이 null이면 그 항목만 비운다`() {
        val s = DisplayCalculator.toDisplay(file.copy(session = null), now, zone)
        assertThat(s.sessionPct).isNull()
        assertThat(s.sessionReset).isNull()
        assertThat(s.weeklyPct).isEqualTo(18)
    }

    @Test
    fun `resetsAt이 깨졌으면 값은 보이고 리셋 문구만 없다`() {
        val s = DisplayCalculator.toDisplay(file.copy(session = Window(42, "garbage")), now, zone)
        assertThat(s.sessionPct).isEqualTo(42)
        assertThat(s.sessionReset).isNull()
    }
}
