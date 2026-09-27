package dev.imkdw.claudewatch.tile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.tiles.TileBuilders.Tile
import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.data.Window
import dev.imkdw.claudewatch.testing.FakeUsageSource
import dev.imkdw.claudewatch.testing.collectTexts
import dev.imkdw.claudewatch.testing.usageFile
import dev.imkdw.claudewatch.testing.watchDevice
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TileRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.parse("2026-09-27T13:48:00Z"), ZoneId.of("Asia/Seoul"))
    private val cached = usageFile(session = Window(42, "2026-09-27T15:00:00Z"), changedAt = "2026-09-27T13:45:00Z")
    private val fresh = cached.copy(session = Window(55, "2026-09-27T15:00:00Z"))

    private fun Tile.texts(): List<String> = collectTexts(tileTimeline!!.timelineEntries.single().layout!!.root as LayoutElement)

    private fun TestScope.renderer(source: FakeUsageSource) = TileRenderer(context, source, clock, backgroundScope)

    @Test
    fun `TL5 새로고침 클릭이면 refresh 1회 후 새 값으로 그린다`() = runTest {
        val source = FakeUsageSource(mapOf("personal" to cached), afterRefresh = mapOf("personal" to fresh))
        val tile = renderer(source).render(TileIds.REFRESH, watchDevice)
        assertThat(source.refreshCount).isEqualTo(1)
        assertThat(tile.texts()).contains("55%")
    }

    @Test
    fun `클릭이 없으면 refresh 하지 않는다`() = runTest {
        val source = FakeUsageSource(mapOf("personal" to cached))
        renderer(source).render(null, watchDevice)
        renderer(source).render(TileIds.ACCOUNT, watchDevice)
        assertThat(source.refreshCount).isEqualTo(0)
    }

    @Test
    fun `TL6 refresh가 5초를 넘기면 캐시 값으로 그린다`() = runTest {
        val source = FakeUsageSource(mapOf("personal" to cached), refreshDelayMillis = 30_000, afterRefresh = mapOf("personal" to fresh))
        val tile = renderer(source).render(TileIds.REFRESH, watchDevice)
        assertThat(currentTime).isEqualTo(5_000)
        assertThat(tile.texts()).contains("42%")
    }

    @Test
    fun `리셋 계산과 신선도 15분`() = runTest {
        val tile = renderer(FakeUsageSource(mapOf("personal" to cached))).render(null, watchDevice)
        assertThat(tile.texts()).containsAtLeast("42%", "1시간 12분 후 리셋", "3분 전 변경")
        assertThat(tile.freshnessIntervalMillis).isEqualTo(15 * 60 * 1000L)
        assertThat(tile.resourcesVersion).isEqualTo(TileRenderer.RESOURCES_VERSION)
    }

    @Test
    fun `계정이 없으면 데이터 없음`() = runTest {
        val tile = renderer(FakeUsageSource()).render(null, watchDevice)
        assertThat(tile.texts()).contains("데이터 없음. 수집기 확인")
    }
}
