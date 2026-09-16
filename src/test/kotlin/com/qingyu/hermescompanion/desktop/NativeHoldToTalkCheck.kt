package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import java.awt.event.InputEvent
import java.nio.file.Files
import javax.swing.JWindow

/** Physical AWT mouse capture check, independent of the microphone/backend flow tests. */
object NativeHoldToTalkCheck {
    @JvmStatic fun main(args:Array<String>) {
        try {runBlocking(Dispatchers.Swing) {
            val dir=Files.createTempDirectory("hermes-native-hold")
            val c=DesktopController(true,SecureConfigStore(dir){ByteArray(32){29}},autoConnect=false)
            val host=DesktopFloatingHost(c)
            val robot=Robot()
            try {
                c.connected=true;c.floatingAssistantEnabled=true;host.sync();delay(300)
                val ball=Window.getWindows().filterIsInstance<JWindow>().single {it.isVisible&&it.width==84}
                val origin=Point(ball.location)
                robot.mouseMove(ball.x+42,ball.y+42);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                withTimeout(8000){while(!c.companion.holdingToTalk||!c.voice.active)delay(30)}
                check(c.voice.holdToTalk);check(c.companion.panelOpen)
                check(Window.getWindows().single {it.isVisible&&it.titleOrEmpty()=="Hermes · 随时问"}.isFocusableWindow.not())
                robot.mouseMove(ball.x+130,ball.y+45);delay(100);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                withTimeout(4000){while(c.companion.holdingToTalk)delay(30)}
                check(ball.location==origin);delay(400)
                println("PASS: native long hold opens the quick panel without stealing focus; outside release ends the hold once")
                c.companion.close();host.sync();delay(100)
                robot.mouseMove(ball.x+42,ball.y+42);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseMove(ball.x-90,ball.y+42);delay(650);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(100)
                check(ball.location!=origin);check(!c.companion.holdingToTalk);check(!c.voice.active)
                println("PASS: native drag moves the ball without entering voice")
                robot.mouseMove(ball.x+42,ball.y+42);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                withTimeout(4000){while(!c.companion.panelOpen)delay(30)}
                check(!c.companion.holdingToTalk);check(!c.voice.active)
                println("PASS: native single click remains a text question")
            }finally {robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);host.close();c.close();dir.toFile().deleteRecursively()}
        };kotlin.system.exitProcess(0)}catch(e:Throwable){e.printStackTrace();kotlin.system.exitProcess(1)}
    }
    private fun Window.titleOrEmpty()=when(this){is Frame->title;is Dialog->title;else->""}
}
