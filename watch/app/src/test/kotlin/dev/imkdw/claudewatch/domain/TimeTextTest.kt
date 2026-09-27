package dev.imkdw.claudewatch.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class TimeTextTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    // 2026-09-27 (일) 21:00 KST
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    private fun resetIn(d: Duration, zone: ZoneId = seoul) = TimeText.resetText(now, now.plus(d), zone)
    private fun changedAgo(d: Duration) = TimeText.changedText(now.minus(d), now)

    @Test
    fun `X1 72분 남음`() {
        assertThat(resetIn(Duration.ofMinutes(72))).isEqualTo("1시간 12분 후 리셋")
    }

    @Test
    fun `X2 45분, 60분, 30초 남음 (분은 올림)`() {
        assertThat(resetIn(Duration.ofMinutes(45))).isEqualTo("45분 후 리셋")
        assertThat(resetIn(Duration.ofMinutes(60))).isEqualTo("1시간 후 리셋")
        assertThat(resetIn(Duration.ofSeconds(30))).isEqualTo("1분 후 리셋")
        assertThat(resetIn(Duration.ofMinutes(44).plusSeconds(1))).isEqualTo("45분 후 리셋")
    }

    @Test
    fun `X3 24시간 이상이면 요일과 시각`() {
        // 2026-09-30 (수) 12:00 KST
        assertThat(TimeText.resetText(now, Instant.parse("2026-09-30T03:00:00Z"), seoul)).isEqualTo("수 12:00 리셋")
        assertThat(resetIn(Duration.ofHours(24))).isEqualTo("월 21:00 리셋")
    }

    @Test
    fun `X4 23시간 59분은 상대 시간`() {
        assertThat(resetIn(Duration.ofHours(23).plusMinutes(59))).isEqualTo("23시간 59분 후 리셋")
    }

    @Test
    fun `X5 변경 시각 문구`() {
        assertThat(changedAgo(Duration.ofSeconds(30))).isEqualTo("방금 변경")
        assertThat(changedAgo(Duration.ofMinutes(3))).isEqualTo("3분 전 변경")
        assertThat(changedAgo(Duration.ofHours(3).plusMinutes(5))).isEqualTo("3시간 전 변경")
        assertThat(changedAgo(Duration.ofDays(2))).isEqualTo("2일 전 변경")
        assertThat(changedAgo(Duration.ofMinutes(59).plusSeconds(59))).isEqualTo("59분 전 변경")
        assertThat(changedAgo(Duration.ofHours(23).plusMinutes(59))).isEqualTo("23시간 전 변경")
    }

    @Test
    fun `X6 changedAt이 미래면 방금 변경`() {
        assertThat(TimeText.changedText(now.plusSeconds(300), now)).isEqualTo("방금 변경")
    }

    @Test
    fun `X7 zone을 바꾸면 요일과 시각이 그 기준`() {
        val reset = Instant.parse("2026-09-30T03:00:00Z")
        assertThat(TimeText.resetText(now, reset, ZoneOffset.UTC)).isEqualTo("수 03:00 리셋")
        assertThat(TimeText.resetText(now, Instant.parse("2026-09-29T20:00:00Z"), seoul)).isEqualTo("수 05:00 리셋")
        assertThat(TimeText.resetText(now, Instant.parse("2026-09-29T20:00:00Z"), ZoneOffset.UTC)).isEqualTo("화 20:00 리셋")
    }
}
