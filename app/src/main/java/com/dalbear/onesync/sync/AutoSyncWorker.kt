package com.dalbear.onesync.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dalbear.onesync.OneSyncApp

/**
 * 등록된 모든 폴더 쌍을 계정별 토큰으로 순서대로 동기화한다.
 * 로그인이 풀렸거나 토큰을 조용히 갱신할 수 없으면 재시도하지 않고 조용히 끝낸다.
 */
class AutoSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OneSyncApp).container
        val sessions = container.sessions
        if (sessions.list().isEmpty()) return Result.success()

        // 직접 누른 동기화가 아직 돌고 있으면 이번 주기는 건너뛴다.
        // 끝나자마자 같은 전체 스캔을 한 번 더 하는 걸 막는다.
        if (SyncCoordinator.state.value.running) return Result.success()

        val now = System.currentTimeMillis()
        val lastSync = container.repo.lastSyncTimes()
        // 방금 동기화한 폴더 쌍은 다시 스캔하지 않는다.
        val pairs = container.repo.listEnabled()
            .filter { isSyncDue(lastSync[it.id], now) }
        if (pairs.isEmpty()) return Result.success()

        var failures = 0
        var authFailures = 0
        for (pair in pairs) {
            val tokenResult = sessions.tokenFor(pair.accountId)
            if (tokenResult.isFailure) {
                // 로그인이 풀린 것은 기다린다고 풀리지 않으므로 재시도하지 않는다.
                authFailures++
                val message = tokenResult.exceptionOrNull()?.localizedMessage
                    ?: "계정 로그인이 필요합니다"
                container.repo.addEvent(pair.id, "error", null, message)
                continue
            }
            try {
                container.engine.sync(pair, tokenResult.getOrThrow())
            } catch (e: Exception) {
                failures++
                container.repo.addEvent(pair.id, "error", null, e.localizedMessage)
            }
        }

        // 전부 네트워크/일시 오류로 실패했을 때만 백오프 재시도를 요청한다.
        return if (failures == pairs.size && authFailures == 0) Result.retry() else Result.success()
    }

}

/** 이 시간 안에 끝난 동기화는 곧바로 다시 돌지 않는다. */
const val RECENT_SYNC_WINDOW_MS = 2 * 60 * 1000L

/**
 * 주기 실행 시점에 이 폴더 쌍을 동기화해야 하는지.
 * 방금(수동) 동기화가 끝났거나, 기기 시계가 앞서 있어 마지막 기록이 미래면 건너뛴다.
 */
fun isSyncDue(
    lastSyncAt: Long?,
    now: Long,
    windowMs: Long = RECENT_SYNC_WINDOW_MS,
): Boolean = lastSyncAt == null || now - lastSyncAt >= windowMs
