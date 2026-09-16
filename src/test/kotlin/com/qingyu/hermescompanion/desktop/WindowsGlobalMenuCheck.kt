package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.awt.ComposeWindow
import com.qingyu.hermescompanion.platform.DesktopHost
import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import java.awt.event.*
import java.io.File
import java.nio.file.Files
import javax.swing.SwingUtilities

/** Run on Windows, on an unlocked test desktop. The target lives in a separate JVM: an AWT
 * event listener in Hermes cannot see its clicks. Disabling focus rules out a false-positive
 * result from windowLostFocus, the mechanism that missed the original reported bug. */
object WindowsGlobalMenuCheck {
    @JvmStatic fun main(args:Array<String>) {
        if(!DesktopHost.isWindows){println("SKIP: global Windows mouse hook requires Windows");return}
        runBlocking(Dispatchers.Swing) {
            suspend fun ready(label:String,predicate:()->Boolean){repeat(120){if(predicate())return;delay(25)};check(predicate()){label}}
            val dir=Files.createTempDirectory("hermes-global-menu182")
            val c=DesktopController(true,SecureConfigStore(dir.resolve("config")){ByteArray(32){8}},autoConnect=false)
            val menu=DesktopMenuHost(c,"点击验证"){listOf(DesktopMenuAction("open","打开 Hermes","panel"){})}
            val child=ProcessBuilder(File(System.getProperty("java.home"),"bin/java.exe").path,"-cp",System.getProperty("java.class.path"),
                OutsideMenuTarget::class.java.name,dir.toString()).redirectErrorStream(true).redirectOutput(dir.resolve("target.log").toFile()).start()
            try {
                ready("Separate process did not open"){Files.exists(dir.resolve("ready"))}
                val work=desktopWorkAreas().first()
                val anchor=Point(work.x+work.width-40,work.y+work.height-40)
                menu.show(anchor);menu.dismiss()
                val window=Window.getWindows().filterIsInstance<ComposeWindow>().first {it.title=="Hermes · 点击验证"}
                window.focusableWindowState=false
                val robot=Robot()
                repeat(3) {attempt->
                    menu.show(anchor);delay(200)
                    check(menu.visible&&!window.isFocused)
                    robot.mouseMove(180,160);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                    ready("Outside-process press failed to dismiss"){!menu.visible}
                    ready("Original click was swallowed"){runCatching {dir.resolve("presses").toFile().readText().toInt()>=attempt+1}.getOrDefault(false)}
                    ready("Mouse hook leaked after closing"){Thread.getAllStackTraces().keys.none {it.isAlive&&it.name=="Hermes-menu-mouse"}}
                }
                println("PASS: unfocused popup, outside-JVM click, click reaches target, reopen, hook teardown")
            }finally {menu.close();c.close();child.destroy();dir.toFile().deleteRecursively()}
        }
        kotlin.system.exitProcess(0)
    }
}

object OutsideMenuTarget {
    @JvmStatic fun main(args:Array<String>) {
        SwingUtilities.invokeLater {
            val dir=File(args.single());var presses=0
            Frame("Hermes separate-process click target").apply {
                bounds=Rectangle(80,80,300,240)
                add(Panel().apply {background=Color.LIGHT_GRAY;addMouseListener(object:MouseAdapter(){override fun mousePressed(e:MouseEvent){
                    if(e.button==MouseEvent.BUTTON1)File(dir,"presses").writeText((++presses).toString())
                }})})
                isVisible=true;File(dir,"ready").writeText("ready")
            }
        }
    }
}
