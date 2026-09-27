package dev.imkdw.claudewatch

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.imkdw.claudewatch.data.GistClient
import dev.imkdw.claudewatch.data.UsageRepository
import dev.imkdw.claudewatch.data.UsageSource
import dev.imkdw.claudewatch.data.UsageStore
import dev.imkdw.claudewatch.notify.AlertCheck
import dev.imkdw.claudewatch.notify.AlertEvaluator
import dev.imkdw.claudewatch.notify.Notifier
import dev.imkdw.claudewatch.work.SystemUiUpdater
import dev.imkdw.claudewatch.work.UiUpdater
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Clock
import java.time.ZoneId

private val Context.usageDataStore by preferencesDataStore(name = "usage")

/** 서비스, 워커, 액티비티가 함께 쓰는 의존성. 테스트는 override로 바꾼다 */
object Graph {
    @Volatile private var sourceOverride: UsageSource? = null
    @Volatile private var uiUpdaterOverride: UiUpdater? = null
    @Volatile private var clockOverride: Clock? = null
    @Volatile private var alertCheckOverride: AlertCheck? = null
    @Volatile private var store: UsageStore? = null
    @Volatile private var repository: UsageRepository? = null

    val clock: Clock get() = clockOverride ?: Clock.systemDefaultZone()
    val zone: ZoneId get() = clock.zone

    fun store(context: Context): UsageStore = store ?: synchronized(this) {
        store ?: UsageStore(context.applicationContext.usageDataStore).also { store = it }
    }

    fun source(context: Context): UsageSource = sourceOverride ?: repository ?: synchronized(this) {
        repository ?: UsageRepository(GistClient(BuildConfig.GIST_ID, baseUrl = BuildConfig.GIST_API_BASE.toHttpUrl()), store(context), clock).also { repository = it }
    }

    fun uiUpdater(context: Context): UiUpdater = uiUpdaterOverride ?: SystemUiUpdater(context.applicationContext)

    fun alertCheck(context: Context): AlertCheck =
        alertCheckOverride ?: AlertEvaluator(store(context), Notifier(context.applicationContext), clock)

    fun override(
        source: UsageSource? = null,
        uiUpdater: UiUpdater? = null,
        clock: Clock? = null,
        alertCheck: AlertCheck? = null,
    ) {
        sourceOverride = source
        uiUpdaterOverride = uiUpdater
        clockOverride = clock
        alertCheckOverride = alertCheck
    }

    fun reset() = override()
}
