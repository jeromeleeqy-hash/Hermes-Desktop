@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*

@Composable internal fun ChatComposer(c:DesktopController,s:HermesSession) {
    val colors=MaterialTheme.colorScheme;val design=LocalDesktopDesign.current;val shape=design.composerShape
    val key=s.scopedId;val draft=c.drafts[key].orEmpty();val attached=c.attachments[key].orEmpty()
    val run=c.runs[key];val preparing=(c.attachmentLoads[key]?:0)>0
    val switching=c.modelSwitching[key]==true;val steering=c.steering[key]==true
    var field by rememberComposerValue(key,draft)
    var focused by remember(key){mutableStateOf(false)}
    var fileHover by remember(key){mutableStateOf(false)}
    var more by remember {mutableStateOf(false)}
    val focus=remember(key){FocusRequester()}
    DisposableEffect(key){onDispose {c.composerHasComposition=false}}
    fun submit(){c.submitDraft()}
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        ComposerReasoningPicker(c,s)
        val failure=c.sendErrors[key]?:c.modelSwitchErrors[key]
        if(failure!=null)Row(Modifier.fillMaxWidth().background(colors.error.copy(alpha=.06f),RoundedCornerShape(10.dp)).padding(10.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Glyph("alert",Modifier.size(17.dp),colors.error)
            Column(Modifier.weight(1f)) {Text(tr(if(c.sendErrors[key]!=null)"这次发送未完成"else"模型切换未完成"),fontSize=13.sp,fontWeight=FontWeight.Medium);SubtleText("内容已保留，可以查看原因后重试。",maxLines=2)}
            DeskTextButton(onClick={c.showDetails("操作详情",failure)}){Text(tr("查看原因"))}
            DeskIconButton(onClick={c.sendErrors.remove(key);c.modelSwitchErrors.remove(key)}){Glyph("close",Modifier.size(15.dp))}
        }
        Column(Modifier.fillMaxWidth().then(if(design.glass)Modifier.shadow(10.dp,shape,clip=false,ambientColor=colors.primary.copy(alpha=.10f),spotColor=colors.primary.copy(alpha=.10f))else Modifier).background(colors.surface,shape)
            .composerFileDrop(c,key){fileHover=it}
            .border(if(focused||fileHover)1.5.dp else 1.dp,if(focused||fileHover)colors.primary.copy(alpha=.7f)else colors.outline,shape)
            .semantics {testTag="chat-composer"}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(fileHover)Row(Modifier.fillMaxWidth().background(colors.primary.copy(alpha=.07f),RoundedCornerShape(8.dp)).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Glyph("attachment",Modifier.size(18.dp),colors.primary);Text(tr("松开鼠标，添加到这段对话"),fontSize=13.sp,color=colors.primary)
            }
            c.attachmentErrors[key]?.let {message->Row(verticalAlignment=Alignment.CenterVertically) {
                Text(message,Modifier.weight(1f),color=colors.error,fontSize=12.sp,maxLines=4)
                DeskIconButton(onClick={c.attachmentErrors.remove(key)}){Glyph("close",Modifier.size(14.dp))}
            }}
            if(attached.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                attached.forEach {a->
                    ComposerAttachment(c,key,a)
                }
            }
            if(draft.startsWith("/")&&draft.length<30)c.commands.filter {it.command.contains(draft,true)}.take(4).forEach {command->
                DeskTextButton(onClick={c.setDraft(key,command.command+" ");focus.requestFocus()}){Text(command.command+"  "+command.description)}
            }
            ComposerImeSession {
            BasicTextField(field,{field=it;c.composerHasComposition=it.composition!=null;c.setDraft(key,it.text)},
                modifier=Modifier.fillMaxWidth().heightIn(min=56.dp,max=180.dp).focusRequester(focus).onFocusChanged {focused=it.isFocused}
                    .preserveMacImeComposition {field}.onPreviewKeyEvent {e->when {
                        isFilePaste(e)&&field.composition==null&&c.pasteFiles(key)->true
                        shouldSendOnKey(c.sendOnEnter,e,field.composition!=null)->{submit();true}
                        shouldInsertNewlineOnKey(c.sendOnEnter,e,field.composition!=null)->{field=insertComposerNewline(field);c.setDraft(key,field.text);true}
                        else->false
                    }}
                    .semantics {contentDescription=tr("消息输入框");testTag="chat-input"}.padding(6.dp),
                textStyle=MaterialTheme.typography.bodyMedium.copy(color=colors.onSurface,lineHeight=24.sp),cursorBrush=SolidColor(colors.primary),
                decorationBox={inner->Box {if(draft.isEmpty())Text(tr(if(run!=null)"任务进行中，可以继续补充…"else"写下想法，或添加资料…"),color=colors.onSurfaceVariant);inner()}})
        }
            BoxWithConstraints {
                val narrow=maxWidth<610.dp
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    if(narrow)ComposerModelPicker(c,s,Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        Hint("添加文件 · 也可拖入或粘贴"){DeskIconButton(onClick={c.chooseFiles(key)},modifier=Modifier.semantics {testTag="add-chat-file"}){Glyph("attachment",Modifier.size(19.dp))}}
                        Hint(if(c.recording)"完成录音"else"语音输入"){DeskIconButton(onClick={c.toggleRecording()},enabled=!c.voiceBusy){Glyph(if(c.recording)"stop"else"mic",Modifier.size(19.dp),if(c.recording)colors.error else colors.onSurfaceVariant)}}
                        Hint("插入提示词片段"){DeskIconButton(onClick={c.snippetsOpen=true}){Glyph("bookmark",Modifier.size(18.dp))}}
                        Box {
                            Hint("更多输入选项"){DeskIconButton(onClick={more=true}){Glyph("more",Modifier.size(19.dp))}}
                            DeskMenu(more,{more=false}) {
                                DeskMenuItem(text={Text(tr("快捷命令"))},leadingIcon={Glyph("command")},onClick={more=false;c.commandsOpen=true})
                                DeskMenuItem(text={Text(tr("语音对话"))},leadingIcon={Glyph("mic")},onClick={more=false;c.voice.open()})
                                DeskMenuItem(text={Text(tr("输入与阅读设置"))},leadingIcon={Glyph("settings")},onClick={more=false;c.settingsSection="对话与记忆";c.loadSettings()})
                            }
                        }
                        if(!narrow){Spacer(Modifier.width(4.dp));ComposerModelPicker(c,s,Modifier.width(240.dp))}
                        Spacer(Modifier.weight(1f))
                        if(run!=null) {
                            Hint("停止当前任务"){DeskIconButton(modifier=Modifier.semantics {testTag="stop-run"},onClick={c.stop(s)},enabled=!run.stopping){Glyph("stop",Modifier.size(19.dp),colors.error)}}
                            RunningModePicker(c)
                        }
                        val hasContent=draft.isNotBlank()||attached.isNotEmpty()
                        val canSteer=run==null||c.runningSendMode!="steer"||(run.controller.runtimeSessionId!=null&&attached.isEmpty())
                        val enabled=hasContent&&!preparing&&!switching&&!steering&&run?.stopping!=true&&canSteer
                        val label=when {preparing->"准备中";switching->"切换中";steering->"发送中";run?.stopping==true->"停止中";run!=null->runningSendLabel(c.runningSendMode);else->"发送"}
                        val hint=when {preparing->"附件准备完成后可发送";switching->"模型切换完成后可发送";!canSteer->"当前无法补充指令，请选择排队发送";!hasContent->"输入内容或添加附件";else->sendKeyLabel(c.sendOnEnter)}
                        Hint(hint) {
                            Surface(shape=RoundedCornerShape(8.dp),color=if(enabled)colors.primary else colors.onSurface.copy(alpha=.08f)) {
                                Row(Modifier.height(36.dp).desktopClick(enabled=enabled){submit()}.semantics {testTag="send-message";contentDescription=tr(label)}.padding(horizontal=12.dp),
                                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Text(tr(label),fontSize=13.sp,fontWeight=FontWeight.Medium,color=if(enabled)colors.onPrimary else colors.onSurfaceVariant)
                                    Glyph(if(run!=null&&c.runningSendMode=="queue")"queue"else"send",Modifier.size(17.dp),if(enabled)colors.onPrimary else colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            if(run==null)SubtleText(if(preparing)"正在读取文件…"else if(attached.isNotEmpty())"${attached.size} 个附件 · 发送时提交"else"草稿自动保留 · 支持拖入 / 粘贴文件",Modifier.weight(1f))else SubtleText(when(c.runningSendMode){"steer"->"向当前任务补充要求";"interrupt"->"确认停止当前任务后再发送";else->"当前任务完成后自动发送"},Modifier.weight(1f))
            ComposerSendHint(c)
        }
    }
}

@Composable private fun ComposerSendHint(c:DesktopController) {
    var open by remember {mutableStateOf(false)}
    Box {
        DeskTextButton(onClick={open=true},modifier=Modifier.semantics {testTag="send-mode"}) {
            Text(sendKeyLabel(c.sendOnEnter),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp));Glyph("chevron-down",Modifier.size(12.dp),MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DeskMenu(open,{open=false}) {listOf(true,false).forEach {enter->
            DeskMenuItem(text={Text(sendKeyLabel(enter))},leadingIcon={Glyph("keyboard")},trailingIcon={if(c.sendOnEnter==enter)Glyph("check",Modifier.size(16.dp))},onClick={c.setSendMode(enter);open=false})
        }}
    }
}

@Composable private fun RunningModePicker(c:DesktopController) {
    var open by remember {mutableStateOf(false)}
    Box {
        Hint("选择运行中的发送方式"){DeskIconButton(onClick={open=true}){Glyph("chevron-down",Modifier.size(17.dp))}}
        DeskMenu(open,{open=false}) {listOf("queue","steer","interrupt").forEach {mode->
            DeskMenuItem(text={Text(tr(runningSendLabel(mode)))},leadingIcon={Glyph("send")},trailingIcon={if(c.runningSendMode==mode)Glyph("check",Modifier.size(16.dp))},onClick={c.runningSendMode=mode;c.savePreference("runningSendMode",mode);open=false})
        }}
    }
}
