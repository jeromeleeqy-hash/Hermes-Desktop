@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.qingyu.hermescompanion.platform.DesktopHost
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.event.InputMethodEvent
import java.awt.event.KeyEvent
import java.awt.font.TextHitInfo
import java.text.AttributedString
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test

/** Real AWT -> Compose input events. This models an IME forwarding a raw key;
 * it does not claim to run WeChat's proprietary input method itself. */
class ComposerNativeImeTest {
    @Test fun awtPreeditUpdatesAndForwardedBackspacePreserveComposition() {
        assumeTrue(DesktopHost.isMac)
        assumeFalse(GraphicsEnvironment.isHeadless())
        runBlocking(Dispatchers.Swing) {
            var field by mutableStateOf(TextFieldValue("前文后文",TextRange(2)))
            val focus=FocusRequester()
            val window=ComposeWindow().apply {setSize(620,240);setLocation(80,80);title="Hermes IME regression"}
            try {
                window.setContent {
                    BasicTextField(field,{field=it},Modifier.fillMaxSize().padding(20.dp)
                        .preserveMacImeComposition {field}.focusRequester(focus))
                    LaunchedEffect(Unit) {focus.requestFocus()}
                }
                window.isVisible=true;window.toFront();window.requestFocus()
                delay(800);focus.requestFocus();delay(250)
                val target=KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                assertNotNull("Native Compose editor must have focus",target)
                fun ime(text:String,committed:Int=0) {
                    target.dispatchEvent(InputMethodEvent(target,InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        AttributedString(text).iterator,committed,TextHitInfo.leading(text.length-committed),null))
                }
                fun rawBackspace() {
                    target.dispatchEvent(KeyEvent(target,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,KeyEvent.VK_BACK_SPACE,'\b'))
                    target.dispatchEvent(KeyEvent(target,KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,KeyEvent.VK_BACK_SPACE,'\b'))
                }
                ime("nihaoshijie");delay(120)
                assertEquals("前文nihaoshijie后文",field.text)
                assertNotNull(field.composition)
                // Both possible orderings: the forwarded key before/after the IME update.
                for (length in 9 downTo 1) {
                    val before=field
                    rawBackspace();delay(80);assertEquals("Raw key must not cancel preedit",before,field)
                    val remaining="nihaoshijie".take(length)
                    ime(remaining);delay(80)
                    val after=field
                    assertEquals("前文${remaining}后文",after.text)
                    assertNotNull(after.composition)
                    rawBackspace();delay(80);assertEquals(after,field)
                }
                ime("");delay(120)
                assertEquals("前文后文",field.text);assertNull(field.composition)
                ime("nihao");delay(100);ime("你好",2);delay(100)
                assertEquals("前文你好后文",field.text);assertNull(field.composition)
                rawBackspace();delay(100)
                assertEquals("前文你后文",field.text)
            } finally {window.dispose()}
        }
    }
}
