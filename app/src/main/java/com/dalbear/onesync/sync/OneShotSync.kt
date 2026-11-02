package com.dalbear.onesync.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/** 즉시 업로드: 파일 변경 후 한 번만 동기화를 예약한다. */
object OneShotSync {

    private const val WORK_NAME = "onesync-instant-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setConstraints(AutoSyncStore(context).constraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            // 실행 중인 동기화를 취소하지 않고 뒤에 붙인다. 중복 업로드를 막는다.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
