package com.dalbear.onesync.monitor

import android.os.FileObserver
import java.io.File

/**
 * 디렉터리 트리를 감시한다. FileObserver는 재귀가 아니므로 폴더마다 하나씩 만든다.
 * 감시 개수는 성능 보호를 위해 제한한다.
 */
class RecursiveFileObserver(
    private val root: File,
    private val recursive: Boolean,
    private val onEvent: (isDirectory: Boolean) -> Unit,
) {

    private val observers = ArrayList<FileObserver>()

    fun start() {
        val dirs = ArrayList<File>()
        collectDirectories(root, dirs)
        for (dir in dirs.take(MAX_WATCHED_DIRECTORIES)) {
            val observer = object : FileObserver(dir.absolutePath, MASK) {
                override fun onEvent(event: Int, path: String?) {
                    // FileObserver.ISDIR는 SDK에서 숨겨져 있어 상수값을 직접 쓴다.
                    val isDirectory = event and IS_DIR != 0
                    onEvent(isDirectory)
                }
            }
            runCatching {
                observer.startWatching()
                observers += observer
            }
        }
    }

    fun stop() {
        observers.forEach { runCatching { it.stopWatching() } }
        observers.clear()
    }

    private fun collectDirectories(directory: File, out: MutableList<File>) {
        out += directory
        if (!recursive) return
        val children = directory.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory && !child.name.startsWith(".")) {
                collectDirectories(child, out)
            }
        }
    }

    private companion object {
        const val MAX_WATCHED_DIRECTORIES = 400
        const val IS_DIR = 0x40000000

        const val MASK = FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MODIFY or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE
    }
}
