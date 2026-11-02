package com.dalbear.onesync.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/** 즉시 업로드: 파일 변경 후 한 번만 동기화를 예약한다. */
object OneShotSync {

    private const val WORK_NAME = "onesync-instant-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
