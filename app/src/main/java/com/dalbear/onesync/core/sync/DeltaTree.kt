package com.dalbear.onesync.core.sync

/** delta 응답에서 얻은 원격 항목. 경로는 부모 체인으로 복원한다. */
data class RemoteNode(
    val id: String,
    val parentId: String?,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val modifiedMs: Long = 0,
    val hash: String? = null,
)

object DeltaTree {

    /**
     * 부모 ID 체인으로 상대 경로를 복원한다.
     * [includeSubdirectories]가 false면 최상위 항목만 돌려주고 하위 폴더는 순회하지 않는다.
     */
    fun toEntries(
        nodes: Collection<RemoteNode>,
        rootId: String,
        includeSubdirectories: Boolean,
    ): List<SnapshotEntry> {
        val childrenByParent = nodes
            .filter { it.id != rootId }
            .groupBy { it.parentId }

        val result = ArrayList<SnapshotEntry>()
        walk(rootId, "", includeSubdirectories, childrenByParent, result, 0)
        return result
    }

    private fun walk(
        parentId: String,
        prefix: String,
        includeSubdirectories: Boolean,
        childrenByParent: Map<String?, List<RemoteNode>>,
        out: MutableList<SnapshotEntry>,
        depth: Int,
    ) {
        if (depth > 64) return
        for (node in childrenByParent[parentId].orEmpty()) {
            if (node.isDirectory && !includeSubdirectories) continue
            val path = if (prefix.isEmpty()) node.name else "$prefix/${node.name}"
            out += SnapshotEntry(
                path = path,
                isDirectory = node.isDirectory,
                size = node.size,
                modifiedMs = node.modifiedMs,
                hash = node.hash,
                remoteId = node.id,
            )
            if (node.isDirectory) {
                walk(node.id, path, includeSubdirectories, childrenByParent, out, depth + 1)
            }
        }
    }
}
