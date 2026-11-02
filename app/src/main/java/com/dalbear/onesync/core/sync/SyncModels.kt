package com.dalbear.onesync.core.sync

/**
 * 폴더 쌍의 동기화 방식.
 *
 * 이름과 기본 의미는 원본 앱(OneSync)의 7가지 모드를 그대로 따른다.
 * 다만 "삭제를 어느 쪽이 진실로 볼 것인가"는 이 클론에서 아래처럼 계약을 고정했다.
 * - UPLOAD_ONLY / UPLOAD_THEN_DELETE : 로컬 변경만 올린다. 삭제는 양쪽 모두 전파하지 않는다.
 * - UPLOAD_MIRROR  : 로컬이 진실. 원격에만 있는 항목은 지우고, 원격에서 지워진 항목은 다시 올린다.
 * - DOWNLOAD_ONLY / DOWNLOAD_THEN_DELETE : 원격 변경만 내려받는다. 삭제는 양쪽 모두 전파하지 않는다.
 * - DOWNLOAD_MIRROR: 원격이 진실. 로컬에만 있는 항목은 지우고, 로컬에서 지워진 항목은 다시 받는다.
 * - TWO_WAY        : 변경과 삭제를 양방향으로 전파한다.
 */
enum class SyncMethod(
    val canUpload: Boolean,
    val canDownload: Boolean,
    val deleteLocalAfterUpload: Boolean,
    val deleteRemoteAfterDownload: Boolean,
) {
    TWO_WAY(canUpload = true, canDownload = true, deleteLocalAfterUpload = false, deleteRemoteAfterDownload = false),
    UPLOAD_ONLY(canUpload = true, canDownload = false, deleteLocalAfterUpload = false, deleteRemoteAfterDownload = false),
    UPLOAD_THEN_DELETE(canUpload = true, canDownload = false, deleteLocalAfterUpload = true, deleteRemoteAfterDownload = false),
    UPLOAD_MIRROR(canUpload = true, canDownload = false, deleteLocalAfterUpload = false, deleteRemoteAfterDownload = false),
    DOWNLOAD_ONLY(canUpload = false, canDownload = true, deleteLocalAfterUpload = false, deleteRemoteAfterDownload = false),
    DOWNLOAD_THEN_DELETE(canUpload = false, canDownload = true, deleteLocalAfterUpload = false, deleteRemoteAfterDownload = true),
    DOWNLOAD_MIRROR(canUpload = false, canDownload = true, deleteLocalAfterUpload = false, deleteRemoteAfterDownload = false);

    /** 로컬에서 지운 파일을 원격에서도 지운다. */
    val propagatesLocalDeletion: Boolean
        get() = this == TWO_WAY || this == UPLOAD_MIRROR

    /** 원격에서 지운 파일을 로컬에서도 지운다. */
    val propagatesRemoteDeletion: Boolean
        get() = this == TWO_WAY || this == DOWNLOAD_MIRROR

    /** 로컬에 없는 원격 항목을 지운다. */
    val mirrorsLocalToRemote: Boolean
        get() = this == UPLOAD_MIRROR

    /** 원격에 없는 로컬 항목을 지운다. */
    val mirrorsRemoteToLocal: Boolean
        get() = this == DOWNLOAD_MIRROR
}

/** 폴더 쌍 루트를 기준으로 한 상대 경로('/' 구분)를 가진 한쪽 스냅샷. */
data class SnapshotEntry(
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val modifiedMs: Long = 0,
    /** OneDrive는 quickXorHash, 로컬은 계산했을 때만 채운다. */
    val hash: String? = null,
    /** 원격 항목의 Graph item id. 로컬 항목은 null. */
    val remoteId: String? = null,
)

/** 마지막 동기화 시점에 DB에 저장해 둔 양쪽 상태. */
data class SyncState(
    val path: String,
    val local: SnapshotEntry?,
    val remote: SnapshotEntry?,
)

enum class Change { NONE, NEW, MODIFIED, DELETED }

sealed interface SyncAction {
    val path: String

    /** 로컬 파일을 원격 경로로 올린다. overwriteRemoteId가 있으면 같은 항목을 덮어쓴다. */
    data class Upload(
        override val path: String,
        val overwriteRemoteId: String? = null,
        /** UPLOAD_THEN_DELETE: 성공 후 로컬 파일을 지운다. */
        val deleteLocalAfterSuccess: Boolean = false,
    ) : SyncAction

    /** 원격 파일을 로컬 경로로 내려받는다. */
    data class Download(
        override val path: String,
        /** DOWNLOAD_THEN_DELETE: 성공 후 원격 파일을 지운다. */
        val deleteRemoteAfterSuccess: Boolean = false,
    ) : SyncAction

    data class CreateLocalDirectory(override val path: String) : SyncAction
    data class CreateRemoteDirectory(override val path: String) : SyncAction
    data class DeleteLocal(override val path: String, val isDirectory: Boolean = false) : SyncAction
    data class DeleteRemote(override val path: String, val remoteId: String? = null) : SyncAction

    /** 양쪽이 모두 바뀌었다. 실행 단계에서 로컬 사본을 만든 뒤 해결한다. */
    data class Conflict(override val path: String) : SyncAction

    /**
     * 양쪽 내용이 같다. 새 전송 없이 DB 상태만 맞춘다.
     * alignLocalModifiedMs가 있으면 로컬 mtime을 그 값으로 맞춰 다음 스캔에서 변경으로 보이지 않게 한다.
     */
    data class Link(override val path: String, val alignLocalModifiedMs: Long? = null) : SyncAction

    /** 이 모드에서는 할 일이 없다. DB 상태는 유지한다. */
    data class Ignore(override val path: String, val reason: String) : SyncAction

    /** 양쪽에서 사라졌다. DB 행을 지운다. */
    data class RemoveState(override val path: String) : SyncAction
}

/**
 * 실행 순서: 폴더 생성(얕은 것부터) → 전송/링크 → 삭제(깊은 것부터).
 * 상위 폴더가 없으면 파일을 만들 수 없고, 하위 항목보다 상위 폴더를 먼저 지우면 실패한다.
 */
fun List<SyncAction>.orderedForExecution(): List<SyncAction> {
    fun depth(path: String): Int = path.count { it == '/' }

    val (directories, rest) = partition {
        it is SyncAction.CreateLocalDirectory || it is SyncAction.CreateRemoteDirectory
    }
    val (deletions, transfers) = rest.partition {
        it is SyncAction.DeleteLocal || it is SyncAction.DeleteRemote
    }
    return directories.sortedBy { depth(it.path) } +
        transfers +
        deletions.sortedByDescending { depth(it.path) }
}
