@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing

object RenderPreviews {
    @JvmStatic fun main(args:Array<String>) = runBlocking(Dispatchers.Swing) {
        val destination=File(args.firstOrNull() ?: "build/previews").apply {mkdirs()}
        for(name in args.drop(1).ifEmpty {listOf("home-wide","home-narrow","home-empty","tasks-wide-empty","tasks-wide-active","tasks-narrow","settings-wide-glass","settings-retina-glass","settings-paper","settings","english-dark","chat","chat-paper","chat-glass","chat-wide","chat-collapsed","narrow-active","jump","dialog","dialog-paper","dialog-glass","files","files-wide","sessions","assistant","dialog-approval","dialog-approval-glass")}) {
            val loginPreview=name.startsWith("login")
            val page=when {loginPreview->Page.HOME;name.startsWith("dialog-approval")->Page.TASKS;(name.startsWith("settings")||name=="english-dark")->Page.PROFILE;name.startsWith("home")->Page.HOME;(name.startsWith("tasks")||name.startsWith("cron"))->Page.TASKS;name.startsWith("files")->Page.FILES;name=="sessions"->Page.SESSIONS;else->Page.CHAT}
            val c=DesktopController(true);c.page=page;c.reduceMotion=true
            var quickDismissed=false
            if(name=="chat-focus")c.focusMode=true
            if(name=="chat-collapsed")c.sidebarCollapsed=true
            if(name=="chat-dark")c.appearance="深色"
            if(name=="chat-outline"||name=="chat-metadata")c.showOutline=true
            if(page in setOf(Page.HOME,Page.TASKS,Page.SESSIONS))seedWideFixtures(c,name)
            if(name=="home-error"){c.sessions=emptyList();c.sessionsLoadError="Input should be less than or equal to 100"}
            if(name=="home-loading"){c.sessions=emptyList();c.sessionsLoading=true}
            if(name.contains("paper"))c.skin="温暖灵动"
            if(name.contains("glass")&&!name.startsWith("settings"))c.skin="液态玻璃"
            if(name=="chat-drawer")c.sessionDrawer=true
            if(name=="chat-split"){c.documentSplit=true;c.setFilesPanel(false)}
            if(name.contains("large-text"))c.textScale=1.3f
            val settingsSections=mapOf("settings-model" to "模型","settings-memory" to "对话与记忆","settings-voice" to "语音","settings-skills" to "Skills","settings-tools" to "工具集","settings-notices" to "通知","settings-help" to "帮助")
            settingsSections[name]?.let {c.settingsSection=it}
            c.settings=ServerSettings(models=ServerModelSettings(provider="deepseek",model="deepseek-v4-flash",auxiliary=mapOf("vision" to ModelChoice("anthropic","claude-haiku-4-5"))),conversation=ConversationStyleSettings("清楚、自然，先说重点。","Asia/Shanghai"))
            c.modelCatalog=ModelCatalog(providers=listOf(ModelProvider(slug="deepseek",name="DeepSeek",models=listOf("deepseek-v4-flash","deepseek-v4-pro"))))
            c.skills=listOf(ServerSkill("daily-review","整理一天的进展，标出待处理的问题。"),ServerSkill("document-editor","整理和修改 Markdown 文档，保留来源与版本。"),ServerSkill("web-research","搜索资料并提取可核对的引用。",enabled=false))
            c.toolsets=listOf(ToolsetInfo("web","Web Search & Scraping","搜索网页并提取内容。",listOf("web_search","web_extract")),ToolsetInfo("files","File Operations","读取、编辑和保存服务器上的文件。",listOf("read_file","write_file","patch")),ToolsetInfo("video","Video Analysis","理解视频内容。",listOf("video_analyze"),false,false))
            if(name.startsWith("cron")) {
                c.taskTab="定时任务"
                c.cronJobs=listOf(CronJob("daily","每日工作简报","整理昨天的工作进展、待确认事项和生成的文件。",CronSchedule(expression="0 9 * * *"),nextRunAt="2026-09-12T09:00:00+08:00",lastRunAt="2026-09-11T09:00:00+08:00",lastStatus="ok"),CronJob("weekly","每周内容复盘","总结本周选题、发布情况和下周需要改进的地方。",CronSchedule(expression="30 17 * * 5"),lastStatus="failed"),CronJob("reminder","定期整理文件","整理工作空间中的文档。",CronSchedule(expression="0 18 * * 1-5"),enabled=false))
            }
            if(name=="files-pdf-windows") {
                val bytes=java.io.ByteArrayOutputStream().use {out->org.apache.pdfbox.pdmodel.PDDocument().use {doc->
                    repeat(3){i->val p=org.apache.pdfbox.pdmodel.PDPage();doc.addPage(p)
                        org.apache.pdfbox.pdmodel.PDPageContentStream(doc,p).use {stream->
                            stream.beginText();stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD),26f);stream.newLineAtOffset(50f,725f);stream.showText("Hermes / Document workspace");stream.endText()
                            stream.beginText();stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12f);stream.newLineAtOffset(50f,680f);stream.showText("A focused place to read, review and continue your work.");stream.endText()
                            stream.setNonStrokingColor(java.awt.Color(237,242,252));stream.addRect(50f,390f,500f,220f);stream.fill()
                            stream.beginText();stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),14f);stream.newLineAtOffset(70f,570f);stream.showText("Project notes / Page "+(i+1));stream.endText()
                        }
                    };doc.save(out)};out.toByteArray()}
                c.document=c.document!!.copy(document=WorkspaceDocument("项目简报.pdf","/workspace/项目简报.pdf","application/pdf","",bytes))
                c.setFilesPanel(false)
            }
            if(name=="files-image-windows") {
                val bytes=RenderPreviews::class.java.getResourceAsStream("/icon.png")!!.readBytes()
                c.document=c.document!!.copy(document=WorkspaceDocument("Hermes-F1.png","/workspace/Hermes-F1.png","image/png","",bytes));c.setFilesPanel(false)
            }
            if(name=="files-table")c.document=c.document!!.copy(document=c.document!!.document.copy(content="# 项目进展\n\n| 事项 | 最新进展 | 下一步 |\n|---|---|---|\n| 内容运营 | 已完成选题整理与制作排期 | 确认首期脚本 |\n| 团队协作 | 汇总了待确认问题 | 安排明天的讨论 |\n\n## 接下来\n把需要确认的内容整理好，方便继续讨论。"))
            if(name=="files-diff")c.setDocumentText(c.document!!,c.documentText().replace("延续移动端的核心能力","重新组织桌面上的工作流程")+"\n\n## 新增\n支持文档与对话并排查看。")
            if(name=="chat-collapsed"||name.contains("wide")||name.startsWith("home")||name.startsWith("tasks"))c.setFilesPanel(false)
            if(name.startsWith("chat")&&name!="chat-metadata") {
                val current=c.currentSession
                val existing=c.sessions
                val recent=listOf("秋季内容运营方案","短视频脚本讨论","本周工作安排","阅读与灵感")
                c.sessions=existing.map {it.copy(updatedAt=java.time.Instant.now().minusSeconds(120).toString())}+recent.mapIndexed {i,title->HermesSession("recent-$i",title,profile=c.profile,workspacePath=c.project?.primaryPath.orEmpty(),preview=listOf("已整理好选题，明天继续确认制作安排。","我们先完成第一期脚本。","先处理需要团队确认的事项。","记录下这次讨论的关键观点。")[i],updatedAt=java.time.Instant.now().minusSeconds((i+1)*18000L).toString())}
                c.currentSession=current?.copy(updatedAt=java.time.Instant.now().minusSeconds(120).toString())
            }
            if(name.contains("office")||name.contains("model-picker")||name.contains("model-error"))c.setFilesPanel(false)
            if(page==Page.CHAT)c.currentSession?.let {old->
                val selected=old.copy(model="deepseek-v4-pro",provider="deepseek")
                c.sessions=c.sessions.map {if(it.scopedId==selected.scopedId)selected else it};c.currentSession=selected
            }
            if(name.contains("model-error"))c.modelCatalogError=modelCatalogProblem("Restart required: This process is running code from 2554a16a1d but the checkout on disk is now 939e45c91d. The model picker would risk a stale-module crash — restart this Hermes process.")
            c.assistantPanel=name=="assistant"
            if(loginPreview){c.baseUrl="https://gateway.example.com";c.username="admin"}
            if(name=="english-dark"){c.appearance="深色";c.language="en"}
            setDesktopLanguage(if(name=="english-dark")"en"else"zh")
            if(name=="narrow-active")c.currentSession!!.let {session->
                val record=RunRecord(session,"demo")
                c.runs[session.scopedId]=DesktopRun(session,com.qingyu.hermescompanion.data.StreamController(),record.assistantId,record=record,status="正在处理")
                c.setDraft(session.scopedId,"补充要求：请加上时间安排。")
            }
            if(name=="jump")c.currentSession!!.let {session->
                c.messages[session.scopedId]=(1..16).flatMap {index->listOf(ChatMessage(id="u$index",role=MessageRole.USER,content="第 $index 项，请保留当前对话并记录文件变更。"),ChatMessage(id="a$index",role=MessageRole.ASSISTANT,content="已记录。可以继续浏览右侧的文件，或返回最新消息。"))}
                c.focusMessageId="a2"
            }
            if(name=="chat-metadata")c.currentSession!!.let {session->
                val header="[System: The active model for this chat has changed to deepseek-v4-pro via provider deepseek. From this point forward, use this runtime metadata when answering questions about what model/provider is active.]"
                c.messages[session.scopedId]=listOf(ChatMessage(id="meta-user",role=MessageRole.USER,content=header+"\n请帮我整理今天的工作。"),ChatMessage(id="meta-answer",role=MessageRole.ASSISTANT,content="已整理第一部分。\nOperation interrupted: waiting for model response (85.6s elapsed)."))
                c.setFilesPanel(false)
            }
            val narrow=name.contains("narrow")||name.contains("retina")||name=="dialog-glass"
            val wide=name.contains("wide")
            val windowsViewport=name.contains("windows")||name.contains("large-text")||name=="chat-split"||name=="chat-drawer"||name.startsWith("cron")||name in settingsSections.keys
            val logicalWidth=if(windowsViewport)1280 else if(wide)2048 else if(narrow)1160 else 1440
            val logicalHeight=if(windowsViewport)660 else if(wide)1012 else if(narrow)760 else 900
            val scale=if(name.contains("retina"))2f else if(windowsViewport)1.5f else 1f
            val scene=ImageComposeScene((logicalWidth*scale).toInt(),(logicalHeight*scale).toInt(),density=Density(scale),coroutineContext=Dispatchers.Swing) {
                HermesTheme(c) {
                    DesktopBackdrop {
                        CompositionLocalProvider(LocalDesktopWindowControls provides DesktopWindowControls()) {
                            DesktopFrame {if(loginPreview)ConnectionView(c)else DesktopWorkspace(c)}
                        }
                        if(name.startsWith("quick-"))Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.18f)),contentAlignment=Alignment.Center) {
                            HermesDialogContent(title={Text("快速查找")},text={QuickSwitchContent(c){quickDismissed=true}},confirmButton={},showFooter=false)
                        }
                        if(name.startsWith("cron-editor"))Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.28f)),contentAlignment=Alignment.Center) {
                            CronEditor(c,c.cronJobs.first(),inlinePreview=true){}
                        }
                        if(name.startsWith("dialog"))Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.28f)),contentAlignment=Alignment.Center) {
                            if(name.startsWith("dialog-approval"))HermesDialogContent(
                                title={Text(tr("审批详情"))},
                                text={DecisionPanel(c,c.decisions.values.first(),Modifier.width(552.dp).height(280.dp))},
                                confirmButton={DeskTextButton(onClick={}){Text(tr("返回任务"))}}
                            )else HermesDialogContent(
                                title={Text(tr("文档尚未保存"))},
                                text={Column(Modifier.width(460.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Text(tr("可以保留本机草稿，稍后从文件页继续编辑。"));Caption("功能清单.md")
                                }},
                                confirmButton={DeskTextButton(onClick={}){Text(tr("保留草稿并收起"))}},
                                dismissButton={Row {
                                    DeskTextButton(onClick={}){Text(tr("继续编辑"))}
                                    DeskTextButton(onClick={}){Text(tr("放弃修改"))}
                                }}
                            )
                        }
                    }
                }
            }
            try {
                // Advance the scene clock; render() defaults to zero and freezes wheel animations.
                repeat(35){scene.render(System.nanoTime()).close();delay(33)}
                if(name.contains("model-picker")||name.contains("model-error")) {
                    click(scene,allNodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)=="composer-model"}.boundsInRoot.center)
                    repeat(8){scene.render(System.nanoTime()).close();delay(25)}
                    check(allNodes(scene).any {nodeText(it)=="选择模型"}) {"Model popup did not open by pointer"}
                    if(name.contains("model-error")) {
                        check(allNodes(scene).any {nodeText(it)=="服务器需要完成重启"})
                        check(allNodes(scene).none {nodeText(it)=="没有匹配的模型"})
                    }
                }
                if(name=="quick-keyboard") {
                    val before=c.currentSession
                    val field=allNodes(scene).first {it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("搜索操作、会话与文件")==true}
                    check(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("设置")))
                    repeat(4){scene.render(System.nanoTime()).close();delay(20)}
                    fun press(key:Key) {scene.sendKeyEvent(KeyEvent(key=key,type=KeyEventType.KeyDown))}
                    press(Key.DirectionDown)
                    repeat(3){scene.render(System.nanoTime()).close();delay(20)}
                    press(Key.DirectionUp)
                    repeat(3){scene.render(System.nanoTime()).close();delay(20)}
                    press(Key.Enter)
                    repeat(3){scene.render(System.nanoTime()).close();delay(20)}
                    check(quickDismissed&&c.page==Page.PROFILE&&c.currentSession==before) {"Quick switch keyboard activation failed or changed the conversation"}
                    quickDismissed=false;press(Key.Escape)
                    check(quickDismissed) {"Escape did not dismiss quick switch"}
                    println("Verified quick switch filtering, arrow keys, Enter, Escape and retained conversation")
                }
                if(name.startsWith("settings")&&name.endsWith("glass")) {
                    val card=allNodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)=="skin-流光玻璃"}
                    click(scene,card.boundsInRoot.center)
                    repeat(8){scene.render(System.nanoTime()).close();delay(25)}
                    check(c.skin=="流光玻璃")
                    println("Verified theme selection by pointer at ${scale}x density")
                }
                if(name=="home-scroll-windows") {
                    val range=allNodes(scene).mapNotNull {it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)}.maxByOrNull {it.maxValue()}!!
                    println("Home scroll before=${range.value()} max=${range.maxValue()}")
                    File(destination,"home-scroll-before.png").writeBytes(snapshot(scene))
                    scene.sendPointerEvent(PointerEventType.Move,Offset(logicalWidth*scale*.70f,logicalHeight*scale*.66f))
                    scene.sendPointerEvent(PointerEventType.Scroll,Offset(logicalWidth*scale*.70f,logicalHeight*scale*.66f),scrollDelta=Offset(0f,800f))
                    repeat(35){scene.render(System.nanoTime()).close();delay(25)}
                    println("Home scroll after=${range.value()} max=${range.maxValue()}")
                    check(range.value()>0) {"Workbench did not respond to wheel scrolling"}
                    val end=allNodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)=="home-end"}.boundsInRoot
                    check(end.height>0&&end.bottom<logicalHeight*scale) {"The end of the workbench cannot be reached: $end"}
                }
                if(name=="home-interaction") {
                    val key=c.homeDraftKey()
                    val field=allNodes(scene).first {it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("交办新任务")==true}
                    check(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("我已经写下的目标")))
                    repeat(3){scene.render(System.nanoTime()).close();delay(20)}
                    click(scene,allNodes(scene).first {nodeText(it)=="写一份文档"}.boundsInRoot.center)
                    repeat(3){scene.render(System.nanoTime()).close();delay(20)}
                    check(c.drafts[key].orEmpty().startsWith("我已经写下的目标"))
                    check(c.drafts[key].orEmpty().contains("先和我确认"))
                    val draft=c.drafts[key]
                    click(scene,allNodes(scene).first {nodeText(it)=="开始任务  ↑"}.boundsInRoot.center)
                    repeat(5){scene.render(System.nanoTime()).close();delay(20)}
                    check(c.page==Page.CHAT&&c.drafts[c.currentSession!!.scopedId]==draft)
                    println("Verified home typing, template append and task creation by actual controls")
                    c.page=Page.HOME
                    repeat(3){scene.render(System.nanoTime()).close();delay(20)}
                }
                if(name=="chat-workspace-menu") {
                    c.profiles=listOf(HermesProfile("default"),HermesProfile("personal"),HermesProfile("work"))
                    repeat(4){scene.render(System.nanoTime()).close();delay(20)}
                    click(scene,allNodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)=="workspace-switcher"}.boundsInRoot.center)
                    repeat(8){scene.render(System.nanoTime()).close();delay(25)}
                }
                if(name in setOf("files-split-wide","files-editor-windows","files-edit-windows")) {
                    click(scene,allNodes(scene).first {nodeText(it)==if(name.contains("split"))"双栏"else"编辑"}.boundsInRoot.center)
                    repeat(12){scene.render(System.nanoTime()).close();delay(25)}
                    check(allNodes(scene).any {it.config.getOrNull(SemanticsProperties.TestTag)=="markdown-source"})
                }
                if(name=="files-diff") {
                    val action=allNodes(scene).first {nodeText(it)=="查看修改"};click(scene,action.boundsInRoot.center)
                    repeat(5){scene.render(System.nanoTime()).close();delay(20)}
                }
                if(name=="settings-draft") {
                    val field=allNodes(scene).first {it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("你的称呼")==true}
                    check(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Jerome Studio")))
                    repeat(4){scene.render(System.nanoTime()).close();delay(20)}
                    click(scene,allNodes(scene).first {nodeText(it)=="模型"}.boundsInRoot.center)
                    repeat(4){scene.render(System.nanoTime()).close();delay(20)}
                    click(scene,allNodes(scene).first {nodeText(it)=="外观与账户"}.boundsInRoot.center)
                    repeat(4){scene.render(System.nanoTime()).close();delay(20)}
                    val restored=allNodes(scene).first {it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("你的称呼")==true}
                    check(restored.config.getOrNull(SemanticsProperties.EditableText)?.text=="Jerome Studio") {"Settings navigation lost an unsaved edit"}
                    println("Verified settings draft survives page navigation")
                }
                if(name=="chat-split")check(allNodes(scene).none {nodeText(it)=="跳到底部 ↓"}) {"A long reply did not open at its bottom"}
                if(name=="chat-quote") {
                    val paragraph=allNodes(scene).first {nodeText(it)?.startsWith("我按使用场景")==true}.boundsInRoot
                    val start=Offset(paragraph.left+2,paragraph.center.y);val end=Offset(paragraph.left+150,paragraph.center.y)
                    scene.sendPointerEvent(PointerEventType.Move,start)
                    scene.sendPointerEvent(PointerEventType.Press,start,button=PointerButton.Primary)
                    repeat(10){i->scene.sendPointerEvent(PointerEventType.Move,Offset(start.x+(end.x-start.x)*(i+1)/10,start.y),buttons=PointerButtons(isPrimaryPressed=true));scene.render(System.nanoTime()).close();delay(20)}
                    scene.sendPointerEvent(PointerEventType.Release,end,button=PointerButton.Primary)
                    scene.sendPointerEvent(PointerEventType.Press,Offset(start.x+30,start.y),button=PointerButton.Secondary)
                    scene.sendPointerEvent(PointerEventType.Release,Offset(start.x+30,start.y),button=PointerButton.Secondary)
                    repeat(4){scene.render(System.nanoTime()).close();delay(20)}
                    val quote=allNodes(scene).first {nodeText(it)=="引用到输入框"}
                    click(scene,quote.boundsInRoot.center)
                    repeat(4){scene.render(System.nanoTime()).close();delay(20)}
                    check(c.drafts[c.currentSession!!.scopedId].orEmpty().startsWith("> ")) {"Selected text was not placed in the draft"}
                    println("Verified actual mouse selection and quote context menu")
                }
                verifyLayout(scene,name,scale)
                File(destination,"$name.png").writeBytes(snapshot(scene))
                if(name=="settings-retina-glass") {
                    scene.sendPointerEvent(PointerEventType.Move,Offset(1800f,1100f))
                    scene.sendPointerEvent(PointerEventType.Scroll,Offset(1800f,1100f),scrollDelta=Offset(0f,1200f))
                    repeat(30){scene.render(System.nanoTime()).close();delay(33)}
                    val save=allNodes(scene).first {nodeText(it)=="保存资料"}.boundsInRoot
                    File(destination,"settings-retina-glass-scrolled.png").writeBytes(snapshot(scene))
                    println("Scrolled save action bounds: $save")
                    check(save.height>0&&save.top>0&&save.bottom<logicalHeight*scale-20) {"The profile save action could not be reached by scrolling"}
                    File(destination,"settings-retina-glass-scrolled.png").writeBytes(snapshot(scene))
                    println("Verified the profile save action is reachable by mouse wheel")
                }
                if(name=="chat") {
                    val session=c.currentSession;val document=c.document
                    click(scene,allNodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)=="toggle-files"}.boundsInRoot.center)
                    repeat(5){scene.render(System.nanoTime()).close();delay(20)}
                    check(!c.filesPanelOpen) {"Clicking the rail did not hide the files"}
                    check(c.page==Page.CHAT&&c.currentSession==session&&c.document==document)
                    click(scene,allNodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)=="toggle-files"}.boundsInRoot.center)
                    repeat(5){scene.render(System.nanoTime()).close();delay(20)}
                    check(c.filesPanelOpen)
                    println("Verified file panel toggle preserves conversation and document")
                    val divider=allNodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)=="files-divider"}.boundsInRoot.center
                    val width=c.panelWidth
                    scene.sendPointerEvent(PointerEventType.Move,divider)
                    scene.sendPointerEvent(PointerEventType.Press,divider,button=PointerButton.Primary)
                    repeat(8){i->scene.sendPointerEvent(PointerEventType.Move,Offset(divider.x-(i+1)*10,divider.y),buttons=PointerButtons(isPrimaryPressed=true));scene.render(System.nanoTime()).close();delay(20)}
                    scene.sendPointerEvent(PointerEventType.Release,Offset(divider.x-80,divider.y),button=PointerButton.Primary)
                    repeat(4){scene.render(System.nanoTime()).close();delay(20)}
                    check(c.panelWidth>width+40&&c.currentSession==session&&c.document==document) {"File divider lost context or did not resize"}
                    println("Verified drag resize preserves the session and document")
                }
                if(name=="sessions") {
                    val target=allNodes(scene).first {nodeText(it)=="全部时间"}
                    val before=snapshot(scene)
                    scene.sendPointerEvent(PointerEventType.Move,target.boundsInRoot.center)
                    repeat(3){scene.render(System.nanoTime()).close();delay(20)}
                    val after=snapshot(scene)
                    check(before.contentEquals(after)) {"Hovering the selected time filter painted an unwanted state layer"}
                    File(destination,"sessions-hover.png").writeBytes(after)
                    println("Verified filter hover has no rectangle")
                }
                if(name=="jump") {
                    val target=allNodes(scene).first {nodeText(it)=="跳到底部 ↓"}
                    val input=allNodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)=="chat-input"}
                    check(target.boundsInRoot.bottom<input.boundsInRoot.top)
                    click(scene,target.boundsInRoot.center)
                    repeat(25){scene.render(System.nanoTime()).close();delay(25)}
                    check(allNodes(scene).none {nodeText(it)=="跳到底部 ↓"}) {"Jump did not reach the bottom"}
                    println("Verified jump footer position and pointer interaction")
                }
                println("Rendered $name")
            } finally {scene.close();c.close()}
        }
    }
    private fun seedWideFixtures(c:DesktopController,name:String) {
        val titles=listOf("秋季内容运营方案", "日常助理", "短视频制作与发布计划", "阅读与灵感记录", "整理本周三件重要的事", "产品迭代讨论")
        val previews=listOf("把选题、制作节奏和发布安排整理成一份可以执行的方案，明天继续讨论。", "你好，先记录一下。 <!-- hermes-mobile-context-v1:309 --> 内部上下文，不应显示", "每周三条内容，先完成脚本，再安排拍摄和剪辑。", "@file:/root/.hermes/attachments/品牌与内容方法.md 这份资料的关键观点帮我保存下来。", "按优先级列好，上午先处理需要协作的工作。", "保留讨论结论，下次从待确认的问题继续。")
        c.sessions=titles.mapIndexed {i,title->HermesSession("fixture-$i",title,preview=previews[i],workspacePath=c.project?.primaryPath.orEmpty(),profile=c.profile,updatedAt=java.time.Instant.now().minusSeconds(i*3600L).toString())}
        c.recentArtifacts.clear()
        listOf("秋季内容运营方案_选题与制作排期_20260910.md", "短视频脚本提纲_第一期.md", "品牌内容方法_阅读笔记.md", "本周工作安排与协作清单.md", "产品迭代_会议纪要与待办事项.md").forEachIndexed {i,file->
            val session=c.sessions[i]
            c.recentArtifacts+=RecentArtifact(c.profile,session.id,session.title,path="${c.project!!.primaryPath}/$file",name=file,kind="document",workspacePath=c.project!!.primaryPath)
        }
        c.decisions.clear();c.runs.clear()
        c.completions=listOf(RunCompletionSummary(c.sessions[1].scopedId,"日常助理","已记录今天的安排，接下来可以继续补充。"))
        if(name.contains("active")||name=="tasks-narrow"||name.startsWith("dialog-approval")) {
            listOf("保存本周运营执行方案", "确认短视频发布安排").forEachIndexed {i,title->
                val session=c.sessions[i]
                c.decisions["fixture-$i"]=PendingDecision(c.profile,session,AgentRequest("fixture-$i","runtime",session.id,if(i==0)AgentRequestType.APPROVAL else AgentRequestType.CLARIFICATION,title,"请确认后继续执行。"))
            }
            val session=c.sessions[2];val record=RunRecord(session,"preview")
            c.runs[session.scopedId]=DesktopRun(session,com.qingyu.hermescompanion.data.StreamController(),record.assistantId,record=record,status="正在整理素材")
            c.completions=c.sessions.takeLast(3).map {RunCompletionSummary(it.scopedId,it.title,it.preview)}
        }
        if(name=="tasks-wide-empty")c.completions=emptyList()
        if(name=="home-empty") {c.sessions=emptyList();c.recentArtifacts.clear();c.completions=emptyList()}
    }
    private fun verifyLayout(scene:ImageComposeScene,name:String,scale:Float) {
        val nodes=allNodes(scene)
        fun tagged(tag:String)=nodes.first {it.config.getOrNull(SemanticsProperties.TestTag)==tag}
        if(nodes.any {it.config.getOrNull(SemanticsProperties.TestTag)=="chat-composer"}) {
            val composer=tagged("chat-composer").boundsInRoot;val model=tagged("composer-model").boundsInRoot
            check(model.top>=composer.top&&model.bottom<=composer.bottom) {"Model picker escaped the composer"}
            check(composer.right-tagged("send-message").boundsInRoot.right<=20*scale) {"Send button is detached from the composer's right edge"}
            check(tagged("workspace-switcher").boundsInRoot.right<=tagged("chat-content").boundsInRoot.left+1) {"Workspace switcher is not in the left navigation"}
            check(tagged("toggle-files").boundsInRoot.left>tagged("refresh-content").boundsInRoot.right) {"Files is not next to refresh"}
        }
        if(name=="chat-metadata") {
            check(nodes.none {nodeText(it)?.contains("From this point forward")==true||nodeText(it)?.contains("Operation interrupted:")==true})
            check(nodes.any {nodeText(it)=="已切换模型 · deepseek-v4-pro"})
            check(nodes.any {nodeText(it)=="请帮我整理今天的工作。"})
        }
        if(name=="home-error") {
            check(nodes.any {nodeText(it)=="暂时无法同步会话"})
            check(nodes.none {nodeText(it)=="从一个想法开始"||nodeText(it)?.contains("Input should")==true}) {"An error appeared as empty history or raw protocol text"}
        }
        if(name=="home-loading")check(nodes.none {nodeText(it)=="从一个想法开始"})
        if(name=="files-diff")check(tagged("document-status").boundsInRoot.top>820f*scale) {"Diff mode detached the status bar from the window bottom"}
        if(name.contains("wide")) {
            val tag=when {name.startsWith("home")->"home-content";name.startsWith("tasks")->"tasks-content";name.startsWith("settings")->"settings-content";name.startsWith("files")->"document-content";else->"chat-content"}
            val limit=when {tag=="settings-content"||tag=="chat-content"->2048;tag=="document-content"->1100;else->1800}
            check(tagged(tag).boundsInRoot.width<=limit*scale+1) {"The wide viewport defeated the content width limit: $tag"}
            if(tag=="chat-content")check(tagged("chat-composer").boundsInRoot.width<=1160*scale+1) {"The composer exceeded a readable width"}
        }
        if(name.startsWith("tasks")&&name!="tasks-wide-empty") {
            val lanes=listOf("待你确认","正在执行","最近完成").map {tagged("lane-$it").boundsInRoot}
            check(lanes.zipWithNext().all {(a,b)->a.right<b.left&&kotlin.math.abs(a.top-b.top)<1&&kotlin.math.abs(a.height-b.height)<1}) {"Task lanes are not aligned"}
        }
        if(name=="sessions")check(nodes.any {nodeText(it)=="秋季内容运营方案"}) {"History fixture did not contain visible sessions"}
        if(name.startsWith("home")||name=="sessions") {
            check(nodes.mapNotNull(::nodeText).none {it.contains("hermes-mobile-context")||it.contains("/root/.hermes/")}) {"Internal metadata is visible in conversation previews"}
        }
        if(name.startsWith("settings")&&name !in setOf("settings-model","settings-memory","settings-voice","settings-skills","settings-tools","settings-notices","settings-help")) {
            listOf("轻盈办公","纸间留白","流光玻璃").forEach {label->
                val card=tagged("skin-$label").boundsInRoot
                val text=nodes.first {nodeText(it)==label}.boundsInRoot
                check(text.left>=card.left+9*scale&&text.right<=card.right-9*scale&&text.bottom<card.bottom-20*scale) {"Theme label is outside its padded card: $label"}
            }
        }
    }
    private fun allNodes(scene:ImageComposeScene):List<SemanticsNode> {
        fun descendants(node:SemanticsNode):List<SemanticsNode> = listOf(node)+node.children.flatMap(::descendants)
        return scene.semanticsOwners.flatMap {descendants(it.unmergedRootSemanticsNode)}
    }
    private fun nodeText(node:SemanticsNode)=node.config.getOrNull(SemanticsProperties.Text)?.joinToString(""){it.text}
    private fun snapshot(scene:ImageComposeScene):ByteArray=scene.render(System.nanoTime()).use {image->image.encodeToData()!!.use {it.bytes}}
    private fun click(scene:ImageComposeScene,point:Offset) {
        scene.sendPointerEvent(PointerEventType.Move,point)
        scene.sendPointerEvent(PointerEventType.Press,point,button=PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release,point,button=PointerButton.Primary)
    }
}
