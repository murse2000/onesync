package com.dalbear.onesync.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlannerTest {

    private val planner = SyncPlanner()

    // ── 양방향 ────────────────────────────────────────────────────────────────

    @Test
    fun `twoWay uploads new local file`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            local = listOf(file("a.txt")),
        )
        assertEquals(listOf(SyncAction.Upload("a.txt")), actions)
    }

    @Test
    fun `twoWay downloads new remote file`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            remote = listOf(file("b.txt", id = "remote-1")),
        )
        assertEquals(listOf(SyncAction.Download("b.txt")), actions)
    }

    @Test
    fun `twoWay conflicts when both sides changed differently`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            previous = listOf(state("c.txt", file("c.txt"), file("c.txt", id = "r1"))),
            local = listOf(file("c.txt", size = 200, mtime = 2_000)),
            remote = listOf(file("c.txt", size = 300, mtime = 3_000, id = "r1")),
        )
        assertEquals(listOf(SyncAction.Conflict("c.txt")), actions)
    }

    @Test
    fun `twoWay links when both sides changed to the same content`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            previous = listOf(state("c.txt", file("c.txt"), file("c.txt", id = "r1"))),
            local = listOf(file("c.txt", size = 100, mtime = 5_000)),
            remote = listOf(file("c.txt", size = 100, mtime = 2_000, id = "r1")),
        )
        // 업로드 가능한 모드이므로 로컬 mtime을 원격에 맞춘다.
        assertEquals(listOf(SyncAction.Link("c.txt", 2_000)), actions)
    }

    @Test
    fun `twoWay local deletion deletes remote`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            previous = listOf(state("c.txt", file("c.txt"), file("c.txt", id = "r1"))),
            remote = listOf(file("c.txt", id = "r1")),
        )
        assertEquals(listOf(SyncAction.DeleteRemote("c.txt", "r1")), actions)
    }

    @Test
    fun `twoWay remote deletion deletes local`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            previous = listOf(state("c.txt", file("c.txt"), file("c.txt", id = "r1"))),
            local = listOf(file("c.txt")),
        )
        assertEquals(listOf(SyncAction.DeleteLocal("c.txt")), actions)
    }

    @Test
    fun `both sides deleted removes state row`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            previous = listOf(state("c.txt", file("c.txt"), file("c.txt", id = "r1"))),
        )
        assertEquals(listOf(SyncAction.RemoveState("c.txt")), actions)
    }

    // ── 한쪽 방향 모드 ────────────────────────────────────────────────────────

    @Test
    fun `uploadOnly ignores remote change`() {
        val actions = planner.plan(
            method = SyncMethod.UPLOAD_ONLY,
            previous = listOf(state("c.txt", file("c.txt"), file("c.txt", id = "r1"))),
            local = listOf(file("c.txt")),
            remote = listOf(file("c.txt", size = 200, id = "r1")),
        )
        assertTrue(actions.single() is SyncAction.Ignore)
    }

    @Test
    fun `uploadOnly ignores remote deletion`() {
        val actions = planner.plan(
            method = SyncMethod.UPLOAD_ONLY,
            previous = listOf(state("c.txt", file("c.txt"), file("c.txt", id = "r1"))),
            local = listOf(file("c.txt")),
        )
        assertTrue(actions.single() is SyncAction.Ignore)
    }

    @Test
    fun `uploadThenDelete marks local copy for deletion after upload`() {
        val actions = planner.plan(
            method = SyncMethod.UPLOAD_THEN_DELETE,
            local = listOf(file("new.txt")),
        )
        assertEquals(listOf(SyncAction.Upload("new.txt", null, deleteLocalAfterSuccess = true)), actions)
    }

    @Test
    fun `downloadThenDelete marks remote copy for deletion after download`() {
        val actions = planner.plan(
            method = SyncMethod.DOWNLOAD_THEN_DELETE,
            remote = listOf(file("r.txt", id = "r1")),
        )
        assertEquals(listOf(SyncAction.Download("r.txt", deleteRemoteAfterSuccess = true)), actions)
    }

    // ── 미러 모드 ─────────────────────────────────────────────────────────────

    @Test
    fun `uploadMirror deletes remote-only file`() {
        val actions = planner.plan(
            method = SyncMethod.UPLOAD_MIRROR,
            remote = listOf(file("r.txt", id = "r1")),
        )
        assertEquals(listOf(SyncAction.DeleteRemote("r.txt", "r1")), actions)
    }

    @Test
    fun `uploadMirror re-uploads file deleted remotely`() {
        val actions = planner.plan(
            method = SyncMethod.UPLOAD_MIRROR,
            previous = listOf(state("f.txt", file("f.txt"), file("f.txt", id = "r1"))),
            local = listOf(file("f.txt")),
        )
        assertEquals(listOf(SyncAction.Upload("f.txt")), actions)
    }

    @Test
    fun `downloadMirror deletes local-only file`() {
        val actions = planner.plan(
            method = SyncMethod.DOWNLOAD_MIRROR,
            local = listOf(file("l.txt")),
        )
        assertEquals(listOf(SyncAction.DeleteLocal("l.txt")), actions)
    }

    @Test
    fun `downloadMirror restores locally deleted file`() {
        val actions = planner.plan(
            method = SyncMethod.DOWNLOAD_MIRROR,
            previous = listOf(state("f.txt", file("f.txt"), file("f.txt", id = "r1"))),
            remote = listOf(file("f.txt", id = "r1")),
        )
        assertEquals(listOf(SyncAction.Download("f.txt")), actions)
    }

    // ── 처음 연결 / 애매한 경우 ───────────────────────────────────────────────

    @Test
    fun `new local file with same content as untracked remote file is linked`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            previous = listOf(state("dup.txt", null, file("dup.txt", size = 100, mtime = 1_000, id = "r1"))),
            local = listOf(file("dup.txt", size = 100, mtime = 2_000)),
            remote = listOf(file("dup.txt", size = 100, mtime = 1_000, id = "r1")),
        )
        assertEquals(listOf(SyncAction.Link("dup.txt", 1_000)), actions)
    }

    @Test
    fun `new local file with different content from untracked remote file conflicts`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            previous = listOf(state("dup.txt", null, file("dup.txt", size = 100, id = "r1"))),
            local = listOf(file("dup.txt", size = 200)),
            remote = listOf(file("dup.txt", size = 100, id = "r1")),
        )
        assertEquals(listOf(SyncAction.Conflict("dup.txt")), actions)
    }

    @Test
    fun `file and directory at the same path conflict`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            local = listOf(file("x")),
            remote = listOf(dir("x")),
        )
        assertEquals(listOf(SyncAction.Conflict("x")), actions)
    }

    // ── 폴더 ─────────────────────────────────────────────────────────────────

    @Test
    fun `new local directory creates remote directory`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            local = listOf(dir("photos")),
        )
        assertEquals(listOf(SyncAction.CreateRemoteDirectory("photos")), actions)
    }

    @Test
    fun `directory deleted remotely is deleted locally in two-way mode`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            previous = listOf(state("photos", dir("photos"), dir("photos"))),
            local = listOf(dir("photos")),
        )
        assertEquals(listOf(SyncAction.DeleteLocal("photos", isDirectory = true)), actions)
    }

    @Test
    fun `uploadOnly ignores new remote directory`() {
        val actions = planner.plan(
            method = SyncMethod.UPLOAD_ONLY,
            remote = listOf(dir("remote-only")),
        )
        assertTrue(actions.single() is SyncAction.Ignore)
    }

    // ── 이름 매칭 ─────────────────────────────────────────────────────────────

    @Test
    fun `paths are matched case-insensitively and NFC-normalized`() {
        val actions = planner.plan(
            method = SyncMethod.TWO_WAY,
            previous = listOf(state("Ångström.txt", file("Ångström.txt"), file("Ångström.txt", id = "r1"))),
            local = listOf(file("Ångström.txt")),
            remote = listOf(file("Ångström.txt", id = "r1")),
        )
        assertEquals(listOf(SyncAction.Link("Ångström.txt", null)), actions)
    }

    // ── 실행 순서 ─────────────────────────────────────────────────────────────

    @Test
    fun `directories are created first and deletions run deepest first`() {
        val ordered = listOf(
            SyncAction.DeleteLocal("a/b/c.txt"),
            SyncAction.Upload("a/b/c.txt"),
            SyncAction.CreateRemoteDirectory("a/b"),
            SyncAction.DeleteRemote("a/b"),
        ).orderedForExecution()

        assertEquals(
            listOf(
                SyncAction.CreateRemoteDirectory("a/b"),
                SyncAction.Upload("a/b/c.txt"),
                SyncAction.DeleteLocal("a/b/c.txt"),
                SyncAction.DeleteRemote("a/b"),
            ),
            ordered,
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun file(
        path: String,
        size: Long = 100,
        mtime: Long = 1_000,
        hash: String? = null,
        id: String? = null,
    ) = SnapshotEntry(
        path = path,
        isDirectory = false,
        size = size,
        modifiedMs = mtime,
        hash = hash,
        remoteId = id,
    )

    private fun dir(path: String) = SnapshotEntry(path = path, isDirectory = true)

    private fun state(path: String, local: SnapshotEntry?, remote: SnapshotEntry?) =
        SyncState(path = path, local = local, remote = remote)
}
