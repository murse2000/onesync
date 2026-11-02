package com.dalbear.onesync.sync

import com.dalbear.onesync.core.sync.ConflictNames
import com.dalbear.onesync.core.sync.SnapshotEntry
import com.dalbear.onesync.core.sync.SyncAction
import com.dalbear.onesync.core.sync.SyncPlanner
import com.dalbear.onesync.core.sync.SyncState
import com.dalbear.onesync.core.sync.orderedForExecution
import com.dalbear.onesync.data.DeltaStore
import com.dalbear.onesync.data.FolderPair
import com.dalbear.onesync.data.PairRepository
import com.dalbear.onesync.data.UploadSessionStore
import com.dalbear.onesync.graph.GraphClient
import com.dalbear.onesync.graph.DriveItem
import com.dalbear.onesync.graph.GraphException
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 한 폴더 쌍을 실제로 동기화한다.
 *
 * 1. 로컬/원격 스캔
 * 2. 마지막 상태와 비교해 SyncPlanner로 작업 목록 생성
 * 3. 작업 실행 + 상태 DB 갱신
 */
class SyncEngine(
    private val graph: GraphClient,
    private val repo: PairRepository,
    private val deltaStore: DeltaStore,
    private val uploadStore: UploadSessionStore,
    private val excludes: ExcludeStore,
) {

    data class Summary(
        val uploaded: Int = 0,
        val downloaded: Int = 0,
        val deletedLocal: Int = 0,
        val deletedRemote: Int = 0,
        val createdDirectories: Int = 0,
        val conflicts: Int = 0,
        val skipped: Int = 0,
    ) {
        fun toMessage(): String = buildString {
            append("업로드 ${uploaded}건, 다운로드 ${downloaded}건")
            if (createdDirectories > 0) append(", 폴더 생성 ${createdDirectories}건")
            if (deletedLocal + deletedRemote > 0) append(", 삭제 ${deletedLocal + deletedRemote}건")
            if (conflicts > 0) append(", 충돌 ${conflicts}건")
            if (skipped > 0) append(", 건너뜀 ${skipped}건")
        }
    }

    /** 수동 동기화와 백그라운드 작업이 동시에 같은 쌍을 건드리지 않도록 직렬화한다. */
    private val mutex = Mutex()

    suspend fun sync(
        pair: FolderPair,
        token: String,
        onProgress: (String) -> Unit = {},
    ): Summary = mutex.withLock {
        runSync(pair, token, onProgress)
    }

    private suspend fun runSync(
        pair: FolderPair,
        token: String,
        onProgress: (String) -> Unit = {},
    ): Summary {
        val localRoot = File(pair.localRoot)
        require(localRoot.isDirectory) { "로컬 폴더가 없습니다: ${pair.localRoot}" }

        // 제외 규칙은 양쪽 스캔에 똑같이 적용한다. 한쪽만 빼면 삭제로 오판한다.
        val filter = excludes.filter()
        onProgress("로컬 폴더 스캔 중…")
        val localEntries = LocalScanner.scan(localRoot, pair.includeSubdirectories)
            .filterNot { filter.isExcluded(it.path, it.isDirectory, it.size) }
        onProgress("OneDrive 스캔 중…")
        val remoteEntries = scanRemote(token, pair)
            .filterNot { filter.isExcluded(it.path, it.isDirectory, it.size) }

        val previous = repo.loadState(pair.id)
        val actions = SyncPlanner()
            .plan(pair.syncMethod, previous, localEntries, remoteEntries)
            .orderedForExecution()

        onProgress("동기화 작업 ${actions.size}개 실행 중…")

        val localByPath = localEntries.associateBy { it.path }
        val remoteByPath = remoteEntries.associateBy { it.path }
        val remoteDirectoryIds = remoteEntries
            .filter { it.isDirectory }
            .mapNotNull { entry -> entry.remoteId?.let { entry.path to it } }
            .toMap()
            .toMutableMap()

        var summary = Summary()
        for (action in actions) {
            currentCoroutineContext().ensureActive()
            summary = when (action) {
                is SyncAction.CreateLocalDirectory -> {
                    val dir = File(localRoot, action.path)
                    dir.mkdirs()
                    save(pair.id, action.path, localByPath[action.path], remoteByPath[action.path])
                    event(pair.id, "mkdir_local", action.path, null)
                    summary.copy(createdDirectories = summary.createdDirectories + 1)
                }

                is SyncAction.CreateRemoteDirectory -> {
                    val parentId = parentRemoteId(pair, action.path, remoteDirectoryIds)
                    if (parentId == null) {
                        event(pair.id, "error", action.path, "상위 원격 폴더를 찾지 못했습니다")
                        summary.copy(skipped = summary.skipped + 1)
                    } else {
                        val name = action.path.substringAfterLast('/')
                        val created = withRetry { graph.createFolder(token, parentId, name) }
                        remoteDirectoryIds[action.path] = created.id
                        save(
                            pair.id,
                            action.path,
                            localByPath[action.path],
                            remoteEntry(action.path, created),
                        )
                        event(pair.id, "mkdir_remote", action.path, null)
                        summary.copy(createdDirectories = summary.createdDirectories + 1)
                    }
                }

                is SyncAction.Upload -> {
                    val file = File(localRoot, action.path)
                    val parentId = parentRemoteId(pair, action.path, remoteDirectoryIds)
                    when {
                        !file.isFile -> {
                            event(pair.id, "error", action.path, "로컬 파일이 없습니다")
                            summary.copy(skipped = summary.skipped + 1)
                        }

                        parentId == null -> {
                            event(pair.id, "error", action.path, "상위 원격 폴더를 찾지 못했습니다")
                            summary.copy(skipped = summary.skipped + 1)
                        }

                        else -> {
                            val uploaded = withRetry {
                                uploadFile(token, pair, action.path, file, parentId, onProgress)
                            }
                            save(pair.id, action.path, localByPath[action.path], remoteEntry(action.path, uploaded))
                            event(pair.id, "upload", action.path, null)
                            summary.copy(uploaded = summary.uploaded + 1)
                        }
                    }
                }

                is SyncAction.Download -> {
                    val remote = remoteByPath[action.path]
                    if (remote?.remoteId == null) {
                        event(pair.id, "error", action.path, "원격 항목 ID가 없습니다")
                        summary.copy(skipped = summary.skipped + 1)
                    } else {
                        onProgress("다운로드: ${action.path}")
                        val target = File(localRoot, action.path)
                        target.parentFile?.mkdirs()
                        withRetry {
                            graph.download(token, remote.remoteId, target, remote.size)
                        }
                        if (remote.modifiedMs > 0) target.setLastModified(remote.modifiedMs)
                        save(pair.id, action.path, entryOf(localRoot, action.path), remote)
                        event(pair.id, "download", action.path, null)
                        summary.copy(downloaded = summary.downloaded + 1)
                    }
                }

                is SyncAction.DeleteLocal -> {
                    val target = File(localRoot, action.path)
                    if (target.deleteRecursively()) {
                        repo.deleteState(pair.id, action.path)
                        event(pair.id, "delete_local", action.path, null)
                        summary.copy(deletedLocal = summary.deletedLocal + 1)
                    } else {
                        event(pair.id, "error", action.path, "로컬 삭제 실패")
                        summary.copy(skipped = summary.skipped + 1)
                    }
                }

                is SyncAction.DeleteRemote -> {
                    val remoteId = remoteByPath[action.path]?.remoteId
                        ?: previous.firstOrNull { it.path == action.path }?.remote?.remoteId
                    if (remoteId == null) {
                        repo.deleteState(pair.id, action.path)
                        summary.copy(skipped = summary.skipped + 1)
                    } else {
                        withRetry { graph.deleteItem(token, remoteId) }
                        repo.deleteState(pair.id, action.path)
                        event(pair.id, "delete_remote", action.path, null)
                        summary.copy(deletedRemote = summary.deletedRemote + 1)
                    }
                }

                is SyncAction.Conflict -> {
                    val localFile = File(localRoot, action.path)
                    val remote = remoteByPath[action.path]
                    if (localFile.isFile && remote?.remoteId != null) {
                        val conflictName = ConflictNames.forFile(localFile.name, System.currentTimeMillis())
                        val conflictFile = File(localFile.parentFile, conflictName)
                        if (!localFile.renameTo(conflictFile)) {
                            event(pair.id, "conflict", action.path, "충돌 사본 생성 실패")
                            summary.copy(skipped = summary.skipped + 1)
                        } else {
                            // 원격 버전을 원래 경로로 내려받아 양쪽을 모두 보존한다.
                            withRetry {
                                graph.download(token, remote.remoteId, localFile, remote.size)
                            }
                            save(pair.id, action.path, entryOf(localRoot, action.path), remote)

                            // 이름을 바꾼 로컬 사본은 새 파일로 올린다.
                            val conflictPath = if (action.path.contains('/')) {
                                action.path.substringBeforeLast('/') + "/" + conflictName
                            } else {
                                conflictName
                            }
                            val parentId = parentRemoteId(pair, conflictPath, remoteDirectoryIds)
                            if (parentId != null) {
                                val uploaded = uploadFile(
                                    token, pair, conflictPath, conflictFile, parentId, onProgress,
                                )
                                save(
                                    pair.id,
                                    conflictPath,
                                    entryOf(localRoot, conflictPath),
                                    remoteEntry(conflictPath, uploaded),
                                )
                                repo.addConflict(pair.id, action.path, conflictPath, uploaded.id)
                            }
                            event(pair.id, "conflict", action.path, "로컬 사본: $conflictName")
                            summary.copy(conflicts = summary.conflicts + 1)
                        }
                    } else {
                        event(pair.id, "conflict", action.path, "파일 충돌만 자동 해결합니다")
                        summary.copy(skipped = summary.skipped + 1)
                    }
                }

                is SyncAction.Link -> {
                    save(pair.id, action.path, localByPath[action.path], remoteByPath[action.path])
                    action.alignLocalModifiedMs?.let { mtime ->
                        File(localRoot, action.path).setLastModified(mtime)
                    }
                    summary
                }

                is SyncAction.Ignore -> {
                    event(pair.id, "skipped", action.path, action.reason)
                    summary.copy(skipped = summary.skipped + 1)
                }

                is SyncAction.RemoveState -> {
                    repo.deleteState(pair.id, action.path)
                    summary
                }
            }
        }

        event(pair.id, "sync", null, summary.toMessage())
        return summary
    }

    /**
     * delta 캐시를 먼저 갱신하고 캐시에서 목록을 만든다.
     * delta가 실패하면(권한/엔드포인트 문제 등) 기존 재귀 스캔으로 폴백한다.
     */
    private suspend fun scanRemote(token: String, pair: FolderPair): List<SnapshotEntry> {
        return try {
            deltaStore.refresh(token, pair)
            deltaStore.entries(pair.id, pair.remoteFolderId, pair.includeSubdirectories)
        } catch (e: Exception) {
            val fallback = ArrayList<SnapshotEntry>()
            walkRemote(token, pair.remoteFolderId, "", pair.includeSubdirectories, fallback)
            fallback
        }
    }

    private suspend fun walkRemote(
        token: String,
        folderId: String,
        prefix: String,
        includeSubdirectories: Boolean,
        out: MutableList<SnapshotEntry>,
    ) {
        for (item in graph.entries(token, folderId)) {
            val path = if (prefix.isEmpty()) item.name else "$prefix/${item.name}"
            if (item.isFolder) {
                if (!includeSubdirectories) continue
                out += remoteEntry(path, item)
                walkRemote(token, item.id, path, includeSubdirectories, out)
            } else {
                out += remoteEntry(path, item)
            }
        }
    }

    private suspend fun uploadFile(
        token: String,
        pair: FolderPair,
        path: String,
        file: File,
        parentId: String,
        onProgress: (String) -> Unit,
    ): DriveItem {
        if (file.length() <= MAX_SIMPLE_UPLOAD) {
            onProgress("업로드: $path")
            return graph.uploadSmall(token, parentId, file.name, file.readBytes(), mimeTypeOf(file.name))
        }

        val existing = uploadStore.find(pair.id, path)
            ?.takeIf { it.fileSize == file.length() && it.fileModified == file.lastModified() }

        onProgress(
            "업로드: $path" + if (existing != null) " (이어서 ${existing.offset}B)" else "",
        )
        val uploaded = graph.uploadLarge(
            token = token,
            parentId = parentId,
            name = file.name,
            file = file,
            existingUrl = existing?.url,
            existingOffset = existing?.offset ?: 0,
            onOffset = { url, offset ->
                uploadStore.save(pair.id, path, url, offset, file.length(), file.lastModified())
            },
            onProgress = { percent -> onProgress("업로드: $path ($percent%)") },
        )
        uploadStore.clear(pair.id, path)
        return uploaded
    }

    private fun remoteEntry(path: String, item: DriveItem): SnapshotEntry = SnapshotEntry(
        path = path,
        isDirectory = item.isFolder,
        size = item.size,
        modifiedMs = parseTime(item.modified),
        hash = item.quickXorHash,
        remoteId = item.id,
    )

    private fun parentRemoteId(
        pair: FolderPair,
        path: String,
        directoryIds: Map<String, String>,
    ): String? {
        if (!path.contains('/')) return pair.remoteFolderId
        return directoryIds[path.substringBeforeLast('/')]
    }

    private fun save(pairId: Long, path: String, local: SnapshotEntry?, remote: SnapshotEntry?) {
        repo.upsertState(pairId, SyncState(path, local, remote))
    }

    private fun entryOf(root: File, path: String): SnapshotEntry {
        val file = File(root, path)
        return SnapshotEntry(
            path = path,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0,
            modifiedMs = file.lastModified(),
        )
    }

    private fun event(pairId: Long, type: String, path: String?, message: String?) {
        repo.addEvent(pairId, type, path, message)
    }

    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(0)
    }

    /** 일시적 오류(네트워크, 5xx, 429)만 지수적으로 재시도한다. */
    private suspend fun <T> withRetry(attempts: Int = 3, block: suspend () -> T): T {
        var last: Exception? = null
        for (attempt in 0 until attempts) {
            try {
                return block()
            } catch (e: GraphException) {
                if (e.status in 400..499 && e.status != 429) throw e
                last = e
            } catch (e: IOException) {
                last = e
            }
            if (attempt < attempts - 1) delay(1_000L * (attempt + 1))
        }
        throw last ?: IllegalStateException("retry failed")
    }

    private fun mimeTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "txt" -> "text/plain"
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        else -> "application/octet-stream"
    }

    private companion object {
        const val MAX_SIMPLE_UPLOAD = 4L * 1024 * 1024
    }
}
