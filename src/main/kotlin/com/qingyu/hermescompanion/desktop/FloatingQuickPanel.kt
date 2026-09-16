@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.MessageRole
import com.qingyu.hermescompanion.model.scopedId

@Composable internal fun FloatingQuickPanel(c:DesktopController) {
    val q=c.companion;val s=q.session;val colors=MaterialTheme.colorScheme
    val shape=RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxSize().padding(24.dp).dropShadow(shape,Shadow(radius=16.dp,color=Color.Black.copy(alpha=.14f),offset=DpOffset(0.dp,2.dp))).clip(shape).background(colors.surface)
        .border(1.dp,colors.outline.copy(alpha=.6f),shape).arrive(q.panelOpen).semantics {testTag="floating-quick-panel"}) {
        Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=10.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Image(painterResource("icon.png"),"Hermes",Modifier.size(30.dp))
            Text("随时问 Hermes",Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Hint("打开主窗口"){DeskIconButton(onClick=q::openInMain){Glyph("expand",Modifier.size(17.dp))}}
            Hint("收起，保留草稿"){DeskIconButton(onClick=q::close){Glyph("close",Modifier.size(17.dp))}}
        }
        HorizontalDivider(color=colors.outline.copy(alpha=.6f))
        if(!c.connected||q.failure!=null)Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text(q.failure?:"请先连接 Hermes 网关。",fontSize=13.sp,color=colors.error)
            if(q.pendingFileCount>0)Caption("${q.pendingFileCount} 个文件等待添加")
            SmallButton(if(c.connected)"重试"else"打开连接设置",q::retry)
        }
        if(q.preparing)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(s==null||q.preparing) {
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Caption(if(q.preparing)"正在打开日常助理…"else"连接后即可开始提问")}
        }else {
            var choosing by remember {mutableStateOf(false)}
            var query by remember {mutableStateOf("")}
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    DeskTextButton(onClick={choosing=true}){Glyph("history",Modifier.size(15.dp));Spacer(Modifier.width(6.dp));Text(s.title,Modifier.widthIn(max=230.dp),fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Glyph("chevron-down",Modifier.size(13.dp))}
                    DeskMenu(choosing,{choosing=false},modifier=Modifier.width(310.dp).heightIn(max=350.dp)) {
                        CompactInput(query,{query=it},"搜索会话",Modifier.padding(8.dp))
                        val list=c.sessions.filter {it.profile==c.profile&&(query.isBlank()||it.title.contains(query,true))}.sortedByDescending {it.title=="日常助理"}.take(60)
                        list.forEach {row->DeskMenuItem(text={Text(row.title,maxLines=1,overflow=TextOverflow.Ellipsis)},leadingIcon={Glyph("history",Modifier.size(16.dp))},trailingIcon={if(row.scopedId==s.scopedId)Glyph("check",Modifier.size(15.dp))},onClick={q.select(row);choosing=false})}
                        if(list.isEmpty())Caption("没有匹配的会话",Modifier.padding(16.dp))
                    }
                }
                if(q.voicePanel)DeskTextButton(onClick={c.voice.dismiss();q.open()}){Text("文字提问",fontSize=12.sp)}
                else Hint("框选截图"){DeskIconButton(onClick={q.open(CompanionAction.CAPTURE)}){Glyph("capture",Modifier.size(18.dp))}}
            }
            if(q.voicePanel&&c.voice.active)Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp)){VoicePanel(c)}
            else {
                val key=s.scopedId;val rows=displayTurns(c.messages[key].orEmpty()).filter {it.message.content.isNotBlank()||it.message.isStreaming}.takeLast(24)
                val scroll=rememberLazyListState();var follow by remember(key){mutableStateOf(true)}
                LaunchedEffect(scroll){snapshotFlow {scroll.isScrollInProgress to scroll.canScrollForward}.collect {(moving,more)->if(moving)follow=!more}}
                LaunchedEffect(key,rows.lastOrNull()?.message?.content,rows.size){if(follow&&rows.isNotEmpty())scroll.scrollToItem(rows.lastIndex)}
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if(rows.isEmpty())Column(Modifier.fillMaxSize().padding(26.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("想到什么，随时问",fontSize=21.sp,fontWeight=FontWeight.Medium)
                        Spacer(Modifier.height(10.dp));Caption("拖入文件，或截取屏幕的一部分",Modifier.padding(horizontal=8.dp))
                    }else LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),state=scroll,contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        items(rows,key={it.message.id}){turn->val m=turn.message
                            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(if(m.role==MessageRole.USER)c.nickname else c.hermesName,fontSize=11.sp,color=colors.onSurfaceVariant)
                                SelectionContainer {Box(Modifier.fillMaxWidth().then(if(m.role==MessageRole.USER)Modifier.background(colors.primary.copy(alpha=.07f),RoundedCornerShape(11.dp)).padding(12.dp)else Modifier)) {
                                    if(m.content.isBlank())Text("正在回应…",fontSize=13.sp,color=colors.onSurfaceVariant)else Markdown(visibleUserText(m.content),DesktopFiles::openLink)
                                }}
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(5.dp))
                }
                val pending=c.decisions.values.firstOrNull {it.session.scopedId==key}
                var showDecision by remember(key){mutableStateOf(false)}
                if(pending!=null) {
                    SmallButton("有问题需要你回答 · 点击选择",{showDecision=true})
                    if(showDecision)HermesDialog(onDismissRequest={showDecision=false},title={Text("待你确认")},text={DecisionPanel(c,pending,Modifier.width(430.dp).height(340.dp))},confirmButton={DeskTextButton(onClick={showDecision=false}){Text("稍后处理")}})
                }else LaunchedEffect(Unit){showDecision=false}
                QuickComposer(c)
            }
        }
    }
}

@Composable private fun QuickComposer(c:DesktopController) {
    val q=c.companion;val s=q.session?:return;val key=s.scopedId;val colors=MaterialTheme.colorScheme
    val text=c.drafts[key].orEmpty();val files=c.attachments[key].orEmpty();val preparing=(c.attachmentLoads[key]?:0)>0
    var field by remember(key){mutableStateOf(TextFieldValue(text,TextRange(text.length)))}
    var hover by remember {mutableStateOf(false)};val focus=remember {FocusRequester()}
    LaunchedEffect(text){if(field.text!=text)field=TextFieldValue(text,TextRange(text.length))}
    LaunchedEffect(key,q.panelOpen){if(q.panelOpen)focus.requestFocus()}
    Column(Modifier.fillMaxWidth().padding(12.dp).composerFileDrop(c,key){hover=it}.background(colors.surfaceVariant.copy(alpha=.45f),RoundedCornerShape(12.dp))
        .border(1.dp,if(hover)colors.primary else colors.outline,RoundedCornerShape(12.dp)).padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        (c.attachmentErrors[key]?:c.sendErrors[key]?:c.modelSwitchErrors[key])?.let {Text(it,fontSize=12.sp,lineHeight=18.sp,color=colors.error,maxLines=3)}
        if(hover)Caption("松开即可添加文件")
        if(files.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.heightIn(max=100.dp).verticalScroll(rememberScrollState())) {
            files.forEach {file->ComposerAttachment(c,key,file)}
        }
        BasicTextField(field,{field=it;c.setDraft(key,it.text)},Modifier.fillMaxWidth().heightIn(min=58.dp,max=120.dp).focusRequester(focus).onPreviewKeyEvent {e->when {
            isFilePaste(e)&&field.composition==null&&c.pasteFiles(key)->true
            shouldSendOnKey(c.sendOnEnter,e,field.composition!=null)->{q.send();true}
            shouldInsertNewlineOnKey(c.sendOnEnter,e,field.composition!=null)->{field=insertComposerNewline(field);c.setDraft(key,field.text);true}
            else->false
        }}.semantics {testTag="quick-input";contentDescription="向助理提问"},textStyle=MaterialTheme.typography.bodyMedium.copy(color=colors.onSurface,lineHeight=23.sp),cursorBrush=SolidColor(colors.primary),decorationBox={inner->Box {if(text.isBlank())Text("写下问题…",color=colors.onSurfaceVariant);inner()}})
        ComposerModelPicker(c,s,Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            Hint("添加文件"){DeskIconButton(onClick={c.chooseFiles(key)}){Glyph("attachment",Modifier.size(18.dp))}}
            Hint("连续语音对话"){DeskIconButton(onClick={q.open(CompanionAction.VOICE)}){Glyph("mic",Modifier.size(18.dp))}}
            Caption(if(preparing)"读取附件中…"else sendKeyLabel(c.sendOnEnter),Modifier.weight(1f))
            if(c.runs.containsKey(key))Hint("停止当前任务"){DeskIconButton(onClick={c.stop(s)}){Glyph("stop",Modifier.size(17.dp))}}
            SmallButton(if(c.runs.containsKey(key))"排队发送"else"发送",q::send,true,enabled=c.connected&&!preparing&&c.modelSwitching[key]!=true&&(text.isNotBlank()||files.isNotEmpty()))
        }
    }
}
