package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.i18n.uiText
import com.qingyu.hermescompanion.R


import com.qingyu.hermescompanion.model.ChatMessage
import com.qingyu.hermescompanion.model.HermesSession
import com.qingyu.hermescompanion.model.RecentArtifact
import com.qingyu.hermescompanion.model.WorkspaceDocument
import java.net.URLDecoder

internal fun artifactFileName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }

/** Decode link syntax once, without turning literal '+' characters into spaces. */
internal fun normalizeArtifactTarget(raw: String, markdownLink: Boolean = false): String {
    var value = raw.trim().trim('`', '"', '\'', '<', '>').trim()
    if (value.startsWith("MEDIA:", true)) value = value.substring(6).trim().trim('`', '"', '\'')
    val fileUri = value.startsWith("file://", true)
    val sandboxUri = value.startsWith("sandbox:", true)
    if (fileUri) {
        value = value.substring(7).removePrefix("localhost")
        // file:///C:/work and file://server/share are Windows URI spellings.
        if (Regex("^/[A-Za-z]:/").containsMatchIn(value)) value = value.substring(1)
        else if (!value.startsWith('/') && !isWindowsRemotePath(value)) value = "//$value"
    }
    if (sandboxUri) value = value.substring(8)
    if (markdownLink || fileUri || sandboxUri) {
        value = value.substringBefore('#').substringBefore('?')
        value = runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)
    }
    return value
}

internal fun resolveRemoteArtifactPath(path: String, workspace: String): String? {
    val clean = normalizeArtifactTarget(path)
    if (clean.isBlank()) return null
    if (clean.startsWith('/') || clean.startsWith("~/") || clean.startsWith("\\\\") ||
        Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(clean)) return clean
    // Public URLs are links, never remote filesystem paths.
    if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(clean)) return null
    val root = workspace.trim()
    if (root.isBlank()) return null
    return joinServerPath(root, clean.removePrefix("./").removePrefix(".\\"))
}

/** Recover source links first, then locate bare names only inside the recorded workspace. */
internal class ArtifactFileReader(private val client: HermesApiClient) {
    fun read(item: RecentArtifact, sourceSession: HermesSession?, cachedMessages: List<ChatMessage>): WorkspaceDocument {
        require(sourceSession == null || sourceSession.profile == item.profile) { uiText(R.string.ui_0049, "文件与来源档案不一致") }
        var workspace = item.workspacePath.ifBlank { sourceSession?.workspacePath.orEmpty() }
        val tried = linkedSetOf<String>()
        fun readPath(raw: String, base: String = workspace): WorkspaceDocument? {
            var path = resolveRemoteArtifactPath(raw, base)
            if (path == null && base.isBlank() && !raw.contains("://")) {
                workspace = client.initialWorkspaceForProfile(item.profile).path
                path = resolveRemoteArtifactPath(raw, workspace)
            }
            if (path == null || !tried.add(path)) return null
            return try {
                client.readWorkspaceDocumentForProfile(path, item.profile)
            } catch (error: ApiException) {
                if (error.statusCode != 404) throw error
                null
            }
        }
        fun recover(messages: List<ChatMessage>): WorkspaceDocument? {
            val source = if (item.messageId.isBlank()) messages else messages.filter { it.id == item.messageId }
            val candidates = source.flatMap { ChatInsightParser.artifactsFromText(it.content) }
                .filter { it.name == item.name || truncatedExtensionMatch(item.name, it.name) }
                .map { it.path }.distinct().let { paths ->
                    // A reply can contain both a filename label and its full location.
                    paths.filterNot(::isBareArtifactName).ifEmpty { paths }
                }
            // Two same-name files are ambiguous; leave the choice to the user.
            if (candidates.size != 1) return null
            val raw = candidates.single()
            readPath(raw)?.let { return it }
            val currentSourceRoot = sourceSession?.workspacePath.orEmpty()
            // Only after the recorded location is missing, consider the same source
            // conversation's updated directory and its exact relative link.
            if (currentSourceRoot.isNotBlank() && currentSourceRoot != workspace && resolveRemoteArtifactPath(raw, "") == null) {
                return readPath(raw, currentSourceRoot)
            }
            return null
        }
        if (isBareArtifactName(item.path)) recover(cachedMessages)?.let { return it }
        // A previously verified location is faster and more precise than its original filename-only link.
        readPath(item.path)?.let { return it }
        if (item.sourcePath.isNotBlank()) readPath(item.sourcePath)?.let { return it }
        recover(cachedMessages)?.let { return it }
        val hasSource = item.messageId.isNotBlank() && cachedMessages.any { it.id == item.messageId }
        if (item.sessionId.isNotBlank() && !hasSource) {
            val messages = try {
                client.loadArtifactSourceMessages(item.sessionId, item.profile, item.messageId)
            } catch (error: ApiException) {
                if (error.statusCode != 404) throw error
                emptyList()
            }
            recover(messages)?.let { return it }
        }
        val original = item.sourcePath.ifBlank { item.path }
        if (isBareArtifactName(original)) {
            val name = artifactFileName(normalizeArtifactTarget(original))
            val search = ArtifactDiscovery({ path, timeout -> client.listWorkspaceForProfile(path, item.profile, timeout) })
                .find(workspace, name)
            if (search.complete && search.candidates.size == 1) readPath(search.candidates.single().path)?.let { return it }
            val message = when {
                search.candidates.size > 1 -> "找到多个同名文件，请根据所在目录选择要打开的文件。"
                !search.complete -> search.reason
                search.candidates.isNotEmpty() -> "找到了文件位置，但读取时文件已不可用，请刷新目录后重试。"
                else -> "在这段会话的工作目录及子目录中未找到同名文件。请核对实际保存位置，或粘贴完整路径打开。"
            }
            val details = buildString {
                appendLine("文件：${item.name}")
                appendLine("Profile：${item.profile}")
                appendLine("来源会话：${item.sessionTitle} (${item.sessionId})")
                appendLine("原始链接：$original")
                appendLine("查找范围：$workspace")
                appendLine("查找完成：${search.complete}")
                appendLine(message)
                if (!search.complete && search.reason.isNotBlank() && search.reason != message) appendLine(search.reason)
                appendLine("已尝试读取：")
                tried.forEach { appendLine(it) }
                if (search.candidates.isNotEmpty()) {
                    appendLine("找到的位置：")
                    search.candidates.forEach { appendLine(it.path) }
                }
            }
            throw ArtifactLookupException(message, search.candidates, details)
        }
        throw ApiException(404, uiText(R.string.ui_0050, "找不到「%1\$s」。文件可能已移动或删除，请在项目文件中重新选择，或回到来源对话确认位置。", item.name))
    }

    private fun truncatedExtensionMatch(old: String, current: String): Boolean =
        (old.endsWith(".doc", true) || old.endsWith(".xls", true) || old.endsWith(".ppt", true)) && current == old + "x" ||
            old.endsWith(".htm", true) && current == old + "l"
}
