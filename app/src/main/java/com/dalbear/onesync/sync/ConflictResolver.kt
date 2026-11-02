package com.dalbear.onesync.sync

import com.dalbear.onesync.auth.AccountSessions
import com.dalbear.onesync.data.ConflictRow
import com.dalbear.onesync.data.FolderPair
import com.dalbear.onesync.data.PairRepository
import com.dalbear.onesync.graph.GraphClient
import java.io.File

/**
 * 충돌 사본을 정리한다.
 * - keepRemote: 로컬/원격 사본을 지우고 원격 버전을 남긴다.
 * - keepLocal: 로컬 사본을 원본 자리에 덮어쓰고 사본을 지운다. 다음 동기화가 원격 원본을 갱신한다.
 */
class ConflictResolver(
    private val repo: PairRepository,
    private val sessions: AccountSessions,
    private val graph: GraphClient,
) {

    suspend fun keepRemote(conflict: ConflictRow) {
        val pair = repo.find(conflict.pairId) ?: return
        File(pair.localRoot, conflict.copyPath).delete()
        repo.deleteState(conflict.pairId, conflict.copyPath)
        deleteRemoteCopy(pair, conflict)
        repo.resolveConflict(conflict.id)
    }

    suspend fun keepLocal(conflict: ConflictRow) {
        val pair = repo.find(conflict.pairId) ?: return
        val original = File(pair.localRoot, conflict.originalPath)
        val copy = File(pair.localRoot, conflict.copyPath)
        if (!copy.isFile) return

        copy.copyTo(original, overwrite = true)
        copy.delete()
        repo.deleteState(conflict.pairId, conflict.copyPath)
        deleteRemoteCopy(pair, conflict)
        repo.resolveConflict(conflict.id)
    }

    private suspend fun deleteRemoteCopy(pair: FolderPair, conflict: ConflictRow) {
        val remoteId = conflict.remoteCopyId ?: return
        val token = sessions.tokenFor(pair.accountId).getOrElse { return }
        runCatching { graph.deleteItem(token, remoteId) }
    }
}
