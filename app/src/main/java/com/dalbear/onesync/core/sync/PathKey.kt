package com.dalbear.onesync.core.sync

import java.text.Normalizer
import java.util.Locale

/**
 * 경로 비교용 키.
 *
 * 원본 앱과 같은 이유로 NFC 정규화 + 소문자 비교를 쓴다.
 * OneDrive 이름은 대소문자를 구분하지 않고, macOS/윈도우에서 만든 파일은 NFD로 들어올 수 있어
 * 같은 파일이 두 번 생성되는 것을 막아야 한다.
 */
object PathKey {
    fun of(path: String): String =
        Normalizer.normalize(path, Normalizer.Form.NFC).lowercase(Locale.ROOT)
}
