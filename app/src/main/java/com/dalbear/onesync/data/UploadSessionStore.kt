package com.dalbear.onesync.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.dalbear.onesync.data.db.SyncDatabase

/**
 * 앱이 중간에 종료돼도 업로드 세션을 이어받을 수 있게 상태를 저장한다.
 * 파일 크기/수정 시간이 바뀌면 다른 파일로 보고 세션을 재사용하지 않는다.
 */
class UploadSessionStore(context: Context) {

    data class Row(
        val url: String,
        val offset: Long,
        val fileSize: Long,
        val fileModified: Long,
    )

    private val helper = SyncDatabase(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    fun find(pairId: Long, path: String): Row? {
        db.query(
            "UploadSession",
            null,
            "pairId = ? AND path = ?",
            arrayOf(pairId.toString(), path),
            null,
            null,
            null,
        ).use { c ->
            if (!c.moveToFirst()) return null
            return Row(
                url = c.getString(c.getColumnIndexOrThrow("uploadUrl")),
                offset = c.getLong(c.getColumnIndexOrThrow("offset")),
                fileSize = c.getLong(c.getColumnIndexOrThrow("fileSize")),
                fileModified = c.getLong(c.getColumnIndexOrThrow("fileModified")),
            )
        }
    }

    fun save(
        pairId: Long,
        path: String,
        url: String,
        offset: Long,
        fileSize: Long,
        fileModified: Long,
    ) {
        db.insertWithOnConflict(
            "UploadSession",
            null,
            ContentValues().apply {
                put("pairId", pairId)
                put("path", path)
                put("uploadUrl", url)
                put("offset", offset)
                put("fileSize", fileSize)
                put("fileModified", fileModified)
                put("updatedAt", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun clear(pairId: Long, path: String) {
        db.delete(
            "UploadSession",
            "pairId = ? AND path = ?",
            arrayOf(pairId.toString(), path),
        )
    }
}
