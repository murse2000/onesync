package com.dalbear.onesync.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDueTest {

    private val minute = 60_000L

    @Test
    fun `never synced pair is due`() {
        assertTrue(isSyncDue(null, now = 1_000_000L))
    }

    @Test
    fun `pair synced just now is skipped`() {
        val now = 10 * minute
        assertFalse(isSyncDue(now - 30_000L, now))
        assertFalse(isSyncDue(now, now))
    }

    @Test
    fun `pair synced before the window is due again`() {
        val now = 10 * minute
        assertTrue(isSyncDue(now - 2 * minute, now))
        assertTrue(isSyncDue(now - 30 * minute, now))
    }

    @Test
    fun `future timestamp from clock skew is skipped`() {
        val now = 10 * minute
        assertFalse(isSyncDue(now + 5 * minute, now))
    }
}
