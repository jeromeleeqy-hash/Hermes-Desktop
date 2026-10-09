package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.WorkspaceEntry
import com.qingyu.hermescompanion.model.WorkspaceListing
import java.io.IOException

internal fun isBareArtifactName(raw: String): Boolean {
    val path = normalizeArtifactTarget(raw).removePrefix("./").removePrefix(".\\")
    return path.isNotBlank() && path !in setOf(".", "..") && path.none { it in "/\\:" || it.code < 32 }
}

internal data class ArtifactSearchResult(
    val candidates: List<WorkspaceEntry>,
    val complete: Boolean,
    val reason: String = "",
)

/** A bounded, read-only walk of the source workspace using the existing files API. */
internal class ArtifactDiscovery(
    private val list: (String, Long) -> WorkspaceListing,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val maxDirectories: Int = 128,
    private val maxEntries: Int = 20_000,
    private val budgetMillis: Long = 10_000,
) {
    fun find(workspace: String, name: String): ArtifactSearchResult {
        val root = normalizeWorkspacePath(workspace)
        val candidates = linkedMapOf<String, WorkspaceEntry>()
        fun result(complete: Boolean, reason: String = "") = ArtifactSearchResult(candidates.values.toList(), complete, reason)
        if (!isAbsoluteRemotePath(root) || remotePathsEqual(root, remoteParentPath(root)) || remoteParentPath(root).isBlank() || !isBareArtifactName(name)) {
            return result(false, "没有可供查找的明确工作目录，请提供文件的完整路径。")
        }
        val deadline = clock() + budgetMillis
        val pending = ArrayDeque<String>().apply { add(root) }
        val visited = mutableSetOf<String>()
        var entriesSeen = 0
        while (pending.isNotEmpty()) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("文件查找已取消")
            val remaining = deadline - clock()
            if (remaining <= 0 || visited.size >= maxDirectories) return result(false, "目录较多或响应较慢，尚未查完，不能确认是否有同名文件。")
            val path = pending.removeFirst()
            if (!visited.add(remotePathKey(path))) continue
            val listing = try {
                list(path, minOf(remaining, 3_000))
            } catch (error: ApiException) {
                if (error.statusCode == 401) throw error
                val reason = if (error.statusCode == 403) "有目录无权读取，尚未完成查找。" else "有目录未能读取，尚未完成查找。"
                return result(false, reason)
            } catch (error: IOException) {
                if (Thread.currentThread().isInterrupted) throw error
                return result(false, "网络请求未完成，尚未查完，请稍后重试或提供完整路径。")
            }
            if (!remotePathsEqual(listing.path, path) || !isRemotePathWithin(root, listing.path)) {
                return result(false, "服务器返回的目录位置不一致，已停止自动查找。")
            }
            entriesSeen += listing.entries.size
            if (entriesSeen > maxEntries) return result(false, "目录项目较多，尚未查完，请提供文件的完整路径。")
            for (entry in listing.entries) {
                if (!isRemotePathWithin(root, entry.path) || !remotePathsEqual(remoteParentPath(entry.path), listing.path)) {
                    return result(false, "目录中存在指向其他位置的项目，无法确认查找结果是否完整。")
                }
                if (entry.isDirectory) {
                    if (remotePathKey(entry.path) !in visited) pending.add(entry.path)
                } else if (entry.name == name && artifactFileName(entry.path) == name) {
                    candidates[remotePathKey(entry.path)] = entry
                    // Ambiguity is already established; do not scan a large tree just to enumerate every duplicate.
                    if (candidates.size >= 8) return result(false, "已找到多个同名文件；这里只列出前 8 个，请按完整路径选择。")
                }
            }
        }
        return result(true)
    }
}

internal class ArtifactLookupException(
    override val message: String,
    val candidates: List<WorkspaceEntry>,
    val details: String,
) : Exception(message)
