package dev.imkdw.claudewatch

import android.Manifest
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.connection.DefaultTileClient
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import dev.imkdw.claudewatch.tile.UsageTileService
import dev.imkdw.claudewatch.ui.AccountPickerActivity
import dev.imkdw.claudewatch.work.RefreshScheduler
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** 계획 3.5: Wear OS 에뮬레이터 스모크 테스트 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun e1_앱이_뜨면_refresh_주기_작업이_등록된다() {
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(RefreshScheduler.NAME).get(5, TimeUnit.SECONDS)
        assertThat(infos).hasSize(1)
        assertThat(infos.single().periodicityInfo?.repeatIntervalMillis).isEqualTo(TimeUnit.MINUTES.toMillis(15))
    }

    @Test
    fun e2_계정_선택_화면이_크래시_없이_뜬다() {
        // 권한 요청 대화상자가 화면을 가리지 않게 미리 허용한다
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        ActivityScenario.launch(AccountPickerActivity::class.java).use { scenario ->
            Thread.sleep(1_000)
            assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun e3_타일_서비스에_바인딩해서_타일을_받는다() {
        val client = DefaultTileClient(
            context,
            ComponentName(context, UsageTileService::class.java),
            MoreExecutors.directExecutor(),
        )
        val device = DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(192)
            .setScreenHeightDp(192)
            .setScreenDensity(2f)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .build()
        val tile = client.requestTile(RequestBuilders.TileRequest.Builder().setDeviceConfiguration(device).build())
            .get(10, TimeUnit.SECONDS)
        assertThat(tile.tileTimeline?.timelineEntries?.single()?.layout).isNotNull()
    }
}
