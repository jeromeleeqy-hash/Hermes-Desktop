package com.qingyu.hermescompanion.i18n

import org.json.JSONObject
import java.util.Locale

@Volatile var desktopLanguage = "zh"
private fun catalog(name: String) = JSONObject(
    object {}.javaClass.getResourceAsStream("/$name.json")?.bufferedReader()?.use { it.readText() } ?: "{}"
)
private val english by lazy { catalog("messages-en") }
private val traditional by lazy { catalog("messages-zh-TW") }
private val traditionalLabels by lazy { catalog("desktop-zh-TW") }

fun resolveDesktopLanguage(value: String, locale: Locale = Locale.getDefault()): String = when (value) {
    "en", "zh", "zh-TW" -> value
    else -> when {
        locale.language != "zh" -> "en"
        locale.script == "Hans" -> "zh"
        locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO") -> "zh-TW"
        else -> "zh"
    }
}

/** Translate application-owned labels only. Unknown text, paths and server content stay intact. */
fun traditionalLabel(text: String): String = traditionalLabels.optString(text, text)

/** Literal parts are translated at the call site; only the selected template evaluates its values. */
inline fun localizedText(simplified: () -> String, traditional: () -> String): String =
    if (desktopLanguage == "zh-TW") traditional() else simplified()

fun uiText(id: Int, fallback: String, vararg args: Any?): String {
    val template = when (desktopLanguage) {
        "en" -> english.optString(id.toString()).ifBlank { fallback }
        "zh-TW" -> traditional.optString(id.toString()).ifBlank { traditionalLabel(fallback) }
        else -> fallback
    }
    return if (args.isEmpty()) template else String.format(Locale.getDefault(), template, *args)
}
