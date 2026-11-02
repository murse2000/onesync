package com.dalbear.onesync.sync

import com.dalbear.onesync.core.sync.SnapshotEntry
import java.io.File

/**
 * 로컬 폴더를 스캔해 폴더 쌍 루트 기준 상대 경로 목록을 만든다.
 * v1부터 숨김 파일(.으로 시작)은 제외한다.
 *
 * [includeSubdirectories]가 false면 최상위 파일만 대상으로 하고 하위 폴더는 통째로 무시한다.
 */
object LocalScanner {

    fun scan(root: File, includeSubdirectories: Boolean = true): List<SnapshotEntry> {
        val result = ArrayList<SnapshotEntry>()
        walk(root, "", includeSubdirectories, result)
        return result
    }

    private fun walk(
        directory: File,
        prefix: String,
        includeSubdirectories: Boolean,
        out: MutableList<SnapshotEntry>,
    ) {
        val children = directory.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".")) continue
            val path = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            if (child.isDirectory) {
                if (!includeSubdirectories) continue
                out += SnapshotEntry(
                    path = path,
                    isDirectory = true,
                    size = 0,
                    modifiedMs = child.lastModified(),
                )
                walk(child, path, includeSubdirectories, out)
            } else if (child.isFile) {
                out += SnapshotEntry(
                    path = path,
                    isDirectory = false,
                    size = child.length(),
                    modifiedMs = child.lastModified(),
                )
            }
        }
    }
}
