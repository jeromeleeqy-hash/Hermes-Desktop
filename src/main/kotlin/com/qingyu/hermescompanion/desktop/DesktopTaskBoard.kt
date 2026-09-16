package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.ui.format.conversationPreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun TaskBoard(c:DesktopController,onDecision:(String)->Unit,modifier:Modifier=Modifier) {
    val requests=c.decisions.entries.filter {it.value.profile==c.profile}
    val runs=c.runs.values.filter {it.session.profile==c.profile}
    val finished=c.completions.filter {it.sessionId.startsWith(c.profile+"::")}
    if(requests.isEmpty()&&runs.isEmpty()&&finished.isEmpty()) {
        SectionCard(Modifier.fillMaxWidth()) {
            PanelEmpty("check","现在没有待处理事项","把一件事交给 Hermes，执行进度和结果会同步到这里。",Modifier.padding(vertical=14.dp))
            Row(Modifier.fillMaxWidth().padding(bottom=18.dp),horizontalArrangement=Arrangement.Center) {SmallButton("开始一段对话",{c.newSession()},true);Spacer(Modifier.width(10.dp));SmallButton("查看定时任务",{c.taskTab="定时任务"})}
        }
        return
    }
    val fontScale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    val boardHeight=(120+maxOf(requests.size,runs.size,finished.size)*150).coerceIn(320,680).dp*fontScale
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(max=boardHeight).fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            TaskLane("待你确认",requests.size,"inbox","暂时没有待确认事项","需要你审批或补充说明时，会出现在这里。",Modifier.weight(1f)) {
                items(requests,key={it.key}) {(id,value)->
                    TaskItem(Modifier.desktopClick(shape=LocalDesktopDesign.current.shape){onDecision(id)}) {
                        SubtleText(when(value.request.type){AgentRequestType.APPROVAL->"待审批";AgentRequestType.CLARIFICATION->"待澄清";else->"待处理"})
                        Text(value.request.title,fontWeight=FontWeight.Medium,fontSize=14.sp,lineHeight=21.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
                        SubtleText(value.session.title)
                        DeskTextButton(onClick={onDecision(id)}){Text(tr("查看并处理"))}
                    }
                }
            }
            TaskLane("正在执行",runs.size,"clock","当前没有运行任务","在对话中交代一件事，进度会同步到这里。",Modifier.weight(1f)) {
                items(runs,key={it.session.scopedId}) {run->
                    TaskItem {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary,androidx.compose.foundation.shape.CircleShape))
                            SubtleText(run.status)
                            if(run.failed)StatusPill("需要重试",error=true)
                        }
                        Text(run.session.title,fontWeight=FontWeight.Medium,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                        run.tools.lastOrNull()?.let {SubtleText(toolLabel(it.name),maxLines=2)}
                        SubtleText("开始于 " + java.time.Instant.ofEpochMilli(run.started).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            DeskTextButton(onClick={c.openSession(run.session)}){Text(tr("查看对话"))}
                            Spacer(Modifier.weight(1f))
                            if(run.recovering)SmallButton("重试恢复",{c.retryRecovery()})
                            SmallButton("停止",{c.stop(run.session)},enabled=!run.stopping)
                        }
                    }
                }
            }
            TaskLane("最近完成",finished.size,"check","完成的任务会留在这里","可以回看结果，也可以继续原来的对话。",Modifier.weight(1f)) {
                items(finished) {result->
                    val session=(c.sessions+c.cronSessions).firstOrNull {it.scopedId==result.sessionId}
                    TaskItem {
                        SubtleText(Instant.ofEpochMilli(result.completedAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd · HH:mm")))
                        Text(result.title,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        SubtleText(conversationPreview(visibleUserText(result.summary)),maxLines=4)
                        val artifacts=c.recentArtifacts.filter {c.profile==it.profile&&c.profile+"::"+it.sessionId==result.sessionId&&result.artifacts.any {a->a.path==it.path||a.path==it.sourcePath}}
                        artifacts.take(3).forEach {artifact->DeskTextButton(onClick={c.openArtifact(artifact)}){Glyph(fileGlyph(artifact.name),Modifier.size(15.dp));Spacer(Modifier.width(6.dp));Text(artifact.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp)}}
                        DeskTextButton(onClick={if(session!=null)c.openSession(session)else {val p=c.profile;c.request(p,{it.sessionForProfile(result.sessionId.removePrefix(p+"::"),p)}){found->if(c.profile==p){if(found!=null)c.openSession(found)else c.notice="原会话已不可用，保存的摘要仍可查看。"}}}}){Text(tr("查看结果"))}
                    }
                }
            }
        }
    }
}

@Composable private fun TaskLane(title:String,count:Int,icon:String,emptyTitle:String,emptyDetail:String,modifier:Modifier,content:LazyListScope.()->Unit) {
    SectionCard(modifier.fillMaxHeight().semantics {testTag="lane-$title"}) {
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Glyph(icon,Modifier.size(18.dp),MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr(title),Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            CountBadge(count)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.55f))
        if(count==0) {
            // Let text keep its natural height. The body scrolls only when the viewport is short.
            val scroll=rememberScrollState()
            Box(Modifier.weight(1f).fillMaxWidth().semantics {testTag="lane-empty-$title"}) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll),verticalArrangement=Arrangement.Center) {
                    PanelEmpty(icon,emptyTitle,emptyDetail)
                }
                if(scroll.maxValue>0)VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(5.dp))
            }
        }
        else Box(Modifier.weight(1f)) {
            val state=rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().padding(horizontal=12.dp),state=state,contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
            VerticalScrollbar(rememberScrollbarAdapter(state),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(5.dp))
        }
    }
}

@Composable private fun TaskItem(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    val colors=MaterialTheme.colorScheme;val shape=LocalDesktopDesign.current.shape
    Column(modifier.fillMaxWidth().background(colors.background.copy(alpha=.6f),shape).border(1.dp,colors.outline.copy(alpha=.65f),shape).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
}
