package com.dalbear.onesync.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SyncDatabase(context: Context) :
    SQLiteOpenHelper(context, DbSchema.DB_NAME, null, DbSchema.VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        DbSchema.globalV1.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // 기존 폴더 쌍은 하위 폴더 포함을 기본값으로 유지한다.
            db.execSQL(
                "ALTER TABLE FolderPair ADD COLUMN includeSubdirectories INTEGER NOT NULL DEFAULT 1",
            )
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS RemoteEntry(
                    pairId INTEGER NOT NULL,
                    itemId TEXT NOT NULL,
                    parentId TEXT,
                    name TEXT NOT NULL,
                    isDir INTEGER NOT NULL,
                    size INTEGER NOT NULL,
                    modified INTEGER NOT NULL,
                    hash TEXT,
                    PRIMARY KEY(pairId, itemId)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS IndexRemoteEntryParent ON RemoteEntry(pairId, parentId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS DeltaToken(
                    pairId INTEGER PRIMARY KEY,
                    link TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 4) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS Conflict(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pairId INTEGER NOT NULL,
                    originalPath TEXT NOT NULL,
                    copyPath TEXT NOT NULL,
                    remoteCopyId TEXT,
                    createdAt INTEGER NOT NULL,
                    resolvedAt INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS UploadSession(
                    pairId INTEGER NOT NULL,
                    path TEXT NOT NULL,
                    uploadUrl TEXT NOT NULL,
                    offset INTEGER NOT NULL,
                    fileSize INTEGER NOT NULL,
                    fileModified INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(pairId, path)
                )
                """.trimIndent(),
            )
        }
    }
}
