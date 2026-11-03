package com.dalbear.onesync.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalbear.onesync.OneSyncApp
import com.dalbear.onesync.auth.AccountInfo
import com.dalbear.onesync.graph.DriveItem
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 계정별 OneDrive 쿼터와 폴더 목록을 읽는다.
 * 폴더를 고르는 동안에는 [browsingAccountId] 계정의 드라이브만 본다.
 */
class DriveViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OneSyncApp).container
    private val sessions = container.sessions
    private val graph = container.graph

    var accounts by mutableStateOf<List<AccountInfo>>(emptyList())
        private set

    /** 폴더를 고를 때 쓰는 계정 ID. null이면 첫 계정. */
    var browsingAccountId by mutableStateOf<String?>(null)
        private set

    val browsingAccount: AccountInfo?
        get() = accounts.firstOrNull { it.id == browsingAccountId } ?: accounts.firstOrNull()

    var quotaText by mutableStateOf<String?>(null)
        private set

    /** 카드 오른쪽에 크게 보여줄 사용 비율. */
    var quotaPercent by mutableStateOf<Int?>(null)
        private set

    /** "95.2 / 100 GB"처럼 짧게 쓴 사용량/전체. */
    var quotaShortText by mutableStateOf<String?>(null)
        private set

    var quotaFraction by mutableStateOf(0f)
        private set

    var items by mutableStateOf<List<DriveItem>>(emptyList())
        private set

    var currentFolderId by mutableStateOf<String?>(null)
        private set

    var currentFolderName by mutableStateOf("OneDrive")
        private set

    /** 드라이브 루트 기준 상대 경로. 루트는 빈 문자열. */
    var currentFolderPath by mutableStateOf("")
        private set

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private var token: String? = null
    private var browseJob: Job? = null
    private var quotaJob: Job? = null
    private val stack = ArrayList<Triple<String?, String, String>>()

    val canGoUp: Boolean get() = stack.isNotEmpty()

    /** 로그인된 계정 목록을 새로 읽는다. 폴더를 고르는 계정이 사라졌으면 첫 계정으로 되돌린다. */
    fun loadAccounts() {
        viewModelScope.launch {
            accounts = sessions.list()
            if (accounts.none { it.id == browsingAccountId }) {
                browsingAccountId = accounts.firstOrNull()?.id
            }
        }
    }

    /** 폴더 선택 계정을 바꾸고 그 계정의 루트부터 다시 읽는다. */
    fun selectAccount(accountId: String?) {
        if (accountId == browsingAccountId) return
        browsingAccountId = accountId
        resetBrowser()
        refresh()
    }

    /** 홈 카드에 보여줄 첫 계정의 용량. */
    fun refreshQuota() {
        quotaJob?.cancel()
        quotaJob = viewModelScope.launch {
            quotaText = null
            quotaPercent = null
            quotaShortText = null
            quotaFraction = 0f
            try {
                val account = accounts.firstOrNull() ?: sessions.list().firstOrNull() ?: return@launch
                val accessToken = sessions.tokenFor(account.id).getOrElse {
                    throw IllegalStateException(it.localizedMessage ?: "토큰을 가져오지 못했습니다")
                }
                val drive = graph.drive(accessToken)
                quotaText = formatQuota(drive.quotaUsed, drive.quotaTotal)
                quotaPercent = if (drive.quotaTotal > 0) {
                    (drive.quotaUsed * 100.0 / drive.quotaTotal).toInt()
                } else {
                    null
                }
                quotaShortText = if (drive.quotaTotal > 0) {
                    "${shortSize(drive.quotaUsed)} / ${shortSize(drive.quotaTotal)}"
                } else {
                    null
                }
                quotaFraction = if (drive.quotaTotal > 0) {
                    (drive.quotaUsed.toFloat() / drive.quotaTotal).coerceIn(0f, 1f)
                } else {
                    0f
                }
            } catch (_: Exception) {
                // 홈 카드는 "용량 정보를 불러오는 중…"으로 남고, 화면 복귀 시 다시 시도한다.
            }
        }
    }

    fun refresh() {
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            busy = true
            error = null
            try {
                if (accounts.isEmpty()) accounts = sessions.list()
                if (accounts.none { it.id == browsingAccountId }) {
                    browsingAccountId = accounts.firstOrNull()?.id
                    resetBrowser()
                }
                val accessToken = sessions.tokenFor(browsingAccountId).getOrElse {
                    throw IllegalStateException(it.localizedMessage ?: "토큰을 가져오지 못했습니다")
                }
                token = accessToken
                items = graph.folders(accessToken, currentFolderId)
            } catch (e: Exception) {
                error = e.localizedMessage ?: "OneDrive를 읽지 못했습니다"
            } finally {
                busy = false
            }
        }
    }

    fun open(item: DriveItem) {
        stack.add(Triple(currentFolderId, currentFolderName, currentFolderPath))
        currentFolderId = item.id
        currentFolderName = item.name
        currentFolderPath = joinPath(currentFolderPath, item.name)
        items = emptyList()
        refresh()
    }

    fun up() {
        if (stack.isEmpty()) return
        val (id, name, path) = stack.removeAt(stack.lastIndex)
        currentFolderId = id
        currentFolderName = name
        currentFolderPath = path
        items = emptyList()
        refresh()
    }

    /** 현재 폴더 아래에 새 폴더를 만든다. 원격 폴더를 하나도 만들 수 없을 때 유용하다. */
    fun createFolder(name: String) {
        val trimmed = name.trim()
        val accessToken = token ?: return
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            busy = true
            error = null
            try {
                graph.createFolder(accessToken, currentFolderId ?: "root", trimmed)
                items = graph.folders(accessToken, currentFolderId)
            } catch (e: Exception) {
                error = e.localizedMessage ?: "폴더를 만들지 못했습니다"
            } finally {
                busy = false
            }
        }
    }

    private fun resetBrowser() {
        stack.clear()
        currentFolderId = null
        currentFolderName = "OneDrive"
        currentFolderPath = ""
        items = emptyList()
        token = null
    }

    private fun joinPath(prefix: String, name: String): String =
        if (prefix.isEmpty()) name else "$prefix/$name"

    private fun formatQuota(used: Long, total: Long): String {
        if (total <= 0) return "쿼터 정보 없음"
        val percent = (used * 100.0 / total).toInt()
        return "${formatSize(used)} / ${formatSize(total)} 사용 ($percent%)"
    }

    /** 카드 한 줄에 넣는 짧은 표기. 소수 한 자리까지만. */
    private fun shortSize(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024))
        bytes < 1024L * 1024 * 1024 * 1024 ->
            String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        else -> String.format(Locale.US, "%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024))
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
