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
