package dev.imkdw.claudewatch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import dev.imkdw.claudewatch.data.Snapshot
import dev.imkdw.claudewatch.domain.AccountResolver
import dev.imkdw.claudewatch.domain.DisplayCalculator
import dev.imkdw.claudewatch.domain.DisplayState
import java.time.Instant
import java.time.ZoneId

data class AccountItem(val label: String, val subtitle: String, val selected: Boolean)

/** PRD 9.3: 라벨 가나다순, 부제는 리셋 계산이 반영된 값 */
fun accountItems(snapshot: Snapshot, now: Instant, zone: ZoneId): List<AccountItem> =
    AccountResolver.sortedLabels(snapshot.accounts.keys).map { label ->
        val state = DisplayCalculator.toDisplay(snapshot.accounts.getValue(label), now, zone)
        val pct = { v: Int? -> v?.let { "$it%" } ?: "--" }
        AccountItem(label, "세션 ${pct(state.sessionPct)} / 주간 ${pct(state.weeklyPct)}", label == snapshot.selected)
    }

@Composable
fun AccountPickerScreen(items: List<AccountItem>, onSelect: (String) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(DisplayState.NO_DATA, textAlign = TextAlign.Center)
                }
                return@ScreenScaffold
            }
            TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                item {
                    ListHeader(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) { Text("계정 선택") }
                }
                items(items, key = { it.label }) { item ->
                    RadioButton(
                        selected = item.selected,
                        onSelect = { onSelect(item.label) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        secondaryLabel = { Text(item.subtitle) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
    }
}
