@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/** Real pointer and keyboard checks, without a gateway or a visible application owner. */
object Native183Check {
    private fun nodes(w:ComposeWindow):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        return w.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
    }
    private suspend fun ready(label:String,p:()->Boolean){repeat(320){if(p())return;delay(25)};check(p()){label}}
    private fun inlinePreview(w:ComposeWindow)=nodes(w).any {it.config.getOrNull(SemanticsProperties.ContentDescription)?.any {s->s.contains("图片预览")}==true}
    private suspend fun click(robot:Robot,w:ComposeWindow,tag:String) {
        val n=nodes(w).first {it.config.getOrNull(SemanticsProperties.TestTag)==tag}
        val p=n.boundsInRoot.center;val scale=w.graphicsConfiguration.defaultTransform.scaleX
        robot.mouseMove(w.x+(p.x/scale).toInt(),w.y+(p.y/scale).toInt())
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);delay(35);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(200)
    }
    @JvmStatic fun main(args:Array<String>) {
        try {runBlocking(Dispatchers.Swing) {
            val out=File(args.firstOrNull()?:"docs/previews/windows-1.8.3").apply {mkdirs()}
            val dir=Files.createTempDirectory("hermes-native183")
            val c=DesktopController(true,SecureConfigStore(dir){ByteArray(32){44}},autoConnect=false)
            val robot=Robot();var selection:ScreenSelection?=null
            val w=ComposeWindow().apply {isUndecorated=true;isTransparent=true;title="Hermes 1.8.3 check";setBounds(100,80,518,668)}
            try {
                c.notifications=false;c.reduceMotion=true;c.decisions.clear();c.runs.clear()
                c.companion.open();val s=c.companion.session!!
                val source=BufferedImage(640,360,BufferedImage.TYPE_INT_RGB)
                source.createGraphics().apply {color=Color(44,87,164);fillRect(0,0,640,360);color=Color.WHITE;font=Font("SansSerif",Font.BOLD,44);drawString("Hermes 1.8.3",130,190);dispose()}
                c.companion.addCapture(s,source.pngBytes());val file=c.attachments[s.scopedId]!!.single()
                c.setDraft(s.scopedId,"请分析这张截图，草稿需要保留")
                w.setContent {HermesTheme(c){FloatingQuickPanel(c)}};w.isVisible=true;w.toFront();w.requestFocus()
                ready("attachment rendered"){nodes(w).any {it.config.getOrNull(SemanticsProperties.TestTag)=="preview-attachment:${file.id}"}}
                delay(900) // Semantics exist before the native surface has painted its first frame.
                ImageIO.write(robot.createScreenCapture(w.bounds),"png",File(out,"Floating-Panel.png"))
                val before=Window.getWindows().filter {it.isVisible}.toSet()
                click(robot,w,"preview-attachment:${file.id}")
                ready("preview dialog visible"){inlinePreview(w)||Window.getWindows().any {it.isVisible&&it !in before}}
                check(c.attachments[s.scopedId]?.single()==file);check(c.drafts[s.scopedId]=="请分析这张截图，草稿需要保留")
                val dialog=Window.getWindows().firstOrNull {it.isVisible&&it !in before}?:w;delay(600)
                ImageIO.write(robot.createScreenCapture(dialog.bounds),"png",File(out,"Attachment-Preview.png"))
                robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE)
                ready("preview dismissed"){if(dialog===w)!inlinePreview(w)else !dialog.isVisible};check(c.attachments[s.scopedId]?.single()==file)
                println("PASS floating attachment opens preview, Escape closes it, file and draft survive")
                click(robot,w,"remove-attachment:${file.id}");check(c.attachments[s.scopedId].isNullOrEmpty())
                println("PASS only the independent remove button removes the attachment")
                // Test the same component in the full composer.
                c.attachments[s.scopedId]=listOf(file)
                w.setSize(1000,430);w.setContent {HermesTheme(c){Surface {Box(Modifier.fillMaxSize().padding(24.dp)){ChatComposer(c,s)}}}}
                ready("main composer rendered"){nodes(w).any {it.config.getOrNull(SemanticsProperties.TestTag)=="preview-attachment:${file.id}"}}
                delay(600)
                val beforeMain=Window.getWindows().filter {it.isVisible}.toSet();click(robot,w,"preview-attachment:${file.id}")
                ready("main preview visible"){inlinePreview(w)||Window.getWindows().any {it.isVisible&&it !in beforeMain}}
                val mainDialog=Window.getWindows().firstOrNull {it.isVisible&&it !in beforeMain}?:w;delay(300)
                robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE);ready("main preview closed"){if(mainDialog===w)!inlinePreview(w)else !mainDialog.isVisible}
                check(c.attachments[s.scopedId]?.single()==file);println("PASS main composer attachment preview")
                // Screenshot overlay must receive Escape with every app window hidden.
                w.isVisible=false
                var callbacks=0;var result:Rectangle?=Rectangle()
                selection=ScreenSelection(captureDesktopScreens()){callbacks++;result=it}
                ready("screenshot overlay keyboard focus"){KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow?.let {it.isVisible&&it is javax.swing.JFrame&&it !is ComposeWindow}==true}
                robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE)
                ready("Escape cancels screenshot"){callbacks==1};check(result==null)
                selection.close();check(callbacks==1)
                println("PASS screenshot Escape, no main window, callback once")
                callbacks=0;selection=ScreenSelection(captureDesktopScreens()){callbacks++;result=it}
                delay(200);robot.mouseMove(400,300);robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
                ready("right click cancels"){callbacks==1};check(result==null);println("PASS screenshot right-click cancellation")
                callbacks=0;selection=ScreenSelection(captureDesktopScreens()){callbacks++;result=it}
                delay(200);robot.mouseMove(180,200);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseMove(480,420);delay(60);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                ready("selection completes"){callbacks==1};check(result==Rectangle(180,200,300,220));println("PASS screenshot selection still completes")
                // Capture a batch clarification card for visual inspection.
                val request=AgentRequest("srq-batch","runtime",type=AgentRequestType.CLARIFICATION,title="Hermes 有 2 个问题需要你确认",questions=listOf(
                    AgentQuestion("file","哪些文件需要保留？",listOf(AgentRequestChoice("正式资料","keep","保留当前工作空间中的正式文件"),AgentRequestChoice("临时测试文件","temp")),true),
                    AgentQuestion("format","整理成什么格式？",listOf(AgentRequestChoice("Excel 表格","xlsx"),AgentRequestChoice("Word 文档","docx")))))
                w.setBounds(100,80,650,720);w.setContent {HermesTheme(c){Surface {Box(Modifier.fillMaxSize().padding(24.dp)){DecisionPanel(c,PendingDecision(s.profile,s,request),Modifier.fillMaxSize())}}}};w.isVisible=true
                delay(600);ImageIO.write(robot.createScreenCapture(w.bounds),"png",File(out,"Clarification-Batch.png"))
                val menu=DesktopMenuHost(c,"悬浮助手"){listOf(DesktopMenuAction("ask","提问","history"){},DesktopMenuAction("capture","框选截图","capture"){},DesktopMenuAction("open","打开主窗口","panel",dividerBefore=true){})}
                try {menu.show(Point(900,250));delay(700);val frame=Window.getWindows().filterIsInstance<ComposeWindow>().first {it.isVisible&&it.title=="Hermes · 悬浮助手"};ImageIO.write(robot.createScreenCapture(frame.bounds),"png",File(out,"Floating-Menu.png"))}finally {menu.close()}
            }finally {selection?.close();w.dispose();c.close();dir.toFile().deleteRecursively()}
        };System.exit(0)}catch(e:Throwable){e.printStackTrace();System.exit(1)}
    }
}
