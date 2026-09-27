package dev.imkdw.claudewatch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dev.imkdw.claudewatch.Graph
import kotlinx.coroutines.launch

/** F8: 타일 계정 칩이나 컴플리케이션을 탭하면 열린다. 고르면 저장하고 닫힌다 */
class AccountPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = Graph.source(this)
        setContent {
            val snapshot by source.observe().collectAsState(initial = null)
            ClaudeWatchTheme {
                snapshot?.let { snap ->
                    AccountPickerScreen(accountItems(snap, Graph.clock.instant(), Graph.zone), ::select)
                }
            }
        }
    }

    private fun select(label: String) {
        lifecycleScope.launch {
            Graph.source(this@AccountPickerActivity).select(label)
            Graph.uiUpdater(this@AccountPickerActivity).requestUpdate()
            finish()
        }
    }
}
