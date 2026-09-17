package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Reconcile external edits before rendering, never from a delayed draft echo. */
@Composable
internal fun rememberComposerValue(key: String, draft: String): MutableState<TextFieldValue> {
    val field = remember(key) { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    // Local edits update field and the controller together. Matching text must preserve
    // selection and IME composition; an external replacement is applied synchronously.
    if (field.value.text != draft) {
        field.value = TextFieldValue(draft, TextRange(draft.length))
    }
    return field
}
