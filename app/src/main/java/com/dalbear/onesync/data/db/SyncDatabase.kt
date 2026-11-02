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
        // v1에서는 아직 마이그레이션이 없다. 개발 중에는 앱 데이터를 지우면 된다.
        db.execSQL("DROP TABLE IF EXISTS SyncEvent")
        db.execSQL("DROP TABLE IF EXISTS SyncItem")
        db.execSQL("DROP TABLE IF EXISTS FolderPair")
        db.execSQL("DROP TABLE IF EXISTS Account")
        onCreate(db)
    }
}
