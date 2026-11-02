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
import com.dalbear.onesync.data.SyncEventRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OneSyncApp).container
    private val repo = container.repo
    private val engine = container.engine
    private val auth = container.auth
    private val store = container.authStore

    var pairs by mutableStateOf<List<FolderPair>>(emptyList())
        private set

    var busy by mutableStateOf(false)
        private set

    var status by mutableStateOf<String?>(null)
        private set

    private val _progress = MutableStateFlow<String?>(null)
    val progress = _progress.asStateFlow()

    // 새 폴더 쌍 화면에서 임시로 들고 있는 선택값
    var pendingRemoteId: String? = null
    var pendingRemotePath: String? = null
    var pendingLocalPath: String? = null

    fun loadPairs() {
        viewModelScope.launch {
            pairs = withContext(Dispatchers.IO) { repo.list() }
        }
    }

    fun createPair(name: String, method: SyncMethod): Boolean {
        val remoteId = pendingRemoteId ?: return false
        val remotePath = pendingRemotePath ?: return false
        val localPath = pendingLocalPath ?: return false
        val trimmed = name.trim().ifEmpty { "OneDrive 동기화" }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.create(trimmed, localPath, remoteId, remotePath, method)
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
    }

    fun deletePair(pair: FolderPair) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(pair.id) }
            loadPairs()
        }
    }

    fun updateMethod(pair: FolderPair, method: SyncMethod) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.updateMethod(pair.id, method) }
            loadPairs()
        }
    }

    fun events(pairId: Long): List<SyncEventRow> = repo.recentEvents(pairId, 10)

    fun sync(pair: FolderPair) {
        if (busy) return
        val clientId = store.clientId ?: return
        viewModelScope.launch {
            busy = true
            status = null
            _progress.value = "준비 중…"
            try {
                val account = auth.accounts(clientId).firstOrNull()
                    ?: throw IllegalStateException("로그인이 필요합니다")
                val token = auth.accessToken(clientId, account).getOrElse {
                    throw IllegalStateException(it.localizedMessage ?: "토큰을 가져오지 못했습니다")
                }
                val summary = withContext(Dispatchers.IO) {
                    engine.sync(pair, token) { message -> _progress.value = message }
                }
                status = "동기화 완료: ${summary.toMessage()}"
            } catch (e: Exception) {
                status = "동기화 실패: ${e.localizedMessage ?: e.javaClass.simpleName}"
            } finally {
                _progress.value = null
                busy = false
                loadPairs()
            }
        }
    }
}
