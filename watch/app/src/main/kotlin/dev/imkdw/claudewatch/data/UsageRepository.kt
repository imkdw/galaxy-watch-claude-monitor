package dev.imkdw.claudewatch.data

import dev.imkdw.claudewatch.domain.AccountResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock

sealed interface RefreshOutcome {
    data object Updated : RefreshOutcome
    data object Unchanged : RefreshOutcome
    data class Failed(val reason: FailReason) : RefreshOutcome
}

/** 캐시 전체와, 선택 규칙(AccountResolver)을 적용한 선택 계정 */
data class Snapshot(val accounts: Map<String, UsageFile>, val selected: String?) {
    val selectedFile: UsageFile? get() = selected?.let(accounts::get)

    companion object {
        fun of(accounts: Map<String, UsageFile>, storedSelected: String?) =
            Snapshot(accounts, AccountResolver.resolve(storedSelected, accounts))
    }
}

interface UsageSource {
    suspend fun refresh(): RefreshOutcome
    fun observe(): Flow<Snapshot>
    suspend fun select(label: String)
}

suspend fun UsageSource.snapshot(): Snapshot = observe().first()

class UsageRepository(
    private val gist: GistSource,
    private val store: UsageStore,
    private val clock: Clock,
) : UsageSource {

    override suspend fun refresh(): RefreshOutcome {
        val cached = store.current()
        // 캐시가 비었는데 304를 받으면 영영 빈 화면이 되므로 etag를 보내지 않는다
        val etag = cached.etag.takeIf { cached.accounts.isNotEmpty() }
        return when (val result = gist.fetch(etag)) {
            is FetchResult.NotModified -> RefreshOutcome.Unchanged
            is FetchResult.Failed -> RefreshOutcome.Failed(result.reason)
            is FetchResult.Updated -> {
                val accounts = try {
                    GistParser.parse(result.body)
                } catch (_: GistFormatException) {
                    return RefreshOutcome.Failed(FailReason.PARSE)
                }
                store.saveSnapshot(accounts, result.etag, clock.instant())
                RefreshOutcome.Updated
            }
        }
    }

    override fun observe(): Flow<Snapshot> = store.data.map { Snapshot.of(it.accounts, it.selected) }

    override suspend fun select(label: String) = store.select(label)
}
