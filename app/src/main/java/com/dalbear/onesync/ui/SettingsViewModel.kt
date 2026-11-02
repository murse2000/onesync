package com.dalbear.onesync.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.dalbear.onesync.sync.AutoSyncScheduler
import com.dalbear.onesync.sync.AutoSyncStore

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AutoSyncStore(application)

    var autoSyncEnabled by mutableStateOf(store.enabled)
        private set

    var autoSyncInterval by mutableStateOf(store.intervalMinutes)
        private set

    init {
        // 앱이 시작될 때 등록 상태를 실제 스케줄과 맞춘다.
        applySchedule()
    }

    fun updateAutoSyncEnabled(value: Boolean) {
        store.enabled = value
        autoSyncEnabled = value
        applySchedule()
    }

    fun updateInterval(minutes: Int) {
        store.intervalMinutes = minutes
        autoSyncInterval = minutes
        applySchedule()
    }

    private fun applySchedule() {
        AutoSyncScheduler.apply(getApplication(), autoSyncEnabled, autoSyncInterval)
    }
}
