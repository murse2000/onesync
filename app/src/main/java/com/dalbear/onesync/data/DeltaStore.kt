package com.dalbear.onesync.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.dalbear.onesync.core.sync.DeltaTree
import com.dalbear.onesync.core.sync.RemoteNode
import com.dalbear.onesync.core.sync.SnapshotEntry
import com.dalbear.onesync.data.db.SyncDatabase
import com.dalbear.onesync.graph.GraphClient
import com.dalbear.onesync.graph.GraphException

/**
 * OneDrive delta 결과를 폴더 쌍별로 캐시한다.
 * 첫 동기화는 전체 목록을 받아 저장하고, 이후에는 deltaLink로 변경분만 반영한다.
 */
class DeltaStore(
    context: Context,
    private val graph: GraphClient,
) {

    private val helper = SyncDatabase(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    suspend fun refresh(token: String, pair: FolderPair) {
        try {
            syncOnce(token, pair, deltaLink(pair.id))
        } catch (e: GraphException) {
            // 토큰이 만료됐거나 캐시가 오래된 경우: 비우고 전체 재동기화.
            if (e.status == 410) {
                clear(pair.id)
                syncOnce(token, pair, null)
            } else {
                throw e
            }
        }
    }

    fun entries(pairId: Long, rootId: String, includeSubdirectories: Boolean): List<SnapshotEntry> {
        val nodes = ArrayList<RemoteNode>()
        db.query("RemoteEntry", null, "pairId = ?", arrayOf(pairId.toString()), null, null, null)
            .use { c ->
                while (c.moveToNext()) {
                    nodes += RemoteNode(
                        id = c.getString(c.getColumnIndexOrThrow("itemId")),
                        parentId = c.getString(c.getColumnIndexOrThrow("parentId")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        isDirectory = c.getInt(c.getColumnIndexOrThrow("isDir")) != 0,
                        size = c.getLong(c.getColumnIndexOrThrow("size")),
                        modifiedMs = c.getLong(c.getColumnIndexOrThrow("modified")),
                        hash = c.getString(c.getColumnIndexOrThrow("hash")),
                    )
                }
            }
        return DeltaTree.toEntries(nodes, rootId, includeSubdirectories)
    }

    fun clear(pairId: Long) {
        db.beginTransaction()
        try {
            db.delete("RemoteEntry", "pairId = ?", arrayOf(pairId.toString()))
            db.delete("DeltaToken", "pairId = ?", arrayOf(pairId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private suspend fun syncOnce(token: String, pair: FolderPair, link: String?) {
        // deltaLink가 없으면 이전 캐시는 신뢰할 수 없으므로 비우고 전체를 받는다.
        if (link == null) {
            db.delete("RemoteEntry", "pairId = ?", arrayOf(pair.id.toString()))
        }

        val page = graph.delta(token, pair.remoteFolderId, link)

        db.beginTransaction()
        try {
            for (id in page.deletedIds) {
                db.delete(
                    "RemoteEntry",
                    "pairId = ? AND itemId = ?",
                    arrayOf(pair.id.toString(), id),
                )
            }
            for (node in page.nodes) {
                db.insertWithOnConflict(
                    "RemoteEntry",
                    null,
                    ContentValues().apply {
                        put("pairId", pair.id)
                        put("itemId", node.id)
                        put("parentId", node.parentId)
                        put("name", node.name)
                        put("isDir", if (node.isDirectory) 1 else 0)
                        put("size", node.size)
                        put("modified", node.modifiedMs)
                        put("hash", node.hash)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            page.deltaLink?.let { saveDeltaLink(pair.id, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun deltaLink(pairId: Long): String? {
        db.query(
            "DeltaToken",
            arrayOf("link"),
            "pairId = ?",
            arrayOf(pairId.toString()),
            null,
            null,
            null,
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    private fun saveDeltaLink(pairId: Long, link: String) {
        db.insertWithOnConflict(
            "DeltaToken",
            null,
            ContentValues().apply {
                put("pairId", pairId)
                put("link", link)
                put("updatedAt", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }
}
