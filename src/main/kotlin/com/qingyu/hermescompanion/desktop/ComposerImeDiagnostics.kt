package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.qingyu.hermescompanion.BuildConfig
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.*
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

private var imeDiagnosticWindow: ComposeWindow? = null

/** Explicit, local diagnostic window. Record event metadata only, never typed text. */
internal fun showComposerImeDiagnostics(controller: DesktopController) {
    imeDiagnosticWindow?.let {it.toFront();return}
    val events=mutableStateListOf<String>()
    val started=System.nanoTime()
    fun record(message:String) {
        if(events.size>=500)events.removeAt(0)
        events += "${(System.nanoTime()-started)/1_000_000} ms $message"
    }
    val header="Hermes ${BuildConfig.VERSION_NAME}\nOS ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}\nJava ${System.getProperty("java.runtime.version")} ${System.getProperty("java.vendor")}\nWeType ${weTypeVersion()}\n"
    val window=ComposeWindow().apply {
        title="Hermes 输入法诊断";setSize(680,350);setLocationRelativeTo(null)
        defaultCloseOperation=WindowConstants.DISPOSE_ON_CLOSE
    }
    imeDiagnosticWindow=window
    val observer=AWTEventListener {event ->
        val component=event.source as? Component
        if(component!=null && SwingUtilities.getWindowAncestor(component)===window) {
            when(event) {
                is InputMethodEvent ->record("IME id=${event.id} length=${event.text?.let {it.endIndex-it.beginIndex}?:0} committed=${event.committedCharacterCount} caret=${event.caret?.insertionIndex}")
                is KeyEvent ->if(event.keyCode in setOf(KeyEvent.VK_BACK_SPACE,KeyEvent.VK_DELETE,KeyEvent.VK_ENTER,KeyEvent.VK_ESCAPE))
                    record("KEY id=${event.id} code=${event.keyCode} modifiers=${event.modifiersEx}")
                is FocusEvent ->record("FOCUS id=${event.id} temporary=${event.isTemporary}")
            }
        }
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(observer,AWTEvent.INPUT_METHOD_EVENT_MASK or AWTEvent.KEY_EVENT_MASK or AWTEvent.FOCUS_EVENT_MASK)
    window.addWindowListener(object:WindowAdapter(){override fun windowClosed(event:WindowEvent){
        Toolkit.getDefaultToolkit().removeAWTEventListener(observer)
        if(imeDiagnosticWindow===window)imeDiagnosticWindow=null
        events.clear()
    }})
    window.setContent {
        HermesTheme(controller) {
            var value by remember {mutableStateOf(TextFieldValue())}
            var copied by remember {mutableStateOf(false)}
            Surface {
                Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("用微信输入法输入 nihaoshijie，不选词，按一次退格。",style=MaterialTheme.typography.titleMedium)
                    Text("只记录本窗口的事件顺序、文字长度和光标范围，不记录输入内容。",style=MaterialTheme.typography.bodySmall)
                    Surface(Modifier.weight(1f).fillMaxWidth(),shape=MaterialTheme.shapes.small,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                        ComposerImeSession {
                            BasicTextField(value,{next->value=next;record("VALUE length=${next.text.length} selection=${next.selection} composition=${next.composition}")},
                                Modifier.fillMaxSize().padding(12.dp).preserveMacImeComposition {value},
                                textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary))
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("已记录 ${events.size} 条事件",style=MaterialTheme.typography.bodySmall)
                        Button(onClick={Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(header+events.joinToString("\n")),null);copied=true}) {
                            Text(if(copied)"已复制，可粘贴反馈"else"复制诊断结果")
                        }
                    }
                }
            }
        }
    }
    window.isVisible=true;window.toFront()
}

private fun weTypeVersion():String = runCatching {
    val process=ProcessBuilder("/usr/libexec/PlistBuddy","-c","Print:CFBundleShortVersionString","/Library/Input Methods/WeType.app/Contents/Info.plist").redirectErrorStream(true).start()
    if(!process.waitFor(2,TimeUnit.SECONDS)){process.destroyForcibly();return@runCatching "unavailable"}
    if(process.exitValue()==0)process.inputStream.bufferedReader().use {it.readText().trim().take(80)} else "unavailable"
}.getOrDefault("unavailable")
