package com.dalbear.onesync.ui

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalbear.onesync.OneSyncApp
import com.dalbear.onesync.auth.AccountInfo
import com.dalbear.onesync.auth.AuthConfig
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OneSyncApp).container
    private val auth = container.auth
    private val sessions = container.sessions
    private val store = container.authStore

    var clientId by mutableStateOf(store.clientId)
        private set

    /** 로그인된 계정 전부. 첫 번째를 홈 카드에 대표로 보여준다. */
    var accounts by mutableStateOf<List<AccountInfo>>(emptyList())
        private set

    val accountEmail: String? get() = accounts.firstOrNull()?.username

    var busy by mutableStateOf(false)
        private set

    var status by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    /** GUID 형식이면 저장하고 true. 형식이 틀리면 상태 메시지만 남긴다. */
    fun saveClientId(raw: String): Boolean {
        val id = raw.trim()
        if (!AuthConfig.isValidClientId(id)) {
            status = "클라이언트 ID는 GUID 형식이어야 합니다. 예: 00000000-0000-0000-0000-000000000000"
            return false
        }
        if (id != clientId) {
            auth.reset()
            accounts = emptyList()
        }
        store.clientId = id
        clientId = id
        status = null
        refresh()
        return true
    }

    fun refresh() {
        val id = clientId ?: return
        viewModelScope.launch {
            busy = true
            val created = auth.client(id)
            if (created.isFailure) {
                accounts = emptyList()
                status = created.exceptionOrNull()?.message ?: "MSAL 초기화에 실패했습니다."
            } else {
                accounts = sessions.list()
            }
            busy = false
        }
    }

    /** [selectAccount]가 true면 계정 선택 화면을 띄워 새 계정을 추가한다. */
    fun signIn(activity: Activity, selectAccount: Boolean = false) {
        val id = clientId ?: return
        busy = true
        status = null
        viewModelScope.launch {
            val created = auth.client(id)
            if (created.isFailure) {
                busy = false
                status = created.exceptionOrNull()?.message ?: "MSAL 초기화에 실패했습니다."
                return@launch
            }
            // acquireToken은 Activity가 살아 있는 메인 스레드에서 호출한다.
            auth.signIn(activity, selectAccount) { result ->
                busy = false
                result.onSuccess {
                    status = null
                    refresh()
                }
                result.onFailure {
                    status = it.message ?: "로그인에 실패했습니다."
                }
            }
        }
    }

    /** 계정 하나만 로그아웃한다. 그 계정의 폴더 쌍은 동기화되지 않는다. */
    fun signOut(accountId: String) {
        busy = true
        viewModelScope.launch {
            val result = sessions.signOut(accountId)
            accounts = sessions.list()
            status = result.exceptionOrNull()?.message
            busy = false
        }
    }
}
