package dev.imkdw.claudewatch.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.imkdw.claudewatch.Graph
import dev.imkdw.claudewatch.data.snapshot
import kotlin.coroutines.cancellation.CancellationException

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val source = Graph.source(applicationContext)
            val outcome = source.refresh()
            Log.i(TAG, "refresh: $outcome")
            Graph.alertCheck(applicationContext).check(source.snapshot().accounts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refresh 실패", e)
        } finally {
            Graph.uiUpdater(applicationContext).requestUpdate()
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "RefreshWorker"
    }
}
