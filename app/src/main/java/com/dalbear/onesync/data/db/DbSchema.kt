package com.dalbear.onesync.data.db

/**
 * v1 스키마.
 *
 * 원본 앱은 폴더 쌍마다 별도 DB를 썼지만, v1에서는 전역 DB 한 개에 pairId로 구분해 저장한다.
 * 상태(SyncItem)의 의미는 원본과 같다: 마지막 동기화 시점의 양쪽 경로/크기/mtime/해시.
 */
object DbSchema {

    const val DB_NAME = "onesync.db"

    /** v2: FolderPair.includeSubdirectories 추가. */
    const val VERSION = 2

    val globalV1: List<String> = listOf(
        """
        CREATE TABLE Account(
            id TEXT PRIMARY KEY,
            accountType TEXT NOT NULL,
            email TEXT,
            displayName TEXT,
            msalAccountId TEXT,
            usedQuota INTEGER NOT NULL DEFAULT 0,
            totalQuota INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
        """
        CREATE TABLE FolderPair(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            localRoot TEXT NOT NULL,
            remoteFolderId TEXT NOT NULL,
            remoteFolderPath TEXT NOT NULL,
            syncMethod TEXT NOT NULL,
            isEnabled INTEGER NOT NULL DEFAULT 1,
            includeSubdirectories INTEGER NOT NULL DEFAULT 1,
            createdAt INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE SyncItem(
            pairId INTEGER NOT NULL,
            path TEXT NOT NULL,
            localExists INTEGER NOT NULL,
            localSize INTEGER NOT NULL,
            localModified INTEGER NOT NULL,
            localIsDir INTEGER NOT NULL,
            remoteExists INTEGER NOT NULL,
            remoteId TEXT,
            remoteSize INTEGER NOT NULL,
            remoteModified INTEGER NOT NULL,
            remoteIsDir INTEGER NOT NULL,
            remoteHash TEXT,
            syncTime INTEGER NOT NULL,
            PRIMARY KEY(pairId, path)
        )
        """.trimIndent(),
        """
        CREATE TABLE SyncEvent(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            pairId INTEGER NOT NULL,
            type TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            path TEXT,
            message TEXT
        )
        """.trimIndent(),
        "CREATE INDEX IndexSyncEventPairTime ON SyncEvent(pairId, timestamp)",
    )
}
