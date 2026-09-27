package dev.imkdw.claudewatch.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import dev.imkdw.claudewatch.Graph
import dev.imkdw.claudewatch.data.snapshot
import dev.imkdw.claudewatch.domain.DisplayCalculator
import dev.imkdw.claudewatch.domain.DisplayState
import dev.imkdw.claudewatch.domain.levelOf
import dev.imkdw.claudewatch.tile.TileColors
import dev.imkdw.claudewatch.ui.AccountPickerActivity

object ComplicationBuilder {
    private fun plain(text: String) = PlainComplicationText.Builder(text).build()

    /** F13: 5% 단위 20칸. 0~59 기본, 60~84 주황, 85~100 빨강 */
    private val levelRamp = ColorRamp(IntArray(20) { i -> TileColors.of(levelOf(i * 5)) }, false)

    /** F1: 선택 계정의 세션 사용률 링 + 숫자 + 계정 이름 */
    fun buildRanged(state: DisplayState, tapAction: PendingIntent?): RangedValueComplicationData {
        val pct = state.sessionPct
        val description = if (state.account == null || pct == null) "데이터 없음" else "${state.account} 세션 $pct%"
        return RangedValueComplicationData.Builder(
            value = (pct ?: 0).toFloat(),
            min = 0f,
            max = 100f,
            contentDescription = plain(description),
        )
            .setText(plain(pct?.let { "$it%" } ?: "--"))
            .apply { state.account?.let { setTitle(plain(it)) } }
            .setColorRamp(levelRamp)
            .setTapAction(tapAction)
            .build()
    }

    fun build(type: ComplicationType, state: DisplayState, tapAction: PendingIntent?): ComplicationData? =
        if (type == ComplicationType.RANGED_VALUE) buildRanged(state, tapAction) else null

    fun tapIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, AccountPickerActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    suspend fun forSelected(context: Context, type: ComplicationType): ComplicationData? {
        val clock = Graph.clock
        val file = Graph.source(context).snapshot().selectedFile
        return build(type, DisplayCalculator.toDisplay(file, clock.instant(), clock.zone), tapIntent(context))
    }
}
