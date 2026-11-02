package com.dalbear.onesync.sync

import com.dalbear.onesync.core.sync.SyncAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWorkCountTest {

    @Test
    fun `only real transfers count as work`() {
        assertTrue(SyncAction.Upload("a.txt", null, false).countsAsWork())
        assertTrue(SyncAction.Download("a.txt", false).countsAsWork())
        assertTrue(SyncAction.DeleteLocal("a.txt").countsAsWork())
        assertTrue(SyncAction.DeleteRemote("a.txt", null).countsAsWork())
        assertTrue(SyncAction.Conflict("a.txt").countsAsWork())
        assertTrue(SyncAction.CreateLocalDirectory("dir").countsAsWork())
        assertTrue(SyncAction.CreateRemoteDirectory("dir").countsAsWork())
    }

    @Test
    fun `linking already synced items is not work`() {
        assertFalse(SyncAction.Link("a.txt").countsAsWork())
        assertFalse(SyncAction.RemoveState("a.txt").countsAsWork())
        assertFalse(SyncAction.Ignore("a.txt", "이유").countsAsWork())
    }
}
