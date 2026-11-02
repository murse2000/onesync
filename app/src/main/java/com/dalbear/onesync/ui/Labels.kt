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

/** 사용법 화면에서 방식 이름 옆에 붙는 한 줄 설명. */
fun syncMethodHelp(method: SyncMethod): String = when (method) {
    SyncMethod.TWO_WAY -> "폰과 OneDrive의 변경을 서로 반영합니다."
    SyncMethod.UPLOAD_ONLY -> "폰에서 바뀐 것만 OneDrive로 올립니다."
    SyncMethod.UPLOAD_THEN_DELETE -> "올린 뒤 폰에서 지웁니다. OneDrive 백업용입니다."
    SyncMethod.UPLOAD_MIRROR -> "OneDrive를 폰과 똑같이 맞춥니다. OneDrive에만 있는 파일은 지워집니다."
    SyncMethod.DOWNLOAD_ONLY -> "OneDrive에서 바뀐 것만 폰으로 내려받습니다."
    SyncMethod.DOWNLOAD_THEN_DELETE -> "내려받은 뒤 OneDrive에서 지웁니다."
    SyncMethod.DOWNLOAD_MIRROR -> "폰을 OneDrive와 똑같이 맞춥니다. 폰에만 있는 파일은 지워집니다."
}
