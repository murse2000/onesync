package com.dalbear.onesync.sync

import android.content.Context
import com.dalbear.onesync.core.sync.FilterOptions
import com.dalbear.onesync.core.sync.SyncFilter

/**
 * 모든 폴더 쌍에 공통으로 적용하는 제외 규칙.
 * 패턴은 쉼표나 줄바꿈으로 구분한다. 예: `*.tmp, .nomedia, node_modules`
 */
class ExcludeStore(context: Context) {

    private val prefs = context.getSharedPreferences("excludes", Context.MODE_PRIVATE)

    var patterns: String
        get() = prefs.getString(KEY_PATTERNS, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_PATTERNS, value).apply()
        }

    /** 0이면 크기 제한 없음. */
    var maxFileSizeMb: Int
        get() = prefs.getInt(KEY_MAX_SIZE_MB, 0)
        set(value) {
            prefs.edit().putInt(KEY_MAX_SIZE_MB, value.coerceAtLeast(0)).apply()
        }

    fun filter(): SyncFilter = SyncFilter(
        FilterOptions(
            excludePatterns = parsePatterns(patterns),
            maxFileSize = maxFileSizeMb.toLong() * 1024L * 1024L,
        ),
    )

    companion object {
        fun parsePatterns(raw: String): List<String> =
            raw.split(',', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        private const val KEY_PATTERNS = "patterns"
        private const val KEY_MAX_SIZE_MB = "max_size_mb"
    }
}
