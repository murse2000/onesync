package com.dalbear.onesync.sync

import com.dalbear.onesync.core.sync.SnapshotEntry
import java.io.File

/**
 * 로컬 폴더를 재귀적으로 스캔해 폴더 쌍 루트 기준 상대 경로 목록을 만든다.
 * v1에서는 숨김 파일(.으로 시작)을 제외한다.
 */
object LocalScanner {

    fun scan(root: File): List<SnapshotEntry> {
        val result = ArrayList<SnapshotEntry>()
        walk(root, "", result)
        return result
    }

    private fun walk(directory: File, prefix: String, out: MutableList<SnapshotEntry>) {
        val children = directory.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".")) continue
            val path = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            if (child.isDirectory) {
                out += SnapshotEntry(
                    path = path,
                    isDirectory = true,
                    size = 0,
                    modifiedMs = child.lastModified(),
                )
                walk(child, path, out)
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
