package com.qingyu.hermescompanion.i18n

import org.json.JSONObject
import java.util.Locale

@Volatile var desktopLanguage = "zh"
private val english by lazy {
    val text = object {}.javaClass.getResourceAsStream("/messages-en.json")?.bufferedReader()?.use { it.readText() } ?: "{}"
    JSONObject(text)
}
fun uiText(id: Int, fallback: String, vararg args: Any?): String {
    val template = if (desktopLanguage == "en") english.optString(id.toString()).ifBlank { fallback } else fallback
    return if (args.isEmpty()) template else String.format(Locale.getDefault(), template, *args)
}
