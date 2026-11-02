package com.dalbear.onesync.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class DeltaTreeTest {

    private val nodes = listOf(
        RemoteNode(id = "a", parentId = "root", name = "docs", isDirectory = true),
        RemoteNode(id = "b", parentId = "a", name = "note.txt", isDirectory = false, size = 10, modifiedMs = 1_000, hash = "h1"),
        RemoteNode(id = "c", parentId = "root", name = "photo.jpg", isDirectory = false, size = 20, modifiedMs = 2_000, hash = "h2"),
        RemoteNode(id = "orphan", parentId = "missing", name = "lost.txt", isDirectory = false),
    )

    @Test
    fun `restores paths from parent chain`() {
        val entries = DeltaTree.toEntries(nodes, "root", includeSubdirectories = true)
        assertEquals(setOf("docs", "docs/note.txt", "photo.jpg"), entries.map { it.path }.toSet())

        val note = entries.first { it.path == "docs/note.txt" }
        assertEquals("b", note.remoteId)
        assertEquals(10L, note.size)
        assertEquals("h1", note.hash)
    }

    @Test
    fun `top level only skips subdirectories and their children`() {
        val entries = DeltaTree.toEntries(nodes, "root", includeSubdirectories = false)
        assertEquals(listOf("photo.jpg"), entries.map { it.path })
    }

    @Test
    fun `orphans are excluded`() {
        val entries = DeltaTree.toEntries(nodes, "root", includeSubdirectories = true)
        assertEquals(null, entries.firstOrNull { it.path.contains("lost") })
    }
}
