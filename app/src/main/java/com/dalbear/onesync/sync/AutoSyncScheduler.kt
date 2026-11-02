package com.dalbear.onesync.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 주기 동기화 작업을 등록/해제한다.
 * WorkManager가 재부팅 후에도 주기 작업을 복원하므로 별도 BOOT receiver는 필요 없다.
 */
object AutoSyncScheduler {

    private const val WORK_NAME = "onesync-periodic-sync"

    fun apply(context: Context) {
        val store = AutoSyncStore(context)
        val workManager = WorkManager.getInstance(context)
        if (!store.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val interval = store.intervalMinutes.coerceAtLeast(AutoSyncStore.MIN_INTERVAL)
        val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setConstraints(store.constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
