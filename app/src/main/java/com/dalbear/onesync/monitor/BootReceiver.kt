package com.dalbear.onesync.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dalbear.onesync.sync.AutoSyncStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (AutoSyncStore(context).instantUpload) {
            MonitoringController.apply(context, enabled = true)
        }
    }
}

object MonitoringController {

    fun apply(context: Context, enabled: Boolean) {
        val intent = Intent(context, MonitoringService::class.java)
        if (!enabled) {
            context.stopService(intent)
            return
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
