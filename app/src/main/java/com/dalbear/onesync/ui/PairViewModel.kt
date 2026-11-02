package com.dalbear.onesync.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalbear.onesync.OneSyncApp
import com.dalbear.onesync.core.sync.SyncMethod
import com.dalbear.onesync.data.FolderPair
import com.dalbear.onesync.sync.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OneSyncApp).container
    private val repo = container.repo

    var pairs by mutableStateOf<List<FolderPair>>(emptyList())
        private set

    var conflictCount by mutableStateOf(0)
        private set

    /** 계정 ID → 이메일. 폴더 쌍이 어느 계정 것인지 표시할 때 쓴다. */
    var accountLabels by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    // 새 폴더 쌍 화면에서 임시로 들고 있는 선택값
    var pendingRemoteId: String? = null
    var pendingRemotePath: String? = null
    var pendingLocalPath: String? = null
    var pendingIncludeSubdirectories by mutableStateOf(true)

    fun loadPairs() {
        viewModelScope.launch {
            pairs = withContext(Dispatchers.IO) { repo.list() }
            conflictCount = withContext(Dispatchers.IO) { repo.conflictCount() }
            accountLabels = withContext(Dispatchers.IO) {
                container.sessions.list().associate { it.id to it.username }
            }
        }
    }

    fun createPair(
        name: String,
        method: SyncMethod,
        includeSubdirectories: Boolean,
        accountId: String?,
    ): Boolean {
        val remoteId = pendingRemoteId ?: return false
        val remotePath = pendingRemotePath ?: return false
        val localPath = pendingLocalPath ?: return false
        val trimmed = name.trim().ifEmpty { "OneDrive 동기화" }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.create(
                    trimmed,
                    localPath,
                    remoteId,
                    remotePath,
                    method,
                    includeSubdirectories,
                    accountId,
                )
            }
            clearPending()
            loadPairs()
        }
        return true
    }

    fun clearPending() {
        pendingRemoteId = null
        pendingRemotePath = null
        pendingLocalPath = null
        pendingIncludeSubdirectories = true
    }

    /** 계정을 바꾸면 이전 계정에서 고른 원격 폴더는 쓸 수 없으므로 지운다. */
    fun clearPendingRemote() {
        pendingRemoteId = null
        pendingRemotePath = null
    }

    fun deletePair(pair: FolderPair) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(pair.id) }
            loadPairs()
        }
    }

    fun updateOptions(pair: FolderPair, method: SyncMethod, includeSubdirectories: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.updateOptions(pair.id, method, includeSubdirectories) }
            loadPairs()
        }
    }

    fun sync(pair: FolderPair) {
        SyncService.start(getApplication(), pair.id)
    }

    fun syncAll() {
        SyncService.start(getApplication())
    }

    fun cancel() {
        SyncService.cancel(getApplication())
    }
}
