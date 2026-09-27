package dev.imkdw.claudewatch

import android.app.Application
import dev.imkdw.claudewatch.work.RefreshScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshScheduler.schedule(this)
    }
}
