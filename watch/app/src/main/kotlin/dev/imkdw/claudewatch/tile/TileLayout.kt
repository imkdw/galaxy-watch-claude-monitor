package dev.imkdw.claudewatch.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.weight
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_END
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_START
import androidx.wear.protolayout.layout.box
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.row
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.background
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.clip
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import dev.imkdw.claudewatch.domain.DisplayState
import dev.imkdw.claudewatch.domain.Level
import dev.imkdw.claudewatch.domain.levelOf
import dev.imkdw.claudewatch.ui.AccountPickerActivity

object TileIds {
    const val REFRESH = "refresh"
    const val ACCOUNT = "account"
}

object TileColors {
    const val CLAUDE = 0xFFD97757.toInt()
    const val WARN = 0xFFF4A340.toInt()
    const val DANGER = 0xFFE5534B.toInt()
    const val TRACK = 0xFF333333.toInt()

    fun of(level: Level): Int = when (level) {
        Level.NORMAL -> CLAUDE
        Level.WARN -> WARN
        Level.DANGER -> DANGER
    }
}

private val scheme = ColorScheme(primary = TileColors.CLAUDE.argb, onPrimary = 0xFF000000.toInt().argb)

fun refreshClickable() = clickable(action = loadAction(), id = TileIds.REFRESH)

fun accountClickable(context: Context) = clickable(
    action = ActionBuilders.LaunchAction.Builder()
        .setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(context.packageName)
                .setClassName(AccountPickerActivity::class.java.name)
                .build(),
        )
        .build(),
    id = TileIds.ACCOUNT,
)

fun tileLayout(context: Context, state: DisplayState, deviceParams: DeviceParameters): LayoutElement =
    materialScope(context, deviceParams, allowDynamicTheme = false, defaultColorScheme = scheme) {
        primaryLayout(
            titleSlot = { title(context, state.account) },
            mainSlot = { mainContent(state) },
            bottomSlot = {
                textEdgeButton(onClick = refreshClickable()) { text("새로고침".layoutString) }
            },
        )
    }

private fun MaterialScope.title(context: Context, account: String?): LayoutElement =
    if (account == null) {
        text("Claude 사용량".layoutString)
    } else {
        text(
            "$account ▾".layoutString,
            color = TileColors.CLAUDE.argb,
            modifier = LayoutModifier.clickable(accountClickable(context)),
        )
    }

private fun MaterialScope.mainContent(state: DisplayState): LayoutElement {
    val items = mutableListOf<LayoutElement>()
    if (state.account != null) {
        items += usageBlock("세션", state.sessionPct, state.sessionReset)
        items += usageBlock("주간", state.weeklyPct, state.weeklyReset)
    }
    items += text(
        state.footer.layoutString,
        typography = Typography.LABEL_SMALL,
        color = colorScheme.onSurfaceVariant,
        maxLines = 2,
    )
    return column(*items.toTypedArray(), width = expand(), horizontalAlignment = HORIZONTAL_ALIGN_CENTER)
}

private fun MaterialScope.usageBlock(title: String, pct: Int?, reset: String?): LayoutElement {
    val color = TileColors.of(levelOf(pct)).argb
    val header = row(
        text(title.layoutString, typography = Typography.LABEL_SMALL, alignment = TEXT_ALIGN_START),
        spacer(width = weight(1f)),
        text((pct?.let { "$it%" } ?: "--").layoutString, typography = Typography.LABEL_MEDIUM, alignment = TEXT_ALIGN_END),
        width = expand(),
    )
    val parts = mutableListOf(header, bar(pct, color))
    if (reset != null) {
        parts += text(reset.layoutString, typography = Typography.LABEL_SMALL, color = colorScheme.onSurfaceVariant)
    }
    return column(*parts.toTypedArray(), width = expand(), modifier = LayoutModifier)
}

private fun bar(pct: Int?, color: LayoutColor): LayoutElement {
    val filled = (pct ?: 0).coerceIn(0, 100).toFloat()
    val track = LayoutModifier.background(TileColors.TRACK.argb).clip(2f)
    return box(
        row(
            box(width = weight(filled), height = dp(4f), modifier = LayoutModifier.background(color).clip(2f)),
            box(width = weight(100f - filled), height = dp(4f)),
            width = expand(),
        ),
        width = expand(),
        height = dp(4f),
        modifier = track,
    )
}
