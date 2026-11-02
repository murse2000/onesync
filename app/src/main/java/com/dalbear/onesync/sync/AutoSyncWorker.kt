package com.dalbear.onesync.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dalbear.onesync.OneSyncApp

/**
 * 등록된 모든 폴더 쌍을 순서대로 동기화한다.
 * 로그인이 풀렸거나 토큰을 조용히 갱신할 수 없으면 재시도하지 않고 조용히 끝낸다.
 */
class AutoSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OneSyncApp).container
        val clientId = container.authStore.clientId ?: return Result.success()
        val account = container.auth.accounts(clientId).firstOrNull() ?: return Result.success()
        val token = container.auth.accessToken(clientId, account).getOrElse {
            return Result.success()
        }

        val pairs = container.repo.list()
        if (pairs.isEmpty()) return Result.success()

        var failures = 0
        for (pair in pairs) {
            try {
                container.engine.sync(pair, token)
            } catch (e: Exception) {
                failures++
                container.repo.addEvent(pair.id, "error", null, e.localizedMessage)
            }
        }

        // 전부 실패했을 때만 백오프 재시도를 요청한다.
        return if (failures == pairs.size) Result.retry() else Result.success()
    }
}
