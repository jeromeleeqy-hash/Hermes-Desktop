@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import java.awt.event.InputEvent
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

object Render180Previews {
    @JvmStatic fun main(args:Array<String>)=runBlocking<Unit>(Dispatchers.Swing) {
        val out=File(args.firstOrNull()?:"docs/previews/windows-1.8.0").apply {mkdirs()}
        val dir=Files.createTempDirectory("hermes-render180")
        val c=DesktopController(true,SecureConfigStore(dir){ByteArray(32){7}},autoConnect=false)
        try {
            c.connected=true;c.reduceMotion=true;c.nickname="Jerome";c.changeLanguage("zh")
            val daily=HermesSession("daily","日常助理",profile=c.profile,model="日常模型")
            c.sessions=listOf(daily);c.companion.open()
            c.messages[daily.scopedId]=listOf(ChatMessage(role=MessageRole.USER,content="帮我把今天的想法整理成行动计划。"),ChatMessage(role=MessageRole.ASSISTANT,content="可以，我们先抓住最重要的三件事。\n\n1. 整理资料，明确今天要完成的目标。\n2. 给每一步留出专注的时间。\n3. 结束前回顾进展，记下明天的起点。\n\n也可以把文档拖进来，我会结合资料一起考虑。"))
            c.attachments[daily.scopedId]=listOf(PendingAttachment(name="项目周报.docx",mimeType="application/vnd.openxmlformats-officedocument.wordprocessingml.document",uploadDataUrl="data:application/octet-stream;base64,AA=="))
            c.drafts[daily.scopedId]="结合这份周报，帮我列出明天的优先事项。"
            val quick=ImageComposeScene(735,960,density=Density(1.5f),coroutineContext=Dispatchers.Swing){HermesTheme(c){FloatingQuickPanel(c)}}
            try {repeat(25){quick.render(System.nanoTime()).close();delay(20)};File(out,"Floating-Assistant.png").writeBytes(quick.render(System.nanoTime()).use {it.encodeToData()!!.use {it.bytes}})}finally{quick.close()}
            c.page=Page.PROFILE;c.settingsSection="桌面助手"
            val settings=ImageComposeScene(1920,1200,density=Density(1.5f),coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopBackdrop{DesktopWorkspace(c)}}}
            try {repeat(20){settings.render(System.nanoTime()).close();delay(20)};File(out,"Desktop-Settings.png").writeBytes(settings.render(System.nanoTime()).use {it.encodeToData()!!.use {it.bytes}})}finally{settings.close()}
            withContext(Dispatchers.IO) {
                ImageIO.write(OfficeDisplayFixtures.render(wordPreview(OfficeDisplayFixtures.word(),false)),"png",File(out,"Word-Reading.png"))
                WorkbookPreview(OfficeDisplayFixtures.workbook()).use {ImageIO.write(OfficeDisplayFixtures.render(it.html(0)),"png",File(out,"Excel-Reading.png"))}
                loadPagedPreview(OfficeDisplayFixtures.slides(false),false).use {File(out,"PowerPoint-Reading.png").writeBytes(it.page(0,1200))}
            }
            println("Rendered five real application/renderer previews: $out")
        }finally {c.close();dir.toFile().deleteRecursively()}
    }
}

/** Run under a real X display; verifies actual unowned windows, pointer gestures and screen selection. */
object NativeCompanionCheck {
    @JvmStatic fun main(args:Array<String>) {
        try {runBlocking(Dispatchers.Swing) {
            val out=File(args.firstOrNull()?:"build/verification180/native").apply {mkdirs()}
            val dir=Files.createTempDirectory("hermes-native180")
            val c=DesktopController(true,SecureConfigStore(dir){ByteArray(32){11}},autoConnect=false)
            var host:DesktopFloatingHost?=null
            try {
                c.connected=true;c.reduceMotion=true;c.changeLanguage("zh")
                host=DesktopFloatingHost(c);host.sync();delay(300)
                val ball=Window.getWindows().first {it.isVisible&&it.width==84}
                check(ball.isAlwaysOnTop)
                c.companion.open();host.sync();delay(1000)
                val quick=Window.getWindows().first {it.isVisible&&it is Frame&&it.title=="Hermes · 随时问"}
                check(quick.isAlwaysOnTop);check(quick.owner==null)
                ImageIO.write(Robot().createScreenCapture(quick.bounds),"png",File(out,"Native-Quick-Window.png"))
                c.companion.close();host.sync();check(!quick.isVisible);check(ball.isVisible)
                c.floatingAssistantEnabled=false;host.sync();check(!ball.isVisible)
                c.floatingAssistantEnabled=true;host.sync();check(ball.isVisible)
                c.companion.open(CompanionAction.CAPTURE);host.sync();delay(700)
                val overlay=Window.getWindows().firstOrNull {it.isVisible&&it.cursor.type==Cursor.CROSSHAIR_CURSOR}
                check(overlay!=null){"Screenshot overlay did not open: ${c.companion.failure}"}
                val robot=Robot();robot.mouseMove(140,150);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);delay(100);robot.mouseMove(430,340);delay(100);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(1000)
                val s=c.companion.session!!;check(c.attachments[s.scopedId].orEmpty().any {it.mimeType=="image/png"}){"Selected screenshot did not become a draft attachment"}
                host.sync();check(quick.isVisible);check(ball.isVisible)
                println("PASS: native floating/quick windows, close/reopen, settings toggle, range capture -> daily draft")
            }finally {host?.close();c.close();dir.toFile().deleteRecursively();FxRuntime.shutdown()}
        };kotlin.system.exitProcess(0)}catch(e:Throwable){e.printStackTrace();kotlin.system.exitProcess(1)}
    }
}
