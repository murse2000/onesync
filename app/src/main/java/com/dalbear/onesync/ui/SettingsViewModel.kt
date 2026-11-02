package com.dalbear.onesync.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalbear.onesync.OneSyncApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dalbear.onesync.monitor.MonitoringController
import com.dalbear.onesync.sync.AutoSyncScheduler
import com.dalbear.onesync.sync.AutoSyncStore
import com.dalbear.onesync.sync.ExcludeStore

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AutoSyncStore(application)
    private val excludes = ExcludeStore(application)
    private val repo = (application as OneSyncApp).container.repo

    var autoSyncEnabled by mutableStateOf(store.enabled)
        private set

    var autoSyncInterval by mutableStateOf(store.intervalMinutes)
        private set

    var instantUploadEnabled by mutableStateOf(store.instantUpload)
        private set

    var wifiOnly by mutableStateOf(store.wifiOnly)
        private set

    var chargingOnly by mutableStateOf(store.chargingOnly)
        private set

    var batteryNotLow by mutableStateOf(store.batteryNotLow)
        private set

    var excludePatterns by mutableStateOf(excludes.patterns)
        private set

    var maxFileSizeMb by mutableStateOf(excludes.maxFileSizeMb)
        private set

    val exclusionSummary: String
        get() {
            val count = ExcludeStore.parsePatterns(excludePatterns).size
            val size = if (maxFileSizeMb > 0) " · ${maxFileSizeMb}MB 초과 제외" else ""
            return if (count == 0 && maxFileSizeMb == 0) "없음" else "${count}개 패턴$size"
        }

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

    fun updateWifiOnly(value: Boolean) {
        store.wifiOnly = value
        wifiOnly = value
        applySchedule()
    }

    fun updateChargingOnly(value: Boolean) {
        store.chargingOnly = value
        chargingOnly = value
        applySchedule()
    }

    fun updateBatteryNotLow(value: Boolean) {
        store.batteryNotLow = value
        batteryNotLow = value
        applySchedule()
    }

    fun updateExclusions(patterns: String, maxFileSizeMb: Int) {
        excludes.patterns = patterns
        excludes.maxFileSizeMb = maxFileSizeMb
        excludePatterns = patterns
        this.maxFileSizeMb = maxFileSizeMb
    }

    /** 폴더 쌍 설정을 JSON으로 만든다. */
    fun exportJson(onReady: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onReady(runCatching { withContext(Dispatchers.IO) { repo.exportJson() } })
        }
    }

    /** 내보낸 JSON에서 폴더 쌍을 복원한다. */
    fun importJson(text: String, onDone: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { withContext(Dispatchers.IO) { repo.importJson(text) } })
        }
    }

    private fun applySchedule() {
        AutoSyncScheduler.apply(getApplication())
    }

    private fun hasPairs(): Boolean = repo.list().isNotEmpty()
}
