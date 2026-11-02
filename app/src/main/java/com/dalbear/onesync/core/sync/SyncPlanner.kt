package com.dalbear.onesync.core.sync

/**
 * 마지막 동기화 상태(DB)와 현재 스캔 결과를 비교해 할 일 목록을 만든다.
 *
 * 파일 시스템에도, 안드로이드 API에도 의존하지 않는 순수 함수라 JVM 테스트로 계약을 고정한다.
 */
class SyncPlanner {

    fun plan(
        method: SyncMethod,
        previous: Collection<SyncState> = emptyList(),
        local: Collection<SnapshotEntry> = emptyList(),
        remote: Collection<SnapshotEntry> = emptyList(),
    ): List<SyncAction> {
        val previousByKey = previous.associateBy { PathKey.of(it.path) }
        val localByKey = index(local)
        val remoteByKey = index(remote)

        val keys = sortedSetOf<String>()
        keys += localByKey.keys
        keys += remoteByKey.keys
        keys += previousByKey.keys

        val actions = ArrayList<SyncAction>()
        for (key in keys) {
            val state = previousByKey[key]
            val localEntry = localByKey[key]
            val remoteEntry = remoteByKey[key]
            val path = localEntry?.path ?: remoteEntry?.path ?: state?.path ?: continue
            actions += planPath(method, path, state, localEntry, remoteEntry)
        }
        return actions
    }

    private fun planPath(
        method: SyncMethod,
        path: String,
        state: SyncState?,
        local: SnapshotEntry?,
        remote: SnapshotEntry?,
    ): SyncAction {
        val localChange = change(state?.local, local, isRemote = false)
        val remoteChange = change(state?.remote, remote, isRemote = true)

        if (local != null && remote != null && local.isDirectory != remote.isDirectory) {
            return SyncAction.Conflict(path)
        }
        if (local?.isDirectory == true || remote?.isDirectory == true) {
            return planDirectory(method, path, local, remote, localChange, remoteChange)
        }
        return planFile(method, path, local, remote, localChange, remoteChange)
    }

    private fun planDirectory(
        method: SyncMethod,
        path: String,
        local: SnapshotEntry?,
        remote: SnapshotEntry?,
        localChange: Change,
        remoteChange: Change,
    ): SyncAction {
        if (local != null && remote != null) return SyncAction.Link(path)

        // 로컬에만 있다: 원격이 지워졌거나(rc=DELETED) 애초에 없었다(rc=NONE).
        if (local != null) {
            return when {
                remoteChange == Change.DELETED && method.propagatesRemoteDeletion ->
                    SyncAction.DeleteLocal(path, isDirectory = true)

                remoteChange == Change.DELETED && !method.mirrorsLocalToRemote ->
                    SyncAction.Ignore(path, "원격 폴더 삭제를 무시하는 모드")

                method.canUpload ->
                    SyncAction.CreateRemoteDirectory(path)

                method.mirrorsRemoteToLocal ->
                    SyncAction.DeleteLocal(path, isDirectory = true)

                else ->
                    SyncAction.Ignore(path, "업로드하지 않는 모드")
            }
        }

        // 원격에만 있다.
        return when {
            localChange == Change.DELETED && method.propagatesLocalDeletion ->
                SyncAction.DeleteRemote(path)

            localChange == Change.DELETED && method.mirrorsRemoteToLocal ->
                SyncAction.CreateLocalDirectory(path)

            localChange == Change.DELETED ->
                SyncAction.Ignore(path, "로컬 폴더 삭제를 전파하지 않는 모드")

            method.mirrorsLocalToRemote ->
                SyncAction.DeleteRemote(path)

            method.canDownload ->
                SyncAction.CreateLocalDirectory(path)

            else ->
                SyncAction.Ignore(path, "다운로드하지 않는 모드")
        }
    }

    private fun planFile(
        method: SyncMethod,
        path: String,
        local: SnapshotEntry?,
        remote: SnapshotEntry?,
        localChange: Change,
        remoteChange: Change,
    ): SyncAction {
        if (local == null && remote == null) return SyncAction.RemoveState(path)

        // 양쪽에 모두 있는 경우: 삭제는 여기 올 수 없다(현재 항목이 있다는 뜻이므로).
        if (local != null && remote != null) {
            return when {
                localChange.isModifiedOrNew && remoteChange.isModifiedOrNew ->
                    if (sameContent(local, remote)) link(method, local, remote)
                    else SyncAction.Conflict(path)

                // 한쪽만 새 항목: 같은 내용이면 기존 파일로 링크, 아니면 둘 다 사용자 데이터라 충돌로 둔다.
                localChange == Change.NEW && remoteChange == Change.NONE ->
                    when {
                        method.mirrorsRemoteToLocal -> SyncAction.Download(path, method.deleteRemoteAfterDownload)
                        sameContent(local, remote) -> link(method, local, remote)
                        else -> SyncAction.Conflict(path)
                    }

                remoteChange == Change.NEW && localChange == Change.NONE ->
                    when {
                        method.mirrorsLocalToRemote ->
                            SyncAction.Upload(path, remote.remoteId, method.deleteLocalAfterUpload)
                        sameContent(local, remote) -> link(method, local, remote)
                        else -> SyncAction.Conflict(path)
                    }

                localChange.isModifiedOrNew && remoteChange == Change.NONE ->
                    when {
                        method.canUpload ->
                            SyncAction.Upload(path, remote.remoteId, method.deleteLocalAfterUpload)
                        method.mirrorsRemoteToLocal ->
                            SyncAction.Download(path, method.deleteRemoteAfterDownload)
                        else ->
                            SyncAction.Ignore(path, "업로드하지 않는 모드")
                    }

                localChange == Change.NONE && remoteChange.isModifiedOrNew ->
                    when {
                        method.canDownload ->
                            SyncAction.Download(path, method.deleteRemoteAfterDownload)
                        method.mirrorsLocalToRemote ->
                            SyncAction.Upload(path, remote.remoteId, method.deleteLocalAfterUpload)
                        else ->
                            SyncAction.Ignore(path, "다운로드하지 않는 모드")
                    }

                else -> link(method, local, remote)
            }
        }

        // 로컬에만 있다.
        if (local != null) {
            return when {
                remoteChange == Change.DELETED && method.propagatesRemoteDeletion ->
                    SyncAction.DeleteLocal(path)

                remoteChange == Change.DELETED && !method.mirrorsLocalToRemote ->
                    SyncAction.Ignore(path, "원격 삭제를 무시하는 모드")

                method.canUpload ->
                    SyncAction.Upload(path, null, method.deleteLocalAfterUpload)

                method.mirrorsRemoteToLocal ->
                    SyncAction.DeleteLocal(path)

                else ->
                    SyncAction.Ignore(path, "업로드하지 않는 모드")
            }
        }

        // 원격에만 있다.
        return when {
            localChange == Change.DELETED && method.propagatesLocalDeletion ->
                SyncAction.DeleteRemote(path, remote?.remoteId)

            localChange == Change.DELETED && method.mirrorsRemoteToLocal ->
                SyncAction.Download(path, method.deleteRemoteAfterDownload)

            localChange == Change.DELETED ->
                SyncAction.Ignore(path, "로컬 삭제를 전파하지 않는 모드")

            method.mirrorsLocalToRemote ->
                SyncAction.DeleteRemote(path, remote?.remoteId)

            method.canDownload ->
                SyncAction.Download(path, method.deleteRemoteAfterDownload)

            else ->
                SyncAction.Ignore(path, "다운로드하지 않는 모드")
        }
    }

    private fun link(
        method: SyncMethod,
        local: SnapshotEntry,
        remote: SnapshotEntry,
    ): SyncAction.Link {
        // 업로드 가능한 모드에서 원격이 더 오래됐다면 로컬 mtime을 맞춘다.
        // 다음 스캔에서 같은 파일을 또 변경으로 보지 않게 하기 위함이다.
        val align = if (method.canUpload && remote.modifiedMs > 0 && local.modifiedMs > remote.modifiedMs) {
            remote.modifiedMs
        } else {
            null
        }
        return SyncAction.Link(local.path, align)
    }

    private fun sameContent(local: SnapshotEntry?, remote: SnapshotEntry?): Boolean {
        if (local == null || remote == null) return false
        if (local.isDirectory != remote.isDirectory) return false
        if (local.isDirectory) return true
        if (local.size != remote.size) return false
        if (local.hash != null && remote.hash != null) {
            return local.hash.equals(remote.hash, ignoreCase = true)
        }
        // 해시를 아직 모르면 크기만으로 같은 파일로 본다(원본 앱의 링크 규칙과 같다).
        return true
    }

    private fun change(previous: SnapshotEntry?, current: SnapshotEntry?, isRemote: Boolean): Change {
        if (previous == null) return if (current == null) Change.NONE else Change.NEW
        if (current == null) return Change.DELETED
        if (previous.isDirectory != current.isDirectory) return Change.MODIFIED
        if (current.isDirectory) return Change.NONE
        if (previous.size != current.size) return Change.MODIFIED

        if (isRemote && previous.hash != null && current.hash != null) {
            // OneDrive 해시가 양쪽에 있으면 해시가 진실이다.
            return if (previous.hash.equals(current.hash, ignoreCase = true)) Change.NONE else Change.MODIFIED
        }
        if (previous.modifiedMs != current.modifiedMs) return Change.MODIFIED
        if (previous.hash != null && current.hash != null &&
            !previous.hash.equals(current.hash, ignoreCase = true)
        ) {
            return Change.MODIFIED
        }
        return Change.NONE
    }

    private fun index(entries: Collection<SnapshotEntry>): Map<String, SnapshotEntry> {
        val map = LinkedHashMap<String, SnapshotEntry>(entries.size)
        for (entry in entries) map.putIfAbsent(PathKey.of(entry.path), entry)
        return map
    }

    private val Change.isModifiedOrNew: Boolean
        get() = this == Change.NEW || this == Change.MODIFIED
}
