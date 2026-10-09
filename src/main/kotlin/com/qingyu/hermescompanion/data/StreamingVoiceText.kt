package com.qingyu.hermescompanion.data

/** One turn only. Only stable complete sentences leave this buffer; repeated final events are harmless. */
class StreamingVoiceText {
    private var committed = ""
    private var closed = false
    private var changedPrefix = false
    fun update(markdown: String, finished: Boolean = false): List<String> {
        if (closed) return emptyList()
        val safe = markdown.substringBeforeUnclosedFence().substringBeforeUnclosedLink()
        val plain = spokenReply(safe).trim()
        if (!plain.startsWith(committed)) {
            // A recovery/revision may replace text already read aloud. Keep the corrected text on screen.
            changedPrefix = true
        }
        if (changedPrefix) { if (finished) closed = true; return emptyList() }
        val emitted = mutableListOf<String>()
        var cursor = committed.length
        var start = cursor
        while (cursor < plain.length) {
            val c = plain[cursor]
            val terminal = c in "。！？!?\n" ||
                (c == '.' && cursor > 0 && !plain[cursor - 1].isDigit() &&
                    (cursor + 1 < plain.length && plain[cursor + 1].isWhitespace() || finished && cursor == plain.lastIndex))
            val breath = cursor - start >= 72 && c in "，,；; "
            if (terminal || breath) {
                val sentence = plain.substring(start, cursor + 1).trim()
                if (sentence.isNotBlank()) emitted += sentence
                start = cursor + 1
            }
            cursor++
        }
        if (finished && start < plain.length) { emitted += speechChunks(plain.substring(start).trim(), 180); start = plain.length }
        committed = plain.substring(0, start)
        if (finished) closed = true
        return emitted
    }
}

private fun String.substringBeforeUnclosedFence(): String {
    val positions = Regex("```").findAll(this).map { it.range.first }.toList()
    return if (positions.size % 2 == 1) substring(0, positions.last()) else this
}
private fun String.substringBeforeUnclosedLink(): String {
    val opening = lastIndexOf('[')
    if (opening < 0) return this
    val tail = substring(opening)
    return if (!tail.contains(']') || tail.contains("](") && !tail.contains(')')) substring(0, opening) else this
}

internal fun voicePauseMillis(setting: String, sensitivity: String): Long = when (setting) {
    "quick" -> if (sensitivity == "noisy") 1_000L else 700L
    "patient" -> 1_600L
    else -> if (sensitivity == "noisy") 1_250L else 1_000L
}

internal fun spokenConversationPrompt(text: String): String = if (text.trimStart().startsWith('/')) text else """
    $text

    [当前交互方式：连续语音。请用自然、简短、适合听的口语回答，先说有用的第一句话，日常交流通常一两句即可；复杂问题再按需要展开。无需每次客套确认，不要朗读 Markdown、长网址或工具参数。涉及记忆、文件或任务时，可以先说明正在处理，但只有工具确认成功后才能说已记好或已完成。保留必要的核实、授权和复杂推理，不能为了快速回应跳过。]
""".trimIndent()
