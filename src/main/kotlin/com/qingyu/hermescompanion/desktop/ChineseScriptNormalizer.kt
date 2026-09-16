package com.qingyu.hermescompanion.desktop

import com.ibm.icu.text.Transliterator

internal fun chineseScriptTransliteratorId(script: String): String? = when (script.lowercase()) {
    "simplified", "zh-hans", "zh-cn", "zh-sg" -> "Traditional-Simplified"
    "traditional", "zh-hant", "zh-tw", "zh-hk", "zh-mo" -> "Simplified-Traditional"
    else -> null
}

internal fun voiceRecognitionLanguage(language: String, transcriptScript: String): String {
    if (!language.startsWith("zh", ignoreCase = true)) return language
    return when (transcriptScript.lowercase()) {
        "simplified", "zh-hans", "zh-cn", "zh-sg" -> "zh-CN"
        "traditional", "zh-hant", "zh-tw", "zh-hk", "zh-mo" -> "zh-TW"
        else -> language
    }
}

internal fun normalizeVoiceTranscript(text: String, transcriptScript: String): String =
    normalizeVoiceTranscript(text, transcriptScript, ::androidChineseTransliterate)

internal fun normalizeVoiceTranscript(
    text: String,
    transcriptScript: String,
    transliterate: (String, String) -> String,
): String {
    val transformId = chineseScriptTransliteratorId(transcriptScript) ?: return text
    if (text.isBlank()) return text
    return runCatching { transliterate(transformId, text) }.getOrDefault(text)
}

private fun androidChineseTransliterate(transformId: String, text: String): String {
    return Transliterator.getInstance(transformId).transliterate(text)
}
