package dev.imkdw.claudewatch.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.imkdw.claudewatch.domain.DisplayState
import dev.imkdw.claudewatch.domain.Level

class UsageComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        ComplicationBuilder.forSelected(this, request.complicationType)

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        ComplicationBuilder.build(type, PREVIEW, null)

    private companion object {
        val PREVIEW = DisplayState("personal", 42, null, 18, null, emptyList(), "", Level.NORMAL)
    }
}
