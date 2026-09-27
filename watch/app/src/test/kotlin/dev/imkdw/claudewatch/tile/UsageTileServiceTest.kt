package dev.imkdw.claudewatch.tile

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.testing.TestTileClient
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import dev.imkdw.claudewatch.Graph
import dev.imkdw.claudewatch.testing.FakeUsageSource
import dev.imkdw.claudewatch.testing.usageFile
import dev.imkdw.claudewatch.testing.watchDevice
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class UsageTileServiceTest {

    @After
    fun tearDown() = Graph.reset()

    @Test
    fun `TL4 TestTileClient로 타일을 요청하면 예외 없이 15분 신선도 타일`() {
        Graph.override(source = FakeUsageSource(mapOf("personal" to usageFile())))
        val service = UsageTileService()
        val client = TestTileClient(service, MoreExecutors.directExecutor())

        val future = client.requestTile(RequestBuilders.TileRequest.Builder().setDeviceConfiguration(watchDevice).build())
        shadowOf(Looper.getMainLooper()).idle()
        val tile = future.get(5, TimeUnit.SECONDS)

        assertThat(tile.freshnessIntervalMillis).isEqualTo(TimeUnit.MINUTES.toMillis(15))
        assertThat(tile.tileTimeline?.timelineEntries).hasSize(1)
    }
}
