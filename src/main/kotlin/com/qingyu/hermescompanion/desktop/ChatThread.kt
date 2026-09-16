@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.data.ChatInsightParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun ChatView(c:DesktopController) {
    val s=c.currentSession?:return;val key=s.scopedId;val colors=MaterialTheme.colorScheme;val design=LocalDesktopDesign.current
    val turns=displayTurns(c.messages[key].orEmpty()).filter {it.message.content.isNotBlank()||it.message.images.isNotEmpty()||it.message.isStreaming||it.message.reasoning.isNotBlank()}
    val saved=remember(key){c.readingPositions[key]}
    val scroll=key(key){rememberLazyListState(saved?.index?:0,saved?.offset?:0)}
    val scope=rememberCoroutineScope()
    var follow by remember(key){mutableStateOf(saved?.following?:true)}
    var programmatic by remember(key){mutableStateOf(false)}
    var restored by remember(key){mutableStateOf(saved==null)}
    val nearBottom by remember(scroll){derivedStateOf {!scroll.canScrollForward}}
    val currentFollow by rememberUpdatedState(follow)
    DisposableEffect(key,scroll) {onDispose {
        val item=scroll.layoutInfo.visibleItemsInfo.firstOrNull()
        c.rememberReading(key,ReadingPosition(item?.key as? String?:"",scroll.firstVisibleItemIndex,scroll.firstVisibleItemScrollOffset,currentFollow))
    }}
    suspend fun scrollToEnd(animate:Boolean=false) {
        if(turns.isEmpty())return
        programmatic=true
        try {
            val index=turns.lastIndex+if(c.hasOlder(s))1 else 0
            if(animate)scroll.animateScrollToItem(index)else scroll.scrollToItem(index)
            scroll.scrollBy((scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.size?:0).toFloat())
        }finally {programmatic=false}
    }
    LaunchedEffect(key,turns.size) {
        if(!restored&&turns.isNotEmpty()) {
            val index=turns.indexOfFirst {it.message.id==saved?.messageId}
            programmatic=true
            try {scroll.scrollToItem(if(index>=0)index+if(c.hasOlder(s))1 else 0 else (saved?.index?:0).coerceAtMost(turns.lastIndex),saved?.offset?:0)}
            finally {programmatic=false;restored=true}
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow {Triple(scroll.firstVisibleItemIndex,scroll.firstVisibleItemScrollOffset,scroll.isScrollInProgress)}.collect {
            if(!programmatic&&scroll.isScrollInProgress)follow=!scroll.canScrollForward
        }
    }
    LaunchedEffect(key,turns.lastOrNull()?.message,turns.size,c.filesPanelOpen,c.documentSplit,c.panelWidth,c.textScale) {
        if(restored&&follow&&c.focusMessageId==null)scrollToEnd()
    }
    LaunchedEffect(key,c.focusMessageId,turns.size) {
        c.focusMessageId?.let {id->val index=turns.indexOfFirst {id in it.sourceIds};if(index>=0){follow=false;scroll.scrollToItem(index+if(c.hasOlder(s))1 else 0);c.focusMessageId=null}}
    }
    Column(Modifier.fillMaxSize().semantics {testTag="chat-content"}) {
        if(c.showOutline)ChatOutline(c,turns)
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
            Box(Modifier.widthIn(max=if(design.paper)920.dp else 1120.dp).fillMaxSize()) {
                if(turns.isEmpty()&&c.messageLoading[key]==true)LoadingRows()
                else if(turns.isEmpty()&&c.messageErrors[key]==null)Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    Avatar(c,false,Modifier.size(58.dp));Spacer(Modifier.height(18.dp))
                    Text(tr("这一次，我们一起完成什么？"),fontSize=25.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp));SubtleText("写下目标、添加资料，剩下的边做边聊。",maxLines=2)
                    Spacer(Modifier.height(18.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SmallButton("添加文件",{c.chooseFiles(s.scopedId)});SmallButton("插入提示词",{c.snippetsOpen=true})}
                }
                if(turns.isNotEmpty())LazyColumn(state=scroll,modifier=Modifier.fillMaxSize().padding(horizontal=24.dp),contentPadding=PaddingValues(top=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                    if(c.hasOlder(s))item(key="older"){DeskTextButton(onClick={c.loadOlder(s)}){Text(tr("加载更早的消息"))}}
                    items(turns,key={it.message.id}) {turn->ChatMessageRow(c,s,turn)}
                }
                if(turns.isNotEmpty())VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(5.dp))
                c.messageErrors[key]?.let {SyncProblem(c,"消息暂时无法读取",it){c.openSession(s)}}
            }
        }
        if(!nearBottom)Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=2.dp),horizontalArrangement=Arrangement.End) {
            SmallButton(if(c.runs.containsKey(key))"有新内容 · 回到底部"else"跳到底部 ↓",{follow=true;c.focusMessageId=null;scope.launch {scrollToEnd(!c.reduceMotion)}})
        }
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=if(design.paper)980.dp else 1160.dp).fillMaxWidth()) {
                ChatRunStatus(c,s)
                QueuedDrafts(c,s)
                val pending=c.decisions.values.filter {it.session.scopedId==key}
                var showDecision by remember(key){mutableStateOf(false)}
                if(pending.isNotEmpty()) {
                    DeskTextButton(onClick={showDecision=true},modifier=Modifier.padding(horizontal=20.dp)){Glyph("inbox",Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("待你确认 · "+pending.first().request.title,maxLines=2)}
                    if(showDecision)HermesDialog(onDismissRequest={showDecision=false},title={Text("待你确认")},text={DecisionPanel(c,pending.first(),Modifier.width(552.dp).height(400.dp))},confirmButton={DeskTextButton(onClick={showDecision=false}){Text("稍后处理")}})
                }else LaunchedEffect(Unit){showDecision=false}
                ChatComposer(c,s)
            }
        }
    }
}

@Composable private fun ChatOutline(c:DesktopController,turns:List<DisplayTurn>) {
    var open by remember {mutableStateOf(false)}
    val questions=turns.filter {it.message.role==MessageRole.USER&&outlineText(it.message).isNotBlank()}
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha=.035f)).padding(horizontal=20.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Glyph("outline",Modifier.size(16.dp),MaterialTheme.colorScheme.primary)
        Box {
            DeskTextButton(onClick={open=true}){Text(tr("会话大纲")+" · "+questions.size+" 个问题",fontSize=12.sp);Glyph("chevron-down",Modifier.size(13.dp))}
            DeskMenu(open,{open=false},modifier=Modifier.width(440.dp).heightIn(max=400.dp)) {
                questions.forEachIndexed {i,turn->DeskMenuItem(text={Text((i+1).toString()+". "+outlineText(turn.message),maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=13.sp)},onClick={open=false;c.focusMessageId=turn.message.id})}
                if(questions.isEmpty())Text(tr("第一条消息会出现在这里"),Modifier.padding(16.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        SubtleText("点击定位到原文")
    }
}

@Composable private fun ChatMessageRow(c:DesktopController,s:HermesSession,turn:DisplayTurn) {
    val m=turn.message;val colors=MaterialTheme.colorScheme;val design=LocalDesktopDesign.current
    if(m.role==MessageRole.USER) {
        val presented=remember(m.content,m.images){presentUserText(m.content,m.images.map {it.source})}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End),verticalAlignment=Alignment.Top) {
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            presented.notices.forEach {MessageNoticeRow(c,it)}
            if(presented.body.isNotBlank()||m.images.isNotEmpty())Surface(shape=if(design.paper)RoundedCornerShape(6.dp)else RoundedCornerShape(12.dp,12.dp,4.dp,12.dp),color=colors.primary.copy(alpha=if(design.glass).09f else .075f),modifier=Modifier.widthIn(max=680.dp)) {
                Column(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    var expanded by remember(m.id){mutableStateOf(false)}
                    SelectionContainer {Text(presented.body,lineHeight=24.sp,maxLines=if(expanded)Int.MAX_VALUE else 12,overflow=TextOverflow.Ellipsis)}
                    if(presented.body.length>600||presented.body.lines().size>12)DeskTextButton(onClick={expanded=!expanded}){Text(tr(if(expanded)"收起"else"展开完整消息"),fontSize=12.sp)}
                    m.images.forEach {ChatImageView(c,it.source,s.profile)}
                }
            }
        }
        Avatar(c,true,Modifier.padding(top=2.dp).size(32.dp).semantics {testTag="chat-user-avatar"})
        }
    }else {
        val presented=remember(m.content){presentAssistantText(m.content)}
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Avatar(c,false,Modifier.padding(top=2.dp).size(32.dp))
            Column(Modifier.weight(1f).then(if(design.paper)Modifier.background(colors.background.copy(alpha=.40f),RoundedCornerShape(6.dp)).border(1.dp,colors.outline.copy(alpha=.55f),RoundedCornerShape(6.dp)).padding(18.dp)else Modifier),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text(c.hermesName,fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=colors.onSurfaceVariant)
                    if(m.createdAt.isNotBlank())Text(friendlyTime(m.createdAt),fontSize=11.sp,color=colors.onSurfaceVariant)
                }
                val activities=(c.runs[s.scopedId]?.takeIf {it.assistantId in turn.sourceIds}?.tools.orEmpty()+turn.sourceIds.flatMap {c.completedActivities[it].orEmpty()}).distinctBy {it.id}
                if(c.activityMode!="answer"&&(activities.isNotEmpty()||m.reasoning.isNotBlank()&&c.settings?.conversation?.showReasoning!=false)) {
                    Disclosure("执行记录",if(activities.isNotEmpty())activities.size.toString()+" 项工具活动"else"查看模型提供的过程说明",initiallyOpen=c.activityMode=="expanded") {
                        if(m.reasoning.isNotBlank()&&c.settings?.conversation?.showReasoning!=false)SelectionContainer {Text(m.reasoning,fontSize=13.sp,lineHeight=21.sp,color=colors.onSurfaceVariant)}
                        activities.forEach {activity->
                            Row(Modifier.fillMaxWidth().background(colors.primary.copy(alpha=.035f),RoundedCornerShape(8.dp)).padding(9.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Glyph(if(activity.status==ToolStatus.FAILED)"alert"else if(activity.status==ToolStatus.COMPLETED)"check"else"clock",Modifier.size(15.dp),if(activity.status==ToolStatus.FAILED)colors.error else colors.primary)
                                Text(toolLabel(activity.name),Modifier.weight(1f),fontSize=12.sp)
                                DeskTextButton(onClick={c.showDetails(toolLabel(activity.name),activity.preview.ifBlank {"没有额外输出"})}){Text(tr("详情"),fontSize=11.sp)}
                            }
                        }
                    }
                }
                presented.notices.forEach {MessageNoticeRow(c,it)}
                if(presented.body.isNotBlank())QuoteSelection("引用到输入框",{quote->val before=c.drafts[s.scopedId].orEmpty();c.setDraft(s.scopedId,before+(if(before.isBlank())""else"\n\n")+"> "+quote.replace("\n","\n> ")+"\n\n")}) {
                    SelectionContainer {
                        if(m.isStreaming)Text(presented.body,Modifier.fillMaxWidth(),lineHeight=25.sp)
                        else Markdown(visibleAssistantText(presented.body)){target->
                            if(target.startsWith("https://")||target.startsWith("http://"))runCatching {DesktopFiles.openLink(target)}.onFailure {c.error=it.message}
                            else c.openDocument(target,s,s.profile)
                        }
                    }
                }
                m.images.forEach {ChatImageView(c,it.source,s.profile)}
                if(!m.isStreaming&&presented.body.isNotBlank()) {
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Hint("复制回答"){DeskIconButton(onClick={DesktopFiles.copy(visibleAssistantText(presented.body));c.notice="回答已复制"}){Glyph("copy",Modifier.size(16.dp),colors.onSurfaceVariant)}}
                        Hint("朗读回答"){DeskIconButton(onClick={c.speak(visibleAssistantText(presented.body))}){Glyph("audio",Modifier.size(17.dp),colors.onSurfaceVariant)}}
                        if(c.voice.phase==VoicePhase.SPEAKING)Hint("停止朗读"){DeskIconButton(onClick={c.voice.pause()}){Glyph("stop",Modifier.size(16.dp))}}
                    }
                    val citations=remember(m.content){findCitationSources(m.content)}
                    if(citations.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){citations.forEachIndexed {index,cite->DeskAttachChip(onClick={runCatching {DesktopFiles.openLink(cite.url)}.onFailure {c.error=it.message}},label={Text((index+1).toString()+" · "+cite.host)})}}
                    ChatInsightParser.artifactsFromText(m.content).forEach {a->
                        Row(Modifier.fillMaxWidth().background(colors.surface,RoundedCornerShape(10.dp)).border(1.dp,colors.outline,RoundedCornerShape(10.dp))
                            .desktopClick {c.openDocument(a.path,s,s.profile)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Glyph("document",Modifier.size(22.dp),colors.primary)
                            Column(Modifier.weight(1f)) {Text(a.name,fontWeight=FontWeight.Medium,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis);SubtleText("预览、编辑或继续讨论")}
                            Glyph("chevron-right",Modifier.size(16.dp),colors.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MessageNoticeRow(c:DesktopController,notice:MessageNotice) {
    val color=if(notice.problem)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().desktopClick(fillHover=false){c.showDetails(notice.label,notice.raw)}.padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Glyph(if(notice.problem)"alert"else"model",Modifier.size(15.dp),color)
        Text(notice.label,Modifier.weight(1f),fontSize=12.sp,color=color)
        Glyph("chevron-right",Modifier.size(13.dp),color)
    }
}

@Composable private fun ChatRunStatus(c:DesktopController,s:HermesSession) {
    val run=c.runs[s.scopedId]?:return
    var now by remember(run.started){mutableStateOf(System.currentTimeMillis())}
    LaunchedEffect(run.started){while(true){delay(1000);now=System.currentTimeMillis()}}
    val elapsed=((now-run.started)/1000).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(!c.reduceMotion)CircularProgressIndicator(Modifier.size(13.dp),strokeWidth=1.5.dp)else Glyph("clock",Modifier.size(14.dp))
        Text(tr(run.status),Modifier.weight(1f),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(elapsed.toString()+"s",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(run.recovering)DeskTextButton(onClick={c.retryRecovery()}){Text(tr("重试恢复"),fontSize=12.sp)}
        DeskTextButton(onClick={c.assistantPanel=true;c.focusMode=false}){Text(tr("查看进度"),fontSize=12.sp)}
    }
}

@Composable private fun QueuedDrafts(c:DesktopController,s:HermesSession) {
    val waiting=c.queued[s.scopedId].orEmpty()
    if(waiting.isEmpty())return
    var expanded by remember(s.scopedId){mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=.045f),RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Glyph("queue",Modifier.size(16.dp),MaterialTheme.colorScheme.primary)
            DeskTextButton(onClick={expanded=!expanded}){Text(waiting.size.toString()+" 条消息排队中",fontSize=12.sp)}
            Spacer(Modifier.weight(1f))
            if(!c.runs.containsKey(s.scopedId))SmallButton("继续发送",{c.resumeQueue(s)})
        }
        if(expanded)waiting.forEach {q->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(q.prompt.ifBlank {q.attachments.joinToString {it.name}},Modifier.weight(1f),fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                DeskTextButton(onClick={c.removeQueued(s.scopedId,q.id,restore=true)}){Text(tr("改写"),fontSize=12.sp)}
                Hint("移出队列"){DeskIconButton(onClick={c.removeQueued(s.scopedId,q.id)}){Glyph("close",Modifier.size(14.dp))}}
            }
        }
    }
}
