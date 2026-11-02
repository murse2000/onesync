package com.dalbear.onesync.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncCoordinatorTest {

    @Before
    fun reset() {
        SyncCoordinator.finish(null)
    }

    @Test
    fun `count shows progress for the current pair`() {
        SyncCoordinator.begin(pairCount = 2)
        SyncCoordinator.progress("업로드: a.txt")
        SyncCoordinator.count(done = 3, total = 10, pairIndex = 1, pairCount = 2)

        val state = SyncCoordinator.state.value
        assertTrue(state.running)
        assertEquals("업로드: a.txt", state.message)
        assertEquals(3, state.done)
        assertEquals(10, state.total)
        assertEquals(7, state.remaining)
        assertEquals(0.3f, state.fraction, 0.001f)
        assertEquals(1, state.pairIndex)
        assertEquals(2, state.pairCount)
    }

    @Test
    fun `progress keeps counts and planning starts at zero`() {
        SyncCoordinator.begin(pairCount = 1)
        val planned = SyncCoordinator.state.value
        assertEquals(0, planned.done)
        assertEquals(0, planned.total)
        assertEquals(0f, planned.fraction, 0.001f)

        SyncCoordinator.count(done = 0, total = 5, pairIndex = 1, pairCount = 1)
        SyncCoordinator.count(done = 5, total = 5, pairIndex = 1, pairCount = 1)
        assertEquals(0, SyncCoordinator.state.value.remaining)
        assertEquals(1f, SyncCoordinator.state.value.fraction, 0.001f)
    }

    @Test
    fun `finish clears progress`() {
        SyncCoordinator.begin(pairCount = 1)
        SyncCoordinator.count(done = 2, total = 4, pairIndex = 1, pairCount = 1)
        SyncCoordinator.finish("취소됨")

        val state = SyncCoordinator.state.value
        assertFalse(state.running)
        assertEquals("취소됨", state.result)
        assertEquals(0, state.total)
        assertEquals(0, state.remaining)
    }
}
