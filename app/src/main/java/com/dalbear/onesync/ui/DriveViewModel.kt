package com.dalbear.onesync.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalbear.onesync.OneSyncApp
import com.dalbear.onesync.graph.DriveItem
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 로그인된 계정으로 OneDrive 쿼터와 폴더 목록을 읽는다.
 */
class DriveViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OneSyncApp).container
    private val auth = container.auth
    private val store = container.authStore
    private val graph = container.graph

    var quotaText by mutableStateOf<String?>(null)
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
    private val stack = ArrayList<Triple<String?, String, String>>()

    val canGoUp: Boolean get() = stack.isNotEmpty()

    fun refresh() {
        val clientId = store.clientId ?: return
        viewModelScope.launch {
            busy = true
            error = null
            try {
                val account = auth.accounts(clientId).firstOrNull()
                    ?: error("로그인이 필요합니다")
                val accessToken = auth.accessToken(clientId, account).getOrElse {
                    throw IllegalStateException(it.localizedMessage ?: "토큰을 가져오지 못했습니다")
                }
                token = accessToken
                if (quotaText == null) {
                    val drive = graph.drive(accessToken)
                    quotaText = formatQuota(drive.quotaUsed, drive.quotaTotal)
                    quotaFraction = if (drive.quotaTotal > 0) {
                        (drive.quotaUsed.toFloat() / drive.quotaTotal).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
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

    private fun joinPath(prefix: String, name: String): String =
        if (prefix.isEmpty()) name else "$prefix/$name"

    private fun formatQuota(used: Long, total: Long): String {
        if (total <= 0) return "쿼터 정보 없음"
        val percent = (used * 100.0 / total).toInt()
        return "${formatSize(used)} / ${formatSize(total)} 사용 ($percent%)"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
