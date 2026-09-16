package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.platform.DesktopHost
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun HomeView(c:DesktopController) {
    val d=LocalDesktopDesign.current;val colors=MaterialTheme.colorScheme
    val pending=c.decisions.values.filter {it.profile==c.profile}
    val running=c.runs.values.count {it.session.profile==c.profile}
    val artifacts=c.recentArtifacts.filter {it.profile==c.profile&&artifactMatchesProject(it,c.project)}
    val sessions=c.sessions.filter {c.project==null||it.workspacePath==c.project?.primaryPath}
        .sortedWith(compareByDescending<HermesSession>{it.isPinned}.thenByDescending {parseDesktopInstant(it.updatedAt)})
    DesktopPage(maxContentWidth=1440.dp,tag="home-content") {
        Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(colors.primary.copy(alpha=if(d.glass).13f else .065f),colors.primary.copy(alpha=.012f))),d.shape).padding(horizontal=26.dp,vertical=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 · EEEE",desktopLocale())),fontSize=12.sp,color=colors.onSurfaceVariant)
                Text("${tr(when(LocalTime.now().hour){in 5..10->"早上好";in 11..13->"中午好";in 14..18->"下午好";else->"晚上好"})}，${c.nickname}",fontSize=28.sp,lineHeight=39.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(tr("想法在这里开始，事情在这里完成。"),fontSize=15.sp,color=colors.onSurfaceVariant)
            }
            AnimatedMascot(c,Modifier.size(100.dp).semantics {testTag="home-mascot"})
        }
        HomeComposer(c)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("document","写一份文档","帮我写一份文档。先和我确认用途、读者和需要包含的信息。"),
                Triple("search","研究一个问题","帮我研究一个问题。先确认研究范围，再查证来源，整理结论与不确定之处。"),
                Triple("tasks","拆解一个计划","帮我把目标拆成可以执行的计划。先了解我的目标、截止时间和已有资源。"),
                Triple("folder","分析文件","帮我分析附件，提取关键信息，标出需要进一步确认的内容。")
            ).forEach {(icon,label,prompt)->
                Row(Modifier.background(colors.surfaceVariant.copy(alpha=.8f),d.controlShape).desktopClick {c.addHomePrompt(prompt)}.padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Glyph(icon,Modifier.size(15.dp),colors.primary);Text(tr(label),fontSize=12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            HomeMetric("inbox","待你确认",pending.size,Modifier.weight(1f),pending.firstOrNull()?.request?.title?:"需要你决定的事会出现在这里"){c.navigate(Page.TASKS)}
            HomeMetric("tasks","正在进行",running,Modifier.weight(1f),if(running>0)"查看进度或追加要求"else"交办任务后，随时回来查看"){c.navigate(Page.TASKS)}
            HomeMetric("files","近期文件",artifacts.size,Modifier.weight(1f),"找到每一次对话的成果"){c.showRecentFiles()}
        }
        if(pending.isNotEmpty())SectionCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Glyph("inbox",Modifier.size(22.dp),colors.primary)
                Column(Modifier.weight(1f)) {Text(tr("有事项等你确认"),fontWeight=FontWeight.Medium);SubtleText(pending.first().request.title)}
                SmallButton("去处理",{c.navigate(Page.TASKS)},true)
            }
        }
        BoxWithConstraints {
            @Composable fun RecentConversations(modifier:Modifier) {
                SectionCard(modifier) {
                    SectionTitle("继续上次的事",if(c.sessionsLoading)"正在同步会话…"else if(c.sessionsLoadError!=null)"同步未完成 · 已有内容仍可打开"else "${sessions.size} 段会话 · ${c.project?.name?:"全部项目"}",trailing={DeskTextButton(onClick={c.navigate(Page.SESSIONS)}){Text(tr("全部会话 →"))}})
                    if(c.sessionsLoading)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    c.sessionsLoadError?.let {SyncProblem(c,"暂时无法同步会话",it,c.sessionsLoading){c.refresh()}}
                    when {
                        sessions.isNotEmpty()->Column(Modifier.padding(horizontal=12.dp,vertical=4.dp)) {sessions.take(5).forEachIndexed {index,s->
                            SessionRow(s.copy(preview=c.sessionSummaries[s.scopedId]?:s.preview),{c.openSession(s)},showDivider=index<minOf(5,sessions.size)-1) {
                                Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    SubtleText(friendlyTime(s.updatedAt))
                                    if(s.scopedId in c.unread)StatusPill("新回复")else if(s.scopedId in c.runs)StatusPill("执行中")
                                }
                            }
                        }}
                        c.sessionsLoading->LoadingRows()
                        c.sessionsLoadError==null->PanelEmpty("history","从一个想法开始","在上方写下任务，对话和进展会保留在这里。")
                    }
                }
            }
            @Composable fun RecentFiles(modifier:Modifier) {
                SectionCard(modifier) {
                    SectionTitle("最近成果",if(c.artifactsIndexing)c.artifactsIndexProgress else "对话中的文档与文件",trailing={DeskTextButton(onClick={c.showRecentFiles()}){Text(tr("全部文件 →"))}})
                    if(c.artifactsIndexing)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    c.artifactsIndexError?.let {SyncProblem(c,"文件整理尚未完成",it,c.artifactsIndexing){c.syncArtifactIndex()}}
                    if(artifacts.isEmpty()&&c.artifactsIndexError==null) {
                        if(c.artifactsIndexing)LoadingRows()else PanelEmpty("document","让对话留下成果","生成的文件会自动整理到这里，点击即可继续查看。")
                    } else Column(Modifier.padding(horizontal=10.dp,vertical=4.dp)) {artifacts.take(5).forEach {a->
                        Row(Modifier.fillMaxWidth().desktopClick {c.openArtifact(a)}.padding(horizontal=6.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            FileEmblem(a.name,Modifier.size(36.dp))
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Hint(a.name){Text(a.name,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                                SubtleText(a.sessionTitle.ifBlank {"来自工作空间"})
                            }
                        }
                    }}
                    val drafts=c.openDocuments.values.count {it.profile==c.profile&&c.edits[c.documentKey(it)]?.let {text->text!=it.document.content}==true}
                    if(drafts>0)DeskTextButton(onClick={c.fileSection="本机草稿";c.setFilesPanel(true);c.navigate(Page.FILES)}){Text("$drafts 份文档有本机草稿 →")}
                }
            }
            if(maxWidth>=820.dp)Row(horizontalArrangement=Arrangement.spacedBy(d.sectionGap)) {RecentConversations(Modifier.weight(1.3f));RecentFiles(Modifier.weight(1f))}
            else Column(verticalArrangement=Arrangement.spacedBy(d.sectionGap)) {RecentConversations(Modifier.fillMaxWidth());RecentFiles(Modifier.fillMaxWidth())}
        }
        Spacer(Modifier.fillMaxWidth().height(8.dp).semantics {testTag="home-end"})
    }
}

@Composable internal fun HomeComposer(c:DesktopController) {
    val key=c.homeDraftKey();val draft=c.drafts[key].orEmpty();val attached=c.attachments[key].orEmpty()
    val loading=(c.attachmentLoads[key]?:0)>0
    val colors=MaterialTheme.colorScheme;val shape=LocalDesktopDesign.current.composerShape
    var field by remember(key){mutableStateOf(TextFieldValue(draft,TextRange(draft.length)))}
    var focused by remember{mutableStateOf(false)}
    var fileHover by remember(key){mutableStateOf(false)}
    LaunchedEffect(draft){if(field.text!=draft)field=TextFieldValue(draft,TextRange(draft.length))}
    Column(Modifier.fillMaxWidth().composerFileDrop(c,key){fileHover=it}.background(colors.surface,shape).border(if(focused||fileHover)1.5.dp else 1.dp,if(focused||fileHover)colors.primary.copy(alpha=.7f)else colors.outline,shape).padding(18.dp).semantics {testTag="home-composer"},verticalArrangement=Arrangement.spacedBy(14.dp)) {
        BasicTextField(field,{field=it;c.setDraft(key,it.text)},modifier=Modifier.fillMaxWidth().heightIn(min=66.dp,max=180.dp).onFocusChanged {focused=it.isFocused}.onPreviewKeyEvent {e->
            when {
                isFilePaste(e)&&field.composition==null&&c.pasteFiles(key)->true
                shouldSendOnKey(c.sendOnEnter,e,field.composition!=null)->{c.startFromHome();true}
                shouldInsertNewlineOnKey(c.sendOnEnter,e,field.composition!=null)->{field=insertComposerNewline(field);c.setDraft(key,field.text);true}
                else->false
            }
        }.semantics {contentDescription=tr("交办新任务")},textStyle=MaterialTheme.typography.bodyLarge.copy(color=colors.onSurface,fontSize=17.sp,lineHeight=27.sp),cursorBrush=SolidColor(colors.primary),decorationBox={inner->Box {if(draft.isEmpty())Text(tr("想把什么事交给 ${c.hermesName}？"),fontSize=17.sp,color=colors.onSurfaceVariant);inner()}})
        if(fileHover)Text(tr("松开鼠标，添加到新任务"),color=colors.primary,fontSize=13.sp)
        c.attachmentErrors[key]?.let {Text(it,color=colors.error,fontSize=12.sp,maxLines=4)}
        if(attached.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {attached.forEach {a->
            ComposerAttachment(c,key,a)
        }}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            DeskTextButton(onClick={c.chooseFiles(key)},enabled=!loading) {Glyph("add",Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(tr(if(loading)"附件准备中…"else"添加文件"))}
            DeskTextButton(onClick={c.voice.open()}){Text(tr("语音对话"))}
            Spacer(Modifier.weight(1f))
            Hint(if(c.sendOnEnter)"Enter 发送 · Shift Enter 换行"else"${shortcutKey()} Enter 发送") {
                SmallButton(if(c.creatingSession)"正在开始…"else"开始任务  ↑",{c.startFromHome()},true,enabled=!c.creatingSession&&!loading&&(draft.isNotBlank()||attached.isNotEmpty()))
            }
        }
    }
}

@Composable private fun HomeMetric(icon:String,label:String,count:Int,modifier:Modifier,detail:String,action:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Column(modifier.background(colors.surface,LocalDesktopDesign.current.shape).border(1.dp,colors.outline.copy(alpha=.6f),LocalDesktopDesign.current.shape).desktopClick(onClick=action).padding(horizontal=18.dp,vertical=15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Glyph(icon,Modifier.size(17.dp),colors.primary);Text(tr(label),fontSize=13.sp);Spacer(Modifier.weight(1f));Text(count.toString(),fontSize=23.sp,fontWeight=FontWeight.SemiBold)
        }
        SubtleText(detail)
    }
}

@Composable internal fun LoadingRows() {
    Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {repeat(3){i->
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(.52f+i*.09f).height(12.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.065f),RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(.82f-i*.08f).height(8.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.04f),RoundedCornerShape(4.dp)))
        }
    }}
}

@Composable internal fun SyncProblem(c:DesktopController,title:String,detail:String,loading:Boolean=false,retry:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp).background(colors.error.copy(alpha=.045f),RoundedCornerShape(10.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Glyph("refresh",Modifier.size(18.dp),colors.error);Text(tr(title),fontWeight=FontWeight.Medium)}
        Text(tr("这次读取未完成。已加载的内容和草稿会保留，可以重试或查看具体原因。"),fontSize=12.sp,lineHeight=19.sp,color=colors.onSurfaceVariant)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {SmallButton(if(loading)"正在重试…"else"重新同步",retry,enabled=!loading);DeskTextButton(onClick={c.showDetails(title,detail)}){Text(tr("查看原因"))}}
    }
}
