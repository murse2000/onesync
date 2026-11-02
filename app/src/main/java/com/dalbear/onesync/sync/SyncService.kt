package com.dalbear.onesync.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dalbear.onesync.MainActivity
import com.dalbear.onesync.OneSyncApp
import com.dalbear.onesync.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * 수동 동기화를 포그라운드 서비스에서 실행한다.
 * 알림의 취소 액션으로 진행 중인 동기화를 중단할 수 있다.
 */
class SyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var generation = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                job?.cancel()
                SyncCoordinator.finish("취소됨")
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val pairId = intent.getLongExtra(EXTRA_PAIR_ID, -1L)
                SyncCoordinator.begin()
                startForegroundCompat("동기화 준비 중…")
                job?.cancel()
                val currentGeneration = ++generation
                job = scope.launch { run(pairId, currentGeneration) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun run(pairId: Long, runGeneration: Int) {
        var result: String? = null
        try {
            val container = (application as OneSyncApp).container
            val sessions = container.sessions
            if (sessions.list().isEmpty()) throw IllegalStateException("로그인이 필요합니다")

            val pairs = if (pairId > 0) {
                listOfNotNull(container.repo.find(pairId))
            } else {
                container.repo.list()
            }
            if (pairs.isEmpty()) {
                result = "동기화할 폴더가 없습니다"
                return
            }

            val lines = ArrayList<String>()
            var completed = 0
            for (pair in pairs) {
                currentCoroutineContext().ensureActive()
                // 폴더 쌍마다 계정이 다를 수 있으므로 안에서 토큰을 받는다.
                val tokenResult = sessions.tokenFor(pair.accountId)
                if (tokenResult.isFailure) {
                    val message = tokenResult.exceptionOrNull()?.localizedMessage
                        ?: "계정 로그인이 필요합니다"
                    container.repo.addEvent(pair.id, "error", null, message)
                    lines += "${pair.name}: $message"
                    continue
                }
                val token = tokenResult.getOrThrow()
                val summary = container.engine.sync(pair, token) { message ->
                    val text = "${pair.name}: $message"
                    SyncCoordinator.progress(text)
                    updateNotification(text)
                }
                lines += "${pair.name}: ${summary.toMessage()}"
                completed++
            }
            result = (if (completed == 0) "동기화 실패\n" else "동기화 완료\n") +
                lines.joinToString("\n")
        } catch (e: CancellationException) {
            result = "취소됨"
        } catch (e: Exception) {
            result = "동기화 실패: ${e.localizedMessage ?: e.javaClass.simpleName}"
        } finally {
            // 새 동기화가 시작됐으면 이 작업의 종료가 서비스를 내리면 안 된다.
            if (runGeneration == generation) {
                SyncCoordinator.finish(result)
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun startForegroundCompat(text: String) {
        val notification = buildNotification(text)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun updateNotification(text: String) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(text: String): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SyncService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("동기화 중")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, "취소", cancelIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "동기화 진행",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val CHANNEL_ID = "syncing"
        private const val NOTIFICATION_ID = 100

        private const val ACTION_START = "com.dalbear.onesync.action.START_SYNC"
        private const val ACTION_CANCEL = "com.dalbear.onesync.action.CANCEL_SYNC"
        private const val EXTRA_PAIR_ID = "pairId"

        fun start(context: Context, pairId: Long = -1L) {
            val intent = Intent(context, SyncService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PAIR_ID, pairId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, SyncService::class.java).setAction(ACTION_CANCEL),
            )
        }
    }
}
