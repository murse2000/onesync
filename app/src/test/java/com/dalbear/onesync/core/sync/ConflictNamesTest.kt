package com.dalbear.onesync.core.sync

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictNamesTest {

    private val stamp = LocalDateTime.of(2026, 11, 2, 14, 5, 9)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Test
    fun `keeps the extension`() {
        assertEquals(
            "photo (conflict 2026-11-02-14-05-09).jpg",
            ConflictNames.forFile("photo.jpg", stamp),
        )
    }

    @Test
    fun `handles names without extension`() {
        assertEquals(
            "README (conflict 2026-11-02-14-05-09)",
            ConflictNames.forFile("README", stamp),
        )
    }

    @Test
    fun `handles dotfiles`() {
        assertEquals(
            ".gitignore (conflict 2026-11-02-14-05-09)",
            ConflictNames.forFile(".gitignore", stamp),
        )
    }

    @Test
    fun `only the last extension is separated`() {
        assertEquals(
            "archive.tar (conflict 2026-11-02-14-05-09).gz",
            ConflictNames.forFile("archive.tar.gz", stamp),
        )
    }
}
