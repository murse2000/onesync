package com.dalbear.onesync.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalbear.onesync.OneSyncApp
import com.dalbear.onesync.data.ConflictRow
import com.dalbear.onesync.sync.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConflictsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OneSyncApp).container
    private val repo = container.repo

    var conflicts by mutableStateOf<List<ConflictRow>>(emptyList())
        private set

    var busy by mutableStateOf(false)
        private set

    fun load() {
        viewModelScope.launch {
            conflicts = withContext(Dispatchers.IO) { repo.unresolvedConflicts() }
        }
    }

    fun resolve(conflict: ConflictRow, keepLocal: Boolean) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                if (keepLocal) {
                    container.conflictResolver.keepLocal(conflict)
                } else {
                    container.conflictResolver.keepRemote(conflict)
                }
                conflicts = withContext(Dispatchers.IO) { repo.unresolvedConflicts() }
                // 원본이 바뀌었을 수 있으니 한 번 동기화한다.
                SyncService.start(getApplication(), conflict.pairId)
            } finally {
                busy = false
            }
        }
    }
}
