package com.dalbear.onesync.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dalbear.onesync.MainActivity
import com.dalbear.onesync.OneSyncApp
import com.dalbear.onesync.R
import com.dalbear.onesync.sync.OneShotSync
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 폴더 변경을 감시하다가 잠시 조용해지면 동기화를 한 번 예약한다.
 * Android 12+에서 백그라운드 실행을 유지하려면 포그라운드 서비스여야 한다.
 */
class MonitoringService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val observers = ArrayList<RecursiveFileObserver>()
    private var pendingSync: Job? = null
    private var pendingRefresh: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        refreshWatchers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshWatchers()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observers.forEach { it.stop() }
        observers.clear()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        createChannel()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("파일 변경 감시 중")
            .setContentText("폴더가 바뀌면 자동으로 동기화합니다.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .build()

        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "파일 변경 감시",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "폴더 변경을 감지해 자동 동기화를 실행합니다."
            },
        )
    }

    private fun refreshWatchers() {
        observers.forEach { it.stop() }
        observers.clear()

        val container = (application as OneSyncApp).container
        for (pair in container.repo.listEnabled()) {
            val root = File(pair.localRoot)
            if (!root.isDirectory) continue
            val observer = RecursiveFileObserver(
                root = root,
                recursive = pair.includeSubdirectories,
            ) { isDirectory ->
                onFileSystemEvent(isDirectory)
            }
            observer.start()
            observers += observer
        }
    }

    private fun onFileSystemEvent(isDirectory: Boolean) {
        pendingSync?.cancel()
        pendingSync = scope.launch {
            // 복사/저장이 끝날 때까지 잠깐 기다린 뒤 한 번만 동기화한다.
            delay(DEBOUNCE_MS)
            OneShotSync.enqueue(this@MonitoringService)
        }

        if (isDirectory) {
            // 새 폴더가 생기면 감시 목록을 다시 만들어야 한다.
            pendingRefresh?.cancel()
            pendingRefresh = scope.launch {
                delay(REFRESH_DELAY_MS)
                withContext(Dispatchers.Main) { refreshWatchers() }
            }
        }
    }

    private companion object {
        const val CHANNEL_ID = "monitoring"
        const val NOTIFICATION_ID = 200
        const val DEBOUNCE_MS = 4_000L
        const val REFRESH_DELAY_MS = 1_500L
    }
}
