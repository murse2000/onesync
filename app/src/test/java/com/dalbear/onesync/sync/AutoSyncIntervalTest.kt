package com.dalbear.onesync.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSyncIntervalTest {

    @Test
    fun `labels minutes and hours`() {
        assertEquals("15분마다", autoSyncIntervalLabel(15))
        assertEquals("30분마다", autoSyncIntervalLabel(30))
        assertEquals("1시간마다", autoSyncIntervalLabel(60))
        assertEquals("3시간마다", autoSyncIntervalLabel(180))
    }
}
