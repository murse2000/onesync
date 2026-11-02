package com.dalbear.onesync.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.dalbear.onesync.core.sync.SnapshotEntry
import com.dalbear.onesync.core.sync.SyncMethod
import com.dalbear.onesync.core.sync.SyncState
import com.dalbear.onesync.data.db.SyncDatabase

data class FolderPair(
    val id: Long,
    val name: String,
    val localRoot: String,
    val remoteFolderId: String,
    val remoteFolderPath: String,
    val syncMethod: SyncMethod,
)

data class SyncEventRow(
    val id: Long,
    val type: String,
    val timestamp: Long,
    val path: String?,
    val message: String?,
)

class PairRepository(context: Context) {

    private val helper = SyncDatabase(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    fun create(
        name: String,
        localRoot: String,
        remoteFolderId: String,
        remoteFolderPath: String,
        method: SyncMethod,
    ): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("localRoot", localRoot)
            put("remoteFolderId", remoteFolderId)
            put("remoteFolderPath", remoteFolderPath)
            put("syncMethod", method.name)
            put("isEnabled", 1)
            put("createdAt", System.currentTimeMillis())
        }
        return db.insert("FolderPair", null, values)
    }

    fun list(): List<FolderPair> {
        val result = ArrayList<FolderPair>()
        db.query("FolderPair", null, null, null, null, null, "id").use { cursor ->
            while (cursor.moveToNext()) result += cursor.toPair()
        }
        return result
    }

    fun find(id: Long): FolderPair? {
        db.query("FolderPair", null, "id = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toPair() else null
        }
    }

    fun delete(id: Long) {
        db.beginTransaction()
        try {
            db.delete("SyncItem", "pairId = ?", arrayOf(id.toString()))
            db.delete("SyncEvent", "pairId = ?", arrayOf(id.toString()))
            db.delete("FolderPair", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadState(pairId: Long): List<SyncState> {
        val result = ArrayList<SyncState>()
        db.query("SyncItem", null, "pairId = ?", arrayOf(pairId.toString()), null, null, "path").use { c ->
            while (c.moveToNext()) {
                val path = c.getString(c.getColumnIndexOrThrow("path"))
                val local = if (c.getInt(c.getColumnIndexOrThrow("localExists")) != 0) {
                    SnapshotEntry(
                        path = path,
                        isDirectory = c.getInt(c.getColumnIndexOrThrow("localIsDir")) != 0,
                        size = c.getLong(c.getColumnIndexOrThrow("localSize")),
                        modifiedMs = c.getLong(c.getColumnIndexOrThrow("localModified")),
                    )
                } else {
                    null
                }
                val remote = if (c.getInt(c.getColumnIndexOrThrow("remoteExists")) != 0) {
                    SnapshotEntry(
                        path = path,
                        isDirectory = c.getInt(c.getColumnIndexOrThrow("remoteIsDir")) != 0,
                        size = c.getLong(c.getColumnIndexOrThrow("remoteSize")),
                        modifiedMs = c.getLong(c.getColumnIndexOrThrow("remoteModified")),
                        hash = c.getString(c.getColumnIndexOrThrow("remoteHash")),
                        remoteId = c.getString(c.getColumnIndexOrThrow("remoteId")),
                    )
                } else {
                    null
                }
                result += SyncState(path, local, remote)
            }
        }
        return result
    }

    fun upsertState(pairId: Long, state: SyncState) {
        val values = ContentValues().apply {
            put("pairId", pairId)
            put("path", state.path)
            put("localExists", if (state.local != null) 1 else 0)
            put("localSize", state.local?.size ?: 0)
            put("localModified", state.local?.modifiedMs ?: 0)
            put("localIsDir", if (state.local?.isDirectory == true) 1 else 0)
            put("remoteExists", if (state.remote != null) 1 else 0)
            put("remoteId", state.remote?.remoteId)
            put("remoteSize", state.remote?.size ?: 0)
            put("remoteModified", state.remote?.modifiedMs ?: 0)
            put("remoteIsDir", if (state.remote?.isDirectory == true) 1 else 0)
            put("remoteHash", state.remote?.hash)
            put("syncTime", System.currentTimeMillis())
        }
        db.insertWithOnConflict("SyncItem", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteState(pairId: Long, path: String) {
        db.delete("SyncItem", "pairId = ? AND path = ?", arrayOf(pairId.toString(), path))
    }

    fun updateMethod(pairId: Long, method: SyncMethod) {
        val values = ContentValues().apply { put("syncMethod", method.name) }
        db.update("FolderPair", values, "id = ?", arrayOf(pairId.toString()))
    }

    fun addEvent(pairId: Long, type: String, path: String?, message: String?) {
        val values = ContentValues().apply {
            put("pairId", pairId)
            put("type", type)
            put("timestamp", System.currentTimeMillis())
            put("path", path)
            put("message", message)
        }
        db.insert("SyncEvent", null, values)
    }

    fun recentEvents(pairId: Long, limit: Int = 20): List<SyncEventRow> {
        val result = ArrayList<SyncEventRow>()
        db.query(
            "SyncEvent",
            null,
            "pairId = ?",
            arrayOf(pairId.toString()),
            null,
            null,
            "timestamp DESC",
            limit.toString(),
        ).use { c ->
            while (c.moveToNext()) {
                result += SyncEventRow(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    type = c.getString(c.getColumnIndexOrThrow("type")),
                    timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                    path = c.getString(c.getColumnIndexOrThrow("path")),
                    message = c.getString(c.getColumnIndexOrThrow("message")),
                )
            }
        }
        return result
    }

    private fun Cursor.toPair(): FolderPair {
        val method = getString(getColumnIndexOrThrow("syncMethod"))
        return FolderPair(
            id = getLong(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            localRoot = getString(getColumnIndexOrThrow("localRoot")),
            remoteFolderId = getString(getColumnIndexOrThrow("remoteFolderId")),
            remoteFolderPath = getString(getColumnIndexOrThrow("remoteFolderPath")),
            syncMethod = runCatching { SyncMethod.valueOf(method) }.getOrDefault(SyncMethod.TWO_WAY),
        )
    }
}
