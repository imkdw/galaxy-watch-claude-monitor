package dev.imkdw.claudewatch.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.imkdw.claudewatch.data.FetchResult
import dev.imkdw.claudewatch.data.GistSource
import dev.imkdw.claudewatch.data.RefreshOutcome
import dev.imkdw.claudewatch.data.Snapshot
import dev.imkdw.claudewatch.data.UsageFile
import dev.imkdw.claudewatch.data.UsageSource
import dev.imkdw.claudewatch.work.UiUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.nio.file.Files

fun tempDataStore(): DataStore<Preferences> {
    val dir = Files.createTempDirectory("usage-store").toFile().apply { deleteOnExit() }
    return PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        File(dir, "usage.preferences_pb")
    }
}

class FakeGistSource(vararg results: FetchResult) : GistSource {
    private val queue = ArrayDeque(results.toList())
    val etags = mutableListOf<String?>()
    override suspend fun fetch(etag: String?): FetchResult {
        etags += etag
        return queue.removeFirst()
    }
}

class FakeUiUpdater : UiUpdater {
    var count = 0
    override fun requestUpdate() {
        count++
    }
}

/** 타일, 컴플리케이션, 화면 테스트용 가짜 저장소 */
class FakeUsageSource(
    accounts: Map<String, UsageFile> = emptyMap(),
    selected: String? = null,
    var refreshDelayMillis: Long = 0,
    var outcome: RefreshOutcome = RefreshOutcome.Unchanged,
    private val afterRefresh: Map<String, UsageFile>? = null,
) : UsageSource {
    val state = MutableStateFlow(Snapshot.of(accounts, selected))
    var refreshCount = 0
    val selections = mutableListOf<String>()

    override suspend fun refresh(): RefreshOutcome {
        refreshCount++
        delay(refreshDelayMillis)
        afterRefresh?.let { state.value = Snapshot.of(it, state.value.selected) }
        return outcome
    }

    override fun observe(): Flow<Snapshot> = state

    override suspend fun select(label: String) {
        selections += label
        state.value = Snapshot.of(state.value.accounts, label)
    }
}
