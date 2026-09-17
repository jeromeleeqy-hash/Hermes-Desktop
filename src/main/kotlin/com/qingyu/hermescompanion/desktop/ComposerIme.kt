package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import com.qingyu.hermescompanion.platform.DesktopHost

/** Native IME events have already reached AWT before Compose receives these keys.
 * Do not let BasicTextField finish composing and delete the marked selection a
 * second time when a macOS IME also forwards Backspace to the application.
 * The IME remains responsible for editing its preedit string; never edit it here.
 */
internal fun Modifier.preserveMacImeComposition(value: () -> TextFieldValue): Modifier =
    onPreviewKeyEvent { event ->
        DesktopHost.isMac && value().composition != null &&
            event.key == Key.Backspace && event.type == KeyEventType.KeyDown
    }
