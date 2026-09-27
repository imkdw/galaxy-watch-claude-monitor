package dev.imkdw.claudewatch.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object TimeText {
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    /** 24시간 미만은 "1시간 12분 후 리셋" (분 올림), 이상은 "수 12:00 리셋" (계획 P4) */
    fun resetText(now: Instant, resetAt: Instant, zone: ZoneId): String {
        val left = Duration.between(now, resetAt)
        if (left >= Duration.ofHours(24)) {
            val local = resetAt.atZone(zone)
            val day = local.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
            return "$day ${hhmm.format(local)} 리셋"
        }
        val minutes = (left.seconds + 59) / 60
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "${m}분 후 리셋"
            m == 0L -> "${h}시간 후 리셋"
            else -> "${h}시간 ${m}분 후 리셋"
        }
    }

    /** "방금 변경" / "3분 전 변경" / "3시간 전 변경" / "2일 전 변경". 미래(시계 오차)는 방금 */
    fun changedText(changedAt: Instant, now: Instant): String {
        val ago = Duration.between(changedAt, now)
        return when {
            ago < Duration.ofMinutes(1) -> "방금 변경"
            ago < Duration.ofHours(1) -> "${ago.toMinutes()}분 전 변경"
            ago < Duration.ofDays(1) -> "${ago.toHours()}시간 전 변경"
            else -> "${ago.toDays()}일 전 변경"
        }
    }
}
