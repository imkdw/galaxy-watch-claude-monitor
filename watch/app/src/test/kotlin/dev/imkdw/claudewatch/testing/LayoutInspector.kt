package dev.imkdw.claudewatch.testing

import androidx.wear.protolayout.ActionBuilders.Action
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcAdapter
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Modifiers

private fun LayoutElement.children(): List<LayoutElement> = when (this) {
    is Box -> contents
    is Column -> contents
    is Row -> contents
    is Arc -> contents.mapNotNull { (it as? ArcAdapter)?.content }
    else -> emptyList()
}

private fun LayoutElement.modifiers(): Modifiers? = when (this) {
    is Box -> modifiers
    is Column -> modifiers
    is Row -> modifiers
    is Text -> modifiers
    is Spacer -> modifiers
    is Image -> modifiers
    else -> null
}

private fun LayoutElement.walk(visit: (LayoutElement) -> Unit) {
    visit(this)
    children().forEach { it.walk(visit) }
}

/** 레이아웃 트리의 모든 Text 내용 (계획 S6-1) */
fun collectTexts(layout: LayoutElement): List<String> = buildList {
    layout.walk { e -> (e as? Text)?.text?.value?.let(::add) }
}

/** 클릭 가능한 요소의 id → 동작 */
fun collectClickables(layout: LayoutElement): Map<String, Action?> = buildMap {
    layout.walk { e -> e.modifiers()?.clickable?.let { put(it.id, it.onClick) } }
}
