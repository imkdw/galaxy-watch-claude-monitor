package dev.imkdw.claudewatch.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.Graph
import dev.imkdw.claudewatch.data.FailReason
import dev.imkdw.claudewatch.data.RefreshOutcome
import dev.imkdw.claudewatch.testing.FakeUiUpdater
import dev.imkdw.claudewatch.testing.FakeUsageSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RefreshWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ui = FakeUiUpdater()
    private val source = FakeUsageSource()

    @Before
    fun setUp() {
        Graph.override(source = source, uiUpdater = ui)
    }

    @After
    fun tearDown() {
        Graph.reset()
    }

    private suspend fun runWorker() = TestListenableWorkerBuilder<RefreshWorker>(context).build().doWork()

    @Test
    fun `W1 조회 성공이면 success, 화면 갱신 1회`() = runTest {
        source.outcome = RefreshOutcome.Updated
        assertThat(runWorker()).isEqualTo(ListenableWorker.Result.success())
        assertThat(source.refreshCount).isEqualTo(1)
        assertThat(ui.count).isEqualTo(1)
    }

    @Test
    fun `W2 네트워크 실패도 success, 화면 갱신 1회`() = runTest {
        source.outcome = RefreshOutcome.Failed(FailReason.NETWORK)
        assertThat(runWorker()).isEqualTo(ListenableWorker.Result.success())
        assertThat(ui.count).isEqualTo(1)
    }

    @Test
    fun `W2 304도 화면 갱신 1회 (리셋, 상대 시간 반영)`() = runTest {
        source.outcome = RefreshOutcome.Unchanged
        runWorker()
        assertThat(ui.count).isEqualTo(1)
    }

    @Test
    fun `W3 스케줄러는 unique 작업 1개, 15분 주기, 네트워크 조건`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        RefreshScheduler.schedule(context)
        RefreshScheduler.schedule(context)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(RefreshScheduler.NAME).get()
        assertThat(infos).hasSize(1)
        val info = infos.single()
        assertThat(info.periodicityInfo?.repeatIntervalMillis).isEqualTo(TimeUnit.MINUTES.toMillis(15))
        assertThat(info.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }
}
