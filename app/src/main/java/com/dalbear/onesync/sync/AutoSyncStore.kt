package com.dalbear.onesync.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType

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

    var instantUpload: Boolean
        get() = prefs.getBoolean(KEY_INSTANT_UPLOAD, true)
        set(value) {
            prefs.edit().putBoolean(KEY_INSTANT_UPLOAD, value).apply()
        }

    /** Wi-Fi 등 종량제가 아닌 네트워크에서만 실행한다. */
    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        }

    var chargingOnly: Boolean
        get() = prefs.getBoolean(KEY_CHARGING_ONLY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CHARGING_ONLY, value).apply()
        }

    /** Android가 배터리 부족으로 판단하면(보통 15% 내외) 실행하지 않는다. */
    var batteryNotLow: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_NOT_LOW, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BATTERY_NOT_LOW, value).apply()
        }

    /** 백그라운드 동기화(주기·즉시 업로드)에 적용하는 제약. 수동 동기화에는 걸지 않는다. */
    fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(chargingOnly)
        .setRequiresBatteryNotLow(batteryNotLow)
        .build()

    companion object {
        const val DEFAULT_INTERVAL = 60
        /** WorkManager 주기 작업의 최소 간격. */
        const val MIN_INTERVAL = 15
        val INTERVALS = listOf(15, 30, 60, 180, 360)
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_INSTANT_UPLOAD = "instant_upload"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_CHARGING_ONLY = "charging_only"
        private const val KEY_BATTERY_NOT_LOW = "battery_not_low"
    }
}

fun autoSyncIntervalLabel(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60}시간마다"
    else -> "${minutes}분마다"
}
