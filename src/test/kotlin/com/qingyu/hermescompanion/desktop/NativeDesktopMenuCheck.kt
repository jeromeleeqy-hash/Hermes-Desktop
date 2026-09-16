@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.semantics.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import java.awt.event.*
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/** Actual AWT focus, pointer and keyboard events; no main window is needed to open either menu. */
object NativeDesktopMenuCheck {
    private fun nodes(w:ComposeWindow):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        return w.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
    }
    private suspend fun ready(label:String="event",predicate:()->Boolean){repeat(160){if(predicate())return;delay(25)};check(predicate()){"Desktop menu timed out: $label; windows="+Window.getWindows().filterIsInstance<Frame>().map {"${it.title}: visible=${it.isVisible}, focused=${it.isFocused}"}}}
    private suspend fun click(robot:Robot,w:ComposeWindow,id:String) {
        val node=nodes(w).first {it.config.getOrNull(SemanticsProperties.TestTag)=="desktop-menu:$id"}
        val center=node.boundsInRoot.center
        val scale=w.graphicsConfiguration.defaultTransform.scaleX
        robot.mouseMove(w.x+(center.x/scale).toInt(),w.y+(center.y/scale).toInt())
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);delay(40);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(150)
    }
    @JvmStatic fun main(args:Array<String>) {
        try {runBlocking(Dispatchers.Swing) {
            val out=File(args.firstOrNull()?:"build/verification181-hotfix/native").apply {mkdirs()}
            val dir=Files.createTempDirectory("hermes-menu181")
            val c=DesktopController(true,SecureConfigStore(dir){ByteArray(32){11}},autoConnect=false)
            var restored=0;var exited=0
            val tray=DesktopMenuHost(c,"桌面助手") {listOf(
                DesktopMenuAction("open","打开 Hermes","panel"){restored++},
                DesktopMenuAction("floating","桌面悬浮球","pin",checked=c.floatingAssistantEnabled){c.floatingAssistantEnabled=!c.floatingAssistantEnabled},
                DesktopMenuAction("settings","设置","settings"){},
                DesktopMenuAction("exit","退出 Hermes","close",dividerBefore=true){exited++},
            )}
            var floating:DesktopFloatingHost?=null
            var other:Frame?=null
            try {
                c.reduceMotion=true;c.connected=true;c.changeLanguage("zh")
                val robot=Robot()
                tray.show(Point(1480,1010));ready {tray.visible}
                val window=Window.getWindows().filterIsInstance<ComposeWindow>().first {it.title=="Hermes · 桌面助手"}
                ready {nodes(window).any {it.config.getOrNull(SemanticsProperties.TestTag)=="desktop-menu:exit"}}
                check(window.owner==null&&window.isAlwaysOnTop&&window.type==Window.Type.UTILITY)
                check(desktopWorkAreas().any {it.contains(window.bounds)})
                delay(300);ImageIO.write(robot.createScreenCapture(window.bounds),"png",File(out,"Tray-Menu.png"))
                click(robot,window,"floating");check(!tray.visible);check(!c.floatingAssistantEnabled);println("PASS toggle")
                tray.show(Point(1480,1010));ready("reopen focus") {window.isFocused}
                check(Window.getWindows().filterIsInstance<ComposeWindow>().count {it.isDisplayable&&it.title==window.title}==1)
                ready("unchecked state rendered") {nodes(window).firstOrNull {it.config.getOrNull(SemanticsProperties.TestTag)=="desktop-menu:floating"}?.config?.getOrNull(SemanticsProperties.ToggleableState)==androidx.compose.ui.state.ToggleableState.Off}
                robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE);ready("Escape") {!tray.visible};println("PASS Escape")
                tray.show(Point(1480,1010));ready {window.isFocused};delay(100)
                robot.keyPress(KeyEvent.VK_DOWN);robot.keyRelease(KeyEvent.VK_DOWN)
                robot.keyPress(KeyEvent.VK_ENTER);robot.keyRelease(KeyEvent.VK_ENTER);ready("keyboard open") {restored==1};check(!tray.visible);println("PASS keyboard open")
                tray.show(Point(1480,1010));ready {window.isFocused}
                other=Frame("Outside menu").apply {bounds=Rectangle(60,80,280,180);add(TextField("Another window"));isVisible=true}
                delay(150);robot.mouseMove(160,150);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                ready("outside click") {!tray.visible};other.dispose();other=null;println("PASS outside click")

                c.skin="液态玻璃";c.appearance="深色";c.textScale=1.3f
                tray.show(Point(1480,1010));ready {window.isFocused};delay(400)
                ImageIO.write(robot.createScreenCapture(window.bounds),"png",File(out,"Tray-Menu-Dark-Large.png"))
                click(robot,window,"exit");check(exited==1&&!tray.visible)

                c.appearance="浅色";c.skin="安静耐看";c.textScale=1f;c.floatingAssistantEnabled=true
                floating=DesktopFloatingHost(c);floating.sync();delay(250)
                val ball=Window.getWindows().first {it.isVisible&&it.width==84}
                // Keep the pointer target outside the tray popup at every display scale.
                val work=desktopWorkAreas().first();ball.location=Point(work.x+30,work.y+30)
                tray.show(Point(1480,1010));delay(150)
                robot.mouseMove(ball.x+42,ball.y+42);robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
                val popupTitle="Hermes · 悬浮助手"
                ready("ball right-click menu") {Window.getWindows().any {it.isVisible&&it is ComposeWindow&&it.title==popupTitle}}
                val popup=Window.getWindows().filterIsInstance<ComposeWindow>().first {it.isVisible&&it.title==popupTitle}
                ready {nodes(popup).any {it.config.getOrNull(SemanticsProperties.TestTag)=="desktop-menu:ask"}}
                check(!tray.visible);delay(250)
                ImageIO.write(robot.createScreenCapture(popup.bounds),"png",File(out,"Floating-Menu.png"))
                click(robot,popup,"ask");ready {c.companion.panelOpen};check(!popup.isVisible)
                c.companion.close();floating.sync()
                robot.mouseMove(ball.x+42,ball.y+42);robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
                ready {popup.isVisible};delay(100)
                click(robot,popup,"hide-floating");ready {!ball.isVisible};check(!popup.isVisible&&!c.floatingAssistantEnabled)
                println("PASS: tray action/check state, Escape, keyboard open, outside-click dismissal, hidden-main operation, theme/font scale, real ball right-click, single active menu, ask and hide actions")
            }finally {other?.dispose();floating?.close();tray.close();c.close();dir.toFile().deleteRecursively();FxRuntime.shutdown()}
        };kotlin.system.exitProcess(0)}catch(e:Throwable){e.printStackTrace();kotlin.system.exitProcess(1)}
    }
}
