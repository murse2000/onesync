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
    }
}
