package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.mock
import java.awt.*
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO

class DesktopCompanionTest {
    @Test fun aClickWaitsForDoubleClickAndDoubleClickNeverAlsoAsks() {
        val g=FloatingGestures();val p=Point(20,20)
        g.press(p,0);assertEquals(FloatingGestures.Result.NONE,g.release(p,30))
        assertEquals(FloatingGestures.Result.NONE,g.tick(200))
        g.press(p,220);assertEquals(FloatingGestures.Result.CAPTURE,g.release(p,250))
        assertEquals(FloatingGestures.Result.NONE,g.tick(600));assertFalse(g.waiting)
        g.press(p,1000);g.release(p,1030)
        assertEquals(FloatingGestures.Result.ASK,g.tick(1330));assertEquals(FloatingGestures.Result.NONE,g.tick(2000))
    }
    @Test fun holdingReleasesExactlyOnceAndCannotBecomeAClickOrDrag() {
        val g=FloatingGestures();val p=Point(20,20)
        g.press(p,0);assertEquals(FloatingGestures.Result.VOICE,g.tick(550))
        assertEquals(FloatingGestures.Result.NONE,g.tick(900))
        assertEquals(FloatingGestures.Result.NONE,g.move(Point(100,100)))
        assertEquals(FloatingGestures.Result.VOICE_RELEASE,g.release(Point(100,100),950))
        assertEquals(FloatingGestures.Result.NONE,g.release(p,960))
        assertEquals(FloatingGestures.Result.NONE,g.tick(2000))
        g.press(p,3000);assertEquals(FloatingGestures.Result.DRAG,g.move(Point(80,20)))
        assertEquals(FloatingGestures.Result.NONE,g.tick(4000));g.release(Point(80,20),4200)
        assertEquals(FloatingGestures.Result.NONE,g.tick(5000))
        g.press(p,6000);assertEquals(FloatingGestures.Result.VOICE,g.tick(6550));g.cancel()
        assertEquals(FloatingGestures.Result.NONE,g.release(p,6600));assertEquals(FloatingGestures.Result.NONE,g.tick(7000))
    }
    @Test fun bubbleStaysOnReachableWorkAreaAfterMonitorRemoval() {
        val left=Rectangle(-1920,-200,1920,1032);val right=Rectangle(0,0,2560,1392)
        assertEquals(Point(-84,700),clampFloatingBounds(Point(-70,700),84,84,listOf(left,right)))
        assertEquals(Point(0,40),clampFloatingBounds(Point(-1700,40),84,84,listOf(right)))
        assertEquals(Point(2476,1308),clampFloatingBounds(Point(2540,1380),84,84,listOf(right)))
        assertEquals(Rectangle(-100,20,250,120),selectionRectangle(Point(150,140),Point(-100,20)))
    }
    @Test fun screenshotJoinsNegativeMonitorAndScaledMonitorWithoutLeakingOutsideSelection() {
        fun image(w:Int,h:Int,color:Color)=BufferedImage(w,h,BufferedImage.TYPE_INT_RGB).apply {createGraphics().apply {this.color=color;fillRect(0,0,w,h);dispose()}}
        val config=mock(GraphicsConfiguration::class.java)
        val screens=listOf(ScreenSnapshot(config,Rectangle(-100,0,100,100),image(100,100,Color.RED)),ScreenSnapshot(config,Rectangle(0,0,100,100),image(200,200,Color.BLUE)))
        val result=ImageIO.read(composeScreenSelection(screens,Rectangle(-30,10,60,40)).inputStream())
        assertEquals(60,result.width);assertEquals(40,result.height)
        assertEquals(Color.RED.rgb,result.getRGB(1,1));assertEquals(Color.BLUE.rgb,result.getRGB(58,38))
        assertThrows(IllegalArgumentException::class.java){composeScreenSelection(screens,Rectangle(0,0,2,2))}
    }
    @Test fun quickPanelUsesDailyAssistantAndKeepsAttachmentsAndVoiceInTheSelectedConversation()=runBlocking<Unit>(Dispatchers.Swing) {
        val dir=Files.createTempDirectory("hermes-quick-test")
        val c=DesktopController(true,SecureConfigStore(dir){ByteArray(32){4}},autoConnect=false)
        try {
            c.connected=true
            val main=HermesSession("main","主窗口会话",profile=c.profile)
            val daily=HermesSession("daily","日常助理",profile=c.profile)
            c.sessions=listOf(main,daily);c.currentSession=main;c.page=Page.FILES
            c.companion.open();assertEquals(daily.scopedId,c.companion.session?.scopedId)
            assertEquals(main,c.currentSession);assertEquals(Page.FILES,c.page)
            val image=BufferedImage(24,24,BufferedImage.TYPE_INT_RGB).pngBytes()
            c.companion.addCapture(daily,image);assertEquals(1,c.attachments[daily.scopedId]?.size)
            assertTrue(c.attachments[main.scopedId].isNullOrEmpty())
            c.companion.select(main);c.companion.open(CompanionAction.VOICE)
            assertTrue(c.voice.active);assertTrue(c.voice.fromCompanion);assertEquals(main.scopedId,c.voice.sessionKey)
            c.companion.open();assertFalse(c.voice.active);assertFalse(c.companion.voicePanel)
            c.companion.close();c.companion.open();assertEquals(daily.scopedId,c.companion.session?.scopedId)
            assertEquals(1,c.attachments[daily.scopedId]?.size)
            c.companion.close();c.companion.addCapture(daily,image);assertEquals(1,c.attachments[daily.scopedId]?.size)
        }finally {c.close();dir.toFile().deleteRecursively()}
    }
}
