package dev.imkdw.claudewatch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dev.imkdw.claudewatch.Graph
import kotlinx.coroutines.launch

class AccountPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = Graph.source(this)
        requestNotificationPermission()
        setContent {
            val snapshot by source.observe().collectAsState(initial = null)
            ClaudeWatchTheme {
                snapshot?.let { snap ->
                    AccountPickerScreen(accountItems(snap, Graph.clock.instant(), Graph.zone), ::select)
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun select(label: String) {
        lifecycleScope.launch {
            Graph.source(this@AccountPickerActivity).select(label)
            Graph.uiUpdater(this@AccountPickerActivity).requestUpdate()
            finish()
        }
    }
}
