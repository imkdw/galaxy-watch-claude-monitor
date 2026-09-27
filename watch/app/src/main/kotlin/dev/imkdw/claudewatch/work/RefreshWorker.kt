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
            // P1: 실패나 304여도 판정한다 (워치가 계산한 리셋도 알림 대상)
            Graph.alertCheck(applicationContext).check(source.snapshot().accounts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refresh 실패", e)
        } finally {
            // 성공, 304, 실패 모두: 리셋 계산과 "N분 전"은 시간이 흐르면 바뀐다
            Graph.uiUpdater(applicationContext).requestUpdate()
        }
        // 실패해도 15분 뒤 다시 돈다. 백오프 재시도로 배터리를 쓰지 않는다 (계획 P6)
        return Result.success()
    }

    private companion object {
        const val TAG = "RefreshWorker"
    }
}
