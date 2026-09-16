@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.data.HermesApiClient
import com.qingyu.hermescompanion.model.ServerSettings

fun optionLabel(value:String)=mapOf(
    "automatic" to "自动选择","server" to "服务器","system" to "跟随系统","simplified" to "简体中文","traditional" to "繁体中文","original" to "保留原文",
    "quiet" to "安静环境","balanced" to "一般环境","noisy" to "嘈杂环境","zh-CN" to "普通话 · 简体中文","zh-TW" to "中文 · 繁体","en-US" to "英语","ja-JP" to "日语","ko-KR" to "韩语",
    "smart" to "智能审批","always" to "始终询问","never" to "无需询问","none" to "关闭","minimal" to "极低","low" to "低","medium" to "中","high" to "高","xhigh" to "很高","max" to "最高",
    "vision" to "图像理解","web_extract" to "网页提取","compression" to "上下文压缩","skills_hub" to "技能选择","approval" to "审批判断","mcp" to "外部工具","title_generation" to "会话标题","curator" to "内容整理",
)[value]?:value

@Composable fun StatusPill(text:String,error:Boolean=false,muted:Boolean=false) {
    val colors=MaterialTheme.colorScheme;val color=if(error)colors.error else if(muted)colors.onSurfaceVariant else colors.primary
    Text(tr(text),Modifier.background(color.copy(alpha=.08f),RoundedCornerShape(5.dp)).padding(horizontal=7.dp,vertical=3.dp),fontSize=11.sp,color=color,maxLines=1)
}
@Composable fun SettingCard(title:String,description:String="",trailing:(@Composable ()->Unit)?=null,content:@Composable ColumnScope.()->Unit) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if(LocalDesktopDesign.current.compact)16.dp else 22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Text(tr(title),Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.SemiBold);trailing?.invoke()}
            if(description.isNotBlank())SubtleText(description,maxLines=3)
            content()
        }
    }
}
@Composable fun Disclosure(title:String,detail:String="",initiallyOpen:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    var open by remember(title){mutableStateOf(initiallyOpen)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().desktopClick(fillHover=false){open=!open}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            val angle by animateFloatAsState(if(open)90f else 0f,tween(motionMillis(160)),label="disclosure-arrow")
            Glyph("chevron-right",Modifier.size(15.dp).graphicsLayer {rotationZ=angle},MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {Text(tr(title),fontWeight=FontWeight.Medium);if(detail.isNotBlank())SubtleText(detail,maxLines=2)}
        }
        AnimatedVisibility(open,enter=expandVertically(tween(motionMillis(180)))+fadeIn(tween(motionMillis(140))),exit=shrinkVertically(tween(motionMillis(140)))+fadeOut(tween(motionMillis(100)))) {Column(verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}
    }
}
@Composable fun DialogForm(content:@Composable ColumnScope.()->Unit) {
    val scroll=rememberScrollState()
    Box(Modifier.width(520.dp).heightIn(max=470.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(5.dp))
    }
}
@Composable fun FormInput(label:String,value:String,onChange:(String)->Unit,lines:Int=1,modifier:Modifier=Modifier,enabled:Boolean=true,error:String?=null) {
    val colors=MaterialTheme.colorScheme;val shape=LocalDesktopDesign.current.controlShape
    var focused by remember{mutableStateOf(false)}
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(label.isNotBlank())Text(tr(label),fontSize=12.sp,color=colors.onSurfaceVariant)
        BasicTextField(value,onChange,enabled=enabled,singleLine=lines==1,minLines=lines,maxLines=if(lines==1)1 else 12,
            textStyle=MaterialTheme.typography.bodyMedium.copy(color=colors.onSurface),cursorBrush=SolidColor(colors.primary),
            modifier=Modifier.fillMaxWidth().onFocusChanged {focused=it.isFocused}.background(colors.surface,shape).border(1.dp,if(error!=null)colors.error else if(focused)colors.primary else colors.outline,shape).padding(horizontal=10.dp,vertical=9.dp)
                .semantics {contentDescription=label})
        if(error!=null)Text(tr(error),color=colors.error,fontSize=12.sp)
    }
}
@Composable fun <T> SettingsSaveBar(c:DesktopController,draft:SettingDraft<T>,label:String="保存修改",validation:String?=null,
    save:(HermesApiClient,T)->ServerSettings,extract:(ServerSettings)->T) {
    if(draft.conflict) {
        Text(tr("服务器设置已更新。你的草稿已保留，请先确认采用哪份内容。"),fontSize=12.sp,color=MaterialTheme.colorScheme.error)
        Row {DeskTextButton(onClick={draft.reset()}){Text(tr("使用服务器设置"))};DeskTextButton(onClick={draft.useLatestBaseline()}){Text(tr("继续使用我的修改"))}}
    }
    draft.error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
    draft.fieldErrors.values.firstOrNull()?.let {Text(tr(it),color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
    if(validation!=null)Text(tr(validation),color=MaterialTheme.colorScheme.error,fontSize=12.sp)
    Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        SmallButton(if(draft.saving)"正在保存…"else label,{
            val submitted=draft.value;val submittedInputs=draft.rawInputs.toMap();val p=c.profile;draft.saving=true;draft.error=null
            c.request(p,{save(it,submitted)},finished={draft.saving=false},failed={draft.error=it}) {saved->
                draft.commit(submitted,extract(saved),submittedInputs);if(c.profile==p)c.settings=saved;c.notice="设置已保存"
            }
        },true,enabled=draft.dirty&&!draft.saving&&validation==null&&draft.fieldErrors.isEmpty()&&!draft.conflict)
        if(draft.dirty)DeskTextButton(onClick={draft.reset()},enabled=!draft.saving){Text(tr("撤销修改"))}
        Spacer(Modifier.weight(1f))
        SubtleText(if(draft.saving)"正在应用到服务器"else if(draft.dirty)"未保存 · 草稿已保留"else"与服务器一致")
    }
}

@Composable fun NoticeOverlay(c:DesktopController,modifier:Modifier=Modifier) {
    val notice=c.notice?:return
    LaunchedEffect(notice){kotlinx.coroutines.delay(4500);if(c.notice==notice)c.notice=null}
    Surface(modifier.widthIn(max=540.dp).arrive(notice),shape=LocalDesktopDesign.current.shape,color=MaterialTheme.colorScheme.inverseSurface,shadowElevation=6.dp) {
        Row(Modifier.padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Glyph("check",Modifier.size(17.dp),MaterialTheme.colorScheme.inverseOnSurface)
            Text(tr(notice),Modifier.weight(1f,false),fontSize=13.sp,color=MaterialTheme.colorScheme.inverseOnSurface,maxLines=3,overflow=TextOverflow.Ellipsis)
            DeskIconButton(onClick={c.notice=null}){Glyph("close",Modifier.size(18.dp),MaterialTheme.colorScheme.inverseOnSurface)}
        }
    }
}
