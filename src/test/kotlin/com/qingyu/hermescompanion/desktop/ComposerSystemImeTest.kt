@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.qingyu.hermescompanion.platform.DesktopHost
import java.awt.*
import java.awt.event.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test

/** Opt-in test: Robot -> actual macOS input method -> Cocoa -> AWT -> composer.
 * A missing/unselectable proprietary input method is SKIPPED, never a pass. */
class ComposerSystemImeTest {
    @Test fun applePinyinDeletesOneLetterAndKeepsSurroundingText() = verify("com.apple.inputmethod.SCIM.ITABC", false)
    @Test fun weChatPinyinDeletesOneLetterAndKeepsSurroundingText() = verify("com.tencent.inputmethod.wetype.pinyin", true)
    private fun verify(source:String,optional:Boolean) {
        assumeTrue(DesktopHost.isMac && java.lang.Boolean.getBoolean("hermes.systemImeTest"))
        assumeFalse(GraphicsEnvironment.isHeadless())
        runBlocking {
            Toolkit.getDefaultToolkit()
            val weType=java.io.File("/Library/Input Methods/WeType.app")
            if(weType.isDirectory)ImeCocoa.sources(weType.absolutePath)
            val original=ImeCocoa.sources("").lineSequence().first{it.startsWith("CURRENT ")}.removePrefix("CURRENT ")
            val robot=Robot().apply {autoDelay=60}
            var field=TextFieldValue("前文后文",TextRange(2))
            val updates=mutableListOf<TextFieldValue>()
            lateinit var editorRef:MutableState<TextFieldValue>
            lateinit var window:ComposeWindow
            val focus=FocusRequester()
            withContext(Dispatchers.Swing) {
                window=ComposeWindow().apply {setSize(620,280);setLocation(100,100);title="Hermes system IME verification"}
                window.setContent {
                    var draft by remember{mutableStateOf("前文后文")}
                    val editor=rememberComposerValue("system-ime-test",draft)
                    editorRef=editor
                    ComposerImeSession {
                        BasicTextField(editor.value,{next->editor.value=next;draft=next.text;field=next;updates+=next},
                            Modifier.fillMaxSize().padding(20.dp).focusRequester(focus).preserveMacImeComposition {editor.value})
                    }
                    LaunchedEffect(Unit) {focus.requestFocus()}
                }
                window.isVisible=true;window.toFront()
            }
            try {
                delay(900)
                val selected=ImeCocoa.sources(source)
                println(selected.lineSequence().filter{it.startsWith("CURRENT")||it.startsWith("ENABLE")||it.startsWith("SELECTABLE")}.joinToString("\n"))
                if(optional)assumeTrue("WeType is not available/selectable in this session; no WeChat validation claimed",selected.contains("CURRENT $source\n"))
                else assertTrue("System Pinyin must really be selected",selected.contains("CURRENT $source\n"))
                delay(1200)
                withContext(Dispatchers.Swing){window.toFront();focus.requestFocus()}
                delay(250)
                robot.mouseMove(123,150);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                delay(250)
                withContext(Dispatchers.Swing){
                    assertNotNull(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
                    field=TextFieldValue("前文后文",TextRange(2));editorRef.value=field
                }
                delay(250)
                suspend fun key(code:Int) {robot.keyPress(code);robot.keyRelease(code);delay(180)}
                suspend fun type(text:String){for(ch in text)key(KeyEvent.getExtendedKeyCodeForChar(ch.code))}
                fun normalized(value:TextFieldValue):String {
                    val comp=value.composition ?: return ""
                    return value.text.substring(comp.min,comp.max).replace(" ","").replace("'","")
                }
                val pinyin="nihaoshijie"
                type(pinyin);delay(300)
                withContext(Dispatchers.Swing){assertNotNull("Input method must produce actual preedit",field.composition);assertEquals(pinyin,normalized(field))}
                for(length in pinyin.length-1 downTo 1) {
                    withContext(Dispatchers.Swing){updates.clear()}
                    key(KeyEvent.VK_BACK_SPACE)
                    withContext(Dispatchers.Swing){
                        assertTrue("Native update must not temporarily clear the composition",updates.isNotEmpty() && updates.all{it.composition!=null})
                        assertEquals(pinyin.take(length),normalized(field))
                        assertTrue(field.text.startsWith("前文")&&field.text.endsWith("后文"))
                        println("$source remaining=$length composition=${field.composition}")
                    }
                }
                key(KeyEvent.VK_BACK_SPACE)
                withContext(Dispatchers.Swing){assertEquals("前文后文",field.text);assertNull(field.composition)}
                type("nihao");key(KeyEvent.VK_SPACE);delay(250)
                withContext(Dispatchers.Swing){assertEquals("前文你好后文",field.text);assertNull(field.composition)}
                key(KeyEvent.VK_BACK_SPACE)
                withContext(Dispatchers.Swing){assertEquals("前文你后文",field.text)}
            } finally {
                withContext(Dispatchers.Swing){window.dispose()}
                ImeCocoa.sources(original)
            }
        }
    }
}
