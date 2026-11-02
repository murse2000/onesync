package com.dalbear.onesync.sync

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalScannerTest {

    @Test
    fun `scans nested files and skips hidden entries`() {
        val root = Files.createTempDirectory("onesync-scan").toFile()
        try {
            File(root, "a.txt").writeText("a")
            File(root, ".nomedia").writeText("hidden")
            val sub = File(root, "sub").apply { mkdirs() }
            File(sub, "b.txt").writeText("bb")

            val entries = LocalScanner.scan(root)
            val paths = entries.map { it.path }.toSet()

            assertEquals(setOf("a.txt", "sub", "sub/b.txt"), paths)
            assertTrue(entries.first { it.path == "sub" }.isDirectory)
            assertEquals(2L, entries.first { it.path == "sub/b.txt" }.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `missing directory returns empty list`() {
        val missing = File("definitely-not-a-real-directory-12345")
        assertEquals(emptyList<Any>(), LocalScanner.scan(missing))
    }
}
