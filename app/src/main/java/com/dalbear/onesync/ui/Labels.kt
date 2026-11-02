package com.dalbear.onesync.ui

import com.dalbear.onesync.core.sync.SyncMethod

fun syncMethodLabel(method: SyncMethod): String = when (method) {
    SyncMethod.TWO_WAY -> "양방향"
    SyncMethod.UPLOAD_ONLY -> "업로드만"
    SyncMethod.UPLOAD_THEN_DELETE -> "업로드 후 로컬 삭제"
    SyncMethod.UPLOAD_MIRROR -> "업로드 미러"
    SyncMethod.DOWNLOAD_ONLY -> "다운로드만"
    SyncMethod.DOWNLOAD_THEN_DELETE -> "다운로드 후 원격 삭제"
    SyncMethod.DOWNLOAD_MIRROR -> "다운로드 미러"
}
