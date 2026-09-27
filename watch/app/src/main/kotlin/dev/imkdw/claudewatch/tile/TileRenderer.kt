package dev.imkdw.claudewatch.tile

import android.content.Context
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.TileBuilders.Tile
import dev.imkdw.claudewatch.data.UsageSource
import dev.imkdw.claudewatch.data.snapshot
import dev.imkdw.claudewatch.domain.DisplayCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.util.concurrent.TimeUnit

/** onTileRequest의 본체. 서비스와 분리해 가짜 저장소와 가상 시간으로 테스트한다 */
class TileRenderer(
    private val context: Context,
    private val source: UsageSource,
    private val clock: Clock,
    private val refreshScope: CoroutineScope,
    private val refreshTimeoutMillis: Long = REFRESH_TIMEOUT_MILLIS,
) {
    suspend fun render(lastClickableId: String?, deviceParams: DeviceParameters): Tile {
        if (lastClickableId == TileIds.REFRESH) {
            // F4: 최대 5초 기다린다. 넘기면 캐시로 그리고, 조회는 뒤에서 마저 끝낸다
            val refresh = refreshScope.async { source.refresh() }
            withTimeoutOrNull(refreshTimeoutMillis) { refresh.await() }
        }
        val state = DisplayCalculator.toDisplay(source.snapshot().selectedFile, clock.instant(), clock.zone)
        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(tileLayout(context, state, deviceParams)))
            .build()
    }

    companion object {
        const val RESOURCES_VERSION = "1"
        const val REFRESH_TIMEOUT_MILLIS = 5_000L
        val FRESHNESS_MILLIS = TimeUnit.MINUTES.toMillis(15)
    }
}
