package com.dalbear.onesync.ui

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalbear.onesync.OneSyncApp
import com.dalbear.onesync.auth.AuthConfig
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OneSyncApp).container
    private val auth = container.auth
    private val store = container.authStore

    var clientId by mutableStateOf(store.clientId)
        private set

    var accountEmail by mutableStateOf<String?>(null)
        private set

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
            accountEmail = null
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
                accountEmail = null
                status = created.exceptionOrNull()?.message ?: "MSAL 초기화에 실패했습니다."
            } else {
                accountEmail = auth.accounts(id).firstOrNull()?.username
            }
            busy = false
        }
    }

    fun signIn(activity: Activity) {
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
            auth.signIn(activity) { result ->
                busy = false
                result.onSuccess {
                    accountEmail = it.account.username
                    status = null
                }
                result.onFailure {
                    status = it.message ?: "로그인에 실패했습니다."
                }
            }
        }
    }

    fun signOut() {
        val id = clientId ?: return
        busy = true
        viewModelScope.launch {
            val result = auth.signOut(id)
            accountEmail = null
            status = result.exceptionOrNull()?.message
            busy = false
        }
    }
}
