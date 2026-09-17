@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.input.*
import com.qingyu.hermescompanion.platform.DesktopHost

/** Compose 1.10.1's TextFieldValue adapter reports each edit in an IME update
 * separately. CommitText("") then SetComposingText exposes an empty editor and
 * null composition between two halves of a single native update. Keep that
 * update atomic, as in the upstream LegacyPlatformTextInputServiceAdapter fix.
 * Use the public input-session interceptor so no dependency classes are replaced.
 */
internal fun atomicComposerImeRequest(request: PlatformTextInputMethodRequest): PlatformTextInputMethodRequest =
    object : PlatformTextInputMethodRequest by request {
        override val editText: (TextEditingScope.() -> Unit) -> Unit = { edit ->
            val commands = mutableListOf<EditCommand>()
            val scope = object : TextEditingScope {
                override fun deleteSurroundingTextInCodePoints(lengthBeforeCursor: Int, lengthAfterCursor: Int) {
                    commands += DeleteSurroundingTextInCodePointsCommand(lengthBeforeCursor, lengthAfterCursor)
                }
                override fun commitText(text: CharSequence, newCursorPosition: Int) {
                    commands += CommitTextCommand(text.toString(), newCursorPosition)
                }
                override fun setComposingText(text: CharSequence, newCursorPosition: Int) {
                    commands += SetComposingTextCommand(text.toString(), newCursorPosition)
                }
                override fun finishComposingText() { commands += FinishComposingTextCommand() }
            }
            scope.edit()
            if (commands.isNotEmpty()) request.onEditCommand(commands)
        }
    }

private val ComposerImeInterceptor = PlatformTextInputInterceptor { request, next ->
    next.startInputMethod(atomicComposerImeRequest(request))
}

@Composable
internal fun ComposerImeSession(content: @Composable () -> Unit) {
    if (DesktopHost.isMac) InterceptPlatformTextInput(ComposerImeInterceptor, content)
    else content()
}

/** The native IME owns Backspace while composing. Never edit its preedit here. */
internal fun Modifier.preserveMacImeComposition(value: () -> TextFieldValue): Modifier =
    onPreviewKeyEvent { event ->
        DesktopHost.isMac && value().composition != null &&
            event.key == Key.Backspace && event.type == KeyEventType.KeyDown
    }
