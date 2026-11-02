package com.dalbear.onesync.core.sync

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 충돌 시 로컬 파일 이름을 바꿔 양쪽을 모두 보존한다.
 * 원본 앱과 같은 형식: "name (conflict yyyy-MM-dd-HH-mm-ss).ext"
 */
object ConflictNames {

    private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")

    fun forFile(name: String, epochMillis: Long): String {
        val timestamp = LocalDateTime
            .ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .format(FORMAT)
        val suffix = " (conflict $timestamp)"
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) {
            name + suffix
        } else {
            name.substring(0, dot) + suffix + name.substring(dot)
        }
    }
}
