package com.dalbear.onesync.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.dalbear.onesync.OneSyncApp
import com.dalbear.onesync.monitor.MonitoringController
import com.dalbear.onesync.sync.AutoSyncScheduler
import com.dalbear.onesync.sync.AutoSyncStore

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AutoSyncStore(application)

    var autoSyncEnabled by mutableStateOf(store.enabled)
        private set

    var autoSyncInterval by mutableStateOf(store.intervalMinutes)
        private set

    var instantUploadEnabled by mutableStateOf(store.instantUpload)
        private set

    init {
        applySchedule()
        // 폴더 쌍이 있을 때만 감시 서비스를 띄운다.
        if (instantUploadEnabled && hasPairs()) {
            MonitoringController.apply(getApplication(), enabled = true)
        }
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

    fun updateInstantUpload(value: Boolean) {
        store.instantUpload = value
        instantUploadEnabled = value
        if (value && !hasPairs()) return
        MonitoringController.apply(getApplication(), enabled = value)
    }

    private fun applySchedule() {
        AutoSyncScheduler.apply(getApplication(), autoSyncEnabled, autoSyncInterval)
    }

    private fun hasPairs(): Boolean =
        (getApplication<Application>() as OneSyncApp).container.repo.list().isNotEmpty()
}
