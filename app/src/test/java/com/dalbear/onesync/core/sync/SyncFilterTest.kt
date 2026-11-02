package com.dalbear.onesync.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFilterTest {

    @Test
    fun `hidden files can be excluded`() {
        val filter = SyncFilter(FilterOptions(excludeHidden = true))
        assertTrue(filter.isExcluded(".nomedia", isDirectory = false))
        assertTrue(filter.isExcluded(".thumbnails", isDirectory = true))
        assertFalse(filter.isExcluded("photo.jpg", isDirectory = false))
    }

    @Test
    fun `exclude pattern matches name not full path`() {
        val filter = SyncFilter(FilterOptions(excludePatterns = listOf("*.tmp")))
        assertTrue(filter.isExcluded("cache/photo.tmp", isDirectory = false))
        assertFalse(filter.isExcluded("cache/photo.jpg", isDirectory = false))
    }

    @Test
    fun `include patterns apply to files but never prune directories`() {
        val filter = SyncFilter(FilterOptions(includePatterns = listOf("*.jpg")))
        assertFalse(filter.isExcluded("photo.jpg", isDirectory = false))
        assertTrue(filter.isExcluded("note.txt", isDirectory = false))
        assertFalse(filter.isExcluded("nested", isDirectory = true))
    }

    @Test
    fun `excluding a directory also excludes its contents`() {
        val filter = SyncFilter(FilterOptions(excludePatterns = listOf("node_modules", ".git")))
        assertTrue(filter.isExcluded("node_modules", isDirectory = true))
        assertTrue(filter.isExcluded("node_modules/lib/index.js", isDirectory = false))
        assertTrue(filter.isExcluded(".git/config", isDirectory = false))
        assertFalse(filter.isExcluded("src/index.js", isDirectory = false))
    }

    @Test
    fun `max file size excludes only files`() {
        val filter = SyncFilter(FilterOptions(maxFileSize = 100))
        assertTrue(filter.isExcluded("big.bin", isDirectory = false, size = 101))
        assertFalse(filter.isExcluded("big.bin", isDirectory = false, size = 100))
        assertFalse(filter.isExcluded("folder", isDirectory = true, size = 0))
    }

    @Test
    fun `empty rules exclude nothing`() {
        val filter = SyncFilter()
        assertFalse(filter.isExcluded("anything.txt", isDirectory = false, size = 10_000_000))
    }

    @Test
    fun `only top level skips nested entries`() {
        val filter = SyncFilter(FilterOptions(onlyTopLevel = true))
        assertFalse(filter.isExcluded("photo.jpg", isDirectory = false))
        assertTrue(filter.isExcluded("nested/photo.jpg", isDirectory = false))
    }

    @Test
    fun `android-illegal remote names are reported`() {
        assertNull(SyncFilter.invalidLocalNameReason("normal file.txt"))
        assertNotNull(SyncFilter.invalidLocalNameReason("a:b.txt"))
        assertNotNull(SyncFilter.invalidLocalNameReason("trailing."))
        assertNotNull(SyncFilter.invalidLocalNameReason("trailing "))
        assertNotNull(SyncFilter.invalidLocalNameReason(""))
    }

    @Test
    fun `path key is case-insensitive and nfc-normalized`() {
        val composed = "Ångström.txt"
        val decomposed = "A\u030Angstro\u0308m.txt"
        assertEquals(PathKey.of(composed), PathKey.of(decomposed))
        assertEquals(PathKey.of("A.TXT"), PathKey.of("a.txt"))
    }
}
