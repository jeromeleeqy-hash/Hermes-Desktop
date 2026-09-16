package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*

internal fun ChatMessage.recoverySignature(): String = buildString {
    append(createdAt)
    append('|')
    append(content.trim())
    append('|')
    append(reasoning.trim())
    images.forEach { append('|').append(it.source) }
}

internal fun mergeInterimAssistantText(streamed: String, interim: String): String {
    val live = streamed.trim()
    val preview = interim.trim()
    if (live.isBlank()) return preview
    if (preview.isBlank()) return live
    val normalizedLive = live.normalizeStreamText()
    val normalizedPreview = preview.normalizeStreamText()
    return when {
        normalizedLive == normalizedPreview -> live
        normalizedLive.endsWith(normalizedPreview) -> live
        normalizedPreview.startsWith(normalizedLive) -> preview
        else -> "$live\n\n$preview"
    }
}

internal fun mergeCompletedAssistantText(
    streamed: String,
    completed: String,
    responsePreviewed: Boolean = false,
): String {
    val live = streamed.trim()
    val final = completed.trim()
    val normalizedLive = live.normalizeStreamText()
    val normalizedFinal = final.normalizeStreamText()
    return when {
        live.isBlank() -> final
        final.isBlank() -> live
        normalizedFinal == normalizedLive -> live
        normalizedFinal.startsWith(normalizedLive) -> final
        normalizedLive.startsWith(normalizedFinal) || normalizedLive.endsWith(normalizedFinal) -> live
        responsePreviewed && normalizedLive.contains(normalizedFinal) -> live
        else -> "$live\n\n$final"
    }
}

private fun String.normalizeStreamText(): String = replace(Regex("\\s+"), " ").trim()

