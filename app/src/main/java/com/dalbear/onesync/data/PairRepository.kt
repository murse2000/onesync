package com.dalbear.onesync.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.dalbear.onesync.core.sync.SnapshotEntry
import com.dalbear.onesync.core.sync.SyncMethod
import com.dalbear.onesync.core.sync.SyncState
import com.dalbear.onesync.data.db.SyncDatabase
import org.json.JSONArray
import org.json.JSONObject

data class FolderPair(
    val id: Long,
    val name: String,
    val localRoot: String,
    val remoteFolderId: String,
    val remoteFolderPath: String,
    val syncMethod: SyncMethod,
    val includeSubdirectories: Boolean,
    /** 이 폴더 쌍이 동기화할 MSAL 계정 ID. null이면 첫 계정(예전 폴더 쌍). */
    val accountId: String? = null,
    /** 꺼 두면 자동·전체 동기화에서 빠진다. */
    val enabled: Boolean = true,
)

data class SyncEventRow(
    val id: Long,
    val type: String,
    val timestamp: Long,
    val path: String?,
    val message: String?,
)

data class HistoryRow(
    val pairName: String?,
    val type: String,
    val timestamp: Long,
    val path: String?,
    val message: String?,
)

data class ConflictRow(
    val id: Long,
    val pairId: Long,
    val originalPath: String,
    val copyPath: String,
    val remoteCopyId: String?,
    val createdAt: Long,
    val pairName: String?,
)

class PairRepository(context: Context) {

    private val helper = SyncDatabase(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    /** 앱 시작 시 스키마 생성/마이그레이션을 끝내 둔다. */
    fun ensureOpen() {
        helper.writableDatabase
    }

    fun create(
        name: String,
        localRoot: String,
        remoteFolderId: String,
        remoteFolderPath: String,
        method: SyncMethod,
        includeSubdirectories: Boolean,
        accountId: String?,
    ): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("localRoot", localRoot)
            put("remoteFolderId", remoteFolderId)
            put("remoteFolderPath", remoteFolderPath)
            put("syncMethod", method.name)
            put("isEnabled", 1)
            put("includeSubdirectories", if (includeSubdirectories) 1 else 0)
            put("createdAt", System.currentTimeMillis())
            put("accountId", accountId)
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

    /** 켜 둔 폴더 쌍만. 자동·전체·즉시 동기화가 쓴다. */
    fun listEnabled(): List<FolderPair> = list().filter { it.enabled }

    fun setEnabled(id: Long, enabled: Boolean) {
        db.update(
            "FolderPair",
            ContentValues().apply { put("isEnabled", if (enabled) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    /** 폴더 쌍별 마지막 동기화 시각(성공 기록 기준). */
    fun lastSyncTimes(): Map<Long, Long> {
        val result = HashMap<Long, Long>()
        db.rawQuery(
            "SELECT pairId, MAX(timestamp) FROM SyncEvent WHERE type = 'sync' GROUP BY pairId",
            null,
        ).use { c ->
            while (c.moveToNext()) result[c.getLong(0)] = c.getLong(1)
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

    fun updateOptions(
        pairId: Long,
        method: SyncMethod,
        includeSubdirectories: Boolean,
        enabled: Boolean,
    ) {
        val values = ContentValues().apply {
            put("syncMethod", method.name)
            put("includeSubdirectories", if (includeSubdirectories) 1 else 0)
            put("isEnabled", if (enabled) 1 else 0)
        }
        db.update("FolderPair", values, "id = ?", arrayOf(pairId.toString()))
    }

    /** 폴더 쌍 설정을 JSON으로 내보낸다. 동기화 상태는 담지 않는다. */
    fun exportJson(): String {
        val pairs = JSONArray()
        list().forEach { pair ->
            pairs.put(
                JSONObject()
                    .put("name", pair.name)
                    .put("localRoot", pair.localRoot)
                    .put("remoteFolderId", pair.remoteFolderId)
                    .put("remoteFolderPath", pair.remoteFolderPath)
                    .put("syncMethod", pair.syncMethod.name)
                    .put("includeSubdirectories", pair.includeSubdirectories)
                    .put("enabled", pair.enabled)
                    .put("accountId", pair.accountId),
            )
        }
        return JSONObject()
            .put("version", 1)
            .put("pairs", pairs)
            .toString(2)
    }

    /**
     * 내보낸 JSON을 가져온다. 이미 있는 (로컬 경로, 원격 폴더) 조합은 건너뛴다.
     * 동기화 상태는 새로 만들지 않으므로 다음 동기화에서 양쪽을 다시 비교한다.
     * @return 새로 추가한 폴더 쌍 수
     */
    fun importJson(text: String): Int {
        val root = JSONObject(text)
        val pairs = root.optJSONArray("pairs") ?: return 0
        val existing = list()
        var added = 0
        for (i in 0 until pairs.length()) {
            val item = pairs.optJSONObject(i) ?: continue
            val localRoot = item.optString("localRoot")
            val remoteFolderId = item.optString("remoteFolderId")
            if (localRoot.isBlank() || remoteFolderId.isBlank()) continue
            if (existing.any { it.localRoot == localRoot && it.remoteFolderId == remoteFolderId }) continue

            val id = create(
                name = item.optString("name").ifBlank { "OneDrive 동기화" },
                localRoot = localRoot,
                remoteFolderId = remoteFolderId,
                remoteFolderPath = item.optString("remoteFolderPath"),
                method = runCatching {
                    SyncMethod.valueOf(item.optString("syncMethod"))
                }.getOrDefault(SyncMethod.TWO_WAY),
                includeSubdirectories = item.optBoolean("includeSubdirectories", true),
                accountId = item.optString("accountId").takeIf { it.isNotBlank() },
            )
            setEnabled(id, item.optBoolean("enabled", true))
            added++
        }
        return added
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

    /** 모든 폴더 쌍의 기록을 최신순으로. */
    fun recentHistory(limit: Int = 200): List<HistoryRow> {
        val sql = """
            SELECT e.type, e.timestamp, e.path, e.message, p.name AS pairName
            FROM SyncEvent e
            LEFT JOIN FolderPair p ON p.id = e.pairId
            ORDER BY e.timestamp DESC
            LIMIT ?
        """.trimIndent()
        val result = ArrayList<HistoryRow>()
        db.rawQuery(sql, arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) {
                result += HistoryRow(
                    pairName = c.getString(4),
                    type = c.getString(0),
                    timestamp = c.getLong(1),
                    path = c.getString(2),
                    message = c.getString(3),
                )
            }
        }
        return result
    }

    fun addConflict(
        pairId: Long,
        originalPath: String,
        copyPath: String,
        remoteCopyId: String?,
    ) {
        db.insert("Conflict", null, ContentValues().apply {
            put("pairId", pairId)
            put("originalPath", originalPath)
            put("copyPath", copyPath)
            put("remoteCopyId", remoteCopyId)
            put("createdAt", System.currentTimeMillis())
        })
    }

    fun unresolvedConflicts(): List<ConflictRow> {
        val sql = """
            SELECT c.id, c.pairId, c.originalPath, c.copyPath, c.remoteCopyId, c.createdAt, p.name AS pairName
            FROM Conflict c
            LEFT JOIN FolderPair p ON p.id = c.pairId
            WHERE c.resolvedAt IS NULL
            ORDER BY c.createdAt DESC
        """.trimIndent()
        val result = ArrayList<ConflictRow>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                result += ConflictRow(
                    id = c.getLong(0),
                    pairId = c.getLong(1),
                    originalPath = c.getString(2),
                    copyPath = c.getString(3),
                    remoteCopyId = c.getString(4),
                    createdAt = c.getLong(5),
                    pairName = c.getString(6),
                )
            }
        }
        return result
    }

    fun conflictCount(): Int {
        db.rawQuery("SELECT COUNT(*) FROM Conflict WHERE resolvedAt IS NULL", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun resolveConflict(id: Long) {
        db.update(
            "Conflict",
            ContentValues().apply { put("resolvedAt", System.currentTimeMillis()) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }
    private fun Cursor.toPair(): FolderPair {
        val method = getString(getColumnIndexOrThrow("syncMethod"))
        val accountIndex = getColumnIndexOrThrow("accountId")
        return FolderPair(
            id = getLong(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            localRoot = getString(getColumnIndexOrThrow("localRoot")),
            remoteFolderId = getString(getColumnIndexOrThrow("remoteFolderId")),
            remoteFolderPath = getString(getColumnIndexOrThrow("remoteFolderPath")),
            syncMethod = runCatching { SyncMethod.valueOf(method) }.getOrDefault(SyncMethod.TWO_WAY),
            includeSubdirectories = getInt(getColumnIndexOrThrow("includeSubdirectories")) != 0,
            accountId = if (isNull(accountIndex)) null else getString(accountIndex),
            enabled = getInt(getColumnIndexOrThrow("isEnabled")) != 0,
        )
    }
}
