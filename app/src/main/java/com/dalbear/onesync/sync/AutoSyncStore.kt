package com.dalbear.onesync.sync

import android.content.Context

class AutoSyncStore(context: Context) {

    private val prefs = context.getSharedPreferences("autosync", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    var intervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL)
        set(value) {
            prefs.edit().putInt(KEY_INTERVAL, value).apply()
        }

    companion object {
        const val DEFAULT_INTERVAL = 60
        /** WorkManager 주기 작업의 최소 간격. */
        const val MIN_INTERVAL = 15
        val INTERVALS = listOf(15, 30, 60, 180, 360)
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "interval"
    }
}

fun autoSyncIntervalLabel(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60}시간마다"
    else -> "${minutes}분마다"
}
