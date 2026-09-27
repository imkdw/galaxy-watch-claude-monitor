package dev.imkdw.claudewatch.tile

import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.imkdw.claudewatch.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

class UsageTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> = scope.future {
        TileRenderer(this@UsageTileService, Graph.source(this@UsageTileService), Graph.clock, scope)
            .render(requestParams.currentState.lastClickableId, requestParams.deviceConfiguration)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> =
        Futures.immediateFuture(Resources.Builder().setVersion(TileRenderer.RESOURCES_VERSION).build())

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
