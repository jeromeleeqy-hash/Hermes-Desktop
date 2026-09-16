package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*

@Composable internal fun ComposerModelPicker(c:DesktopController,s:HermesSession,modifier:Modifier=Modifier) {
    var open by remember(s.scopedId){mutableStateOf(false)}
    val switching=c.modelSwitching[s.scopedId]==true
    val colors=MaterialTheme.colorScheme
    Box(modifier.widthIn(max=260.dp)) {
        Hint(if(c.runs.containsKey(s.scopedId))"当前任务完成后可切换模型"else"切换当前会话模型") {
            Row(Modifier.fillMaxWidth().height(32.dp).background(colors.surfaceVariant,RoundedCornerShape(8.dp))
                .desktopClick(enabled=!switching&&!c.runs.containsKey(s.scopedId)){open=true;c.loadModelCatalog()}
                .semantics {testTag="composer-model";contentDescription=tr("切换当前会话模型")}.padding(horizontal=9.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                if(switching)CircularProgressIndicator(Modifier.size(14.dp),strokeWidth=1.5.dp)else Glyph("model",Modifier.size(16.dp),colors.primary)
                Text(if(switching)tr("正在切换…")else s.model.ifBlank {c.modelCatalog.currentModel.ifBlank {tr("默认模型")}},Modifier.weight(1f),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                Glyph(if(c.modelCatalogError!=null)"alert"else"chevron-down",Modifier.size(12.dp),colors.onSurfaceVariant)
            }
        }
        DeskMenu(open,{open=false},modifier=Modifier.width(360.dp).heightIn(max=510.dp),shape=RoundedCornerShape(12.dp),containerColor=colors.surface,shadowElevation=8.dp) {
            ModelPickerContent(c,s){open=false}
        }
    }
}

@Composable internal fun ModelCatalogIssue(c:DesktopController,modifier:Modifier=Modifier) {
    val problem=c.modelCatalogError?:return
    val colors=MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(colors.surfaceVariant,RoundedCornerShape(10.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Glyph(if(problem.restartRequired)"refresh"else"alert",Modifier.size(18.dp),colors.primary)
            Text(tr(problem.title),fontSize=14.sp,fontWeight=FontWeight.SemiBold)
        }
        Text(tr(problem.guidance),fontSize=12.sp,lineHeight=20.sp,color=colors.onSurfaceVariant)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            SmallButton(if(c.modelCatalogLoading)"正在读取…"else"重新读取",{c.loadModelCatalog(force=true)},enabled=!c.modelCatalogLoading)
            DeskTextButton(onClick={c.showDetails("模型目录诊断",problem.guidance+"\n\n服务器原始返回：\n"+problem.raw)}) {Text(tr("查看详情"),fontSize=12.sp)}
        }
    }
}

@Composable internal fun ModelPickerContent(c:DesktopController,s:HermesSession,onDismiss:()->Unit={}) {
    var query by remember(s.scopedId){mutableStateOf("")}
    val colors=MaterialTheme.colorScheme
    Column(Modifier.width(360.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(tr("选择模型"),Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Hint("重新读取模型列表"){DeskIconButton(modifier=Modifier.semantics {testTag="model-catalog-retry"},onClick={c.loadModelCatalog(force=true)},enabled=!c.modelCatalogLoading){Glyph("refresh",Modifier.size(17.dp))}}
        }
        val current=s.model.ifBlank {c.modelCatalog.currentModel}
        if(current.isNotBlank())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(tr("当前"),fontSize=11.sp,color=colors.onSurfaceVariant)
            Text(current,Modifier.weight(1f),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.primary)
        }
        if(c.modelCatalogLoading)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        if(c.modelCatalogError!=null)ModelCatalogIssue(c)
        else {
            CompactInput(query,{query=it},"搜索模型或提供商")
            val q=query.trim()
            val providers=c.modelCatalog.providers.map {p->p.copy(models=p.models.filter {q.isBlank()||p.name.contains(q,true)||p.slug.contains(q,true)||it.contains(q,true)})}.filter {it.models.isNotEmpty()}
            if(providers.isNotEmpty()) {
                val state=rememberLazyListState()
                // DropdownMenu asks for intrinsic height. LazyColumn cannot supply it;
                // give the list a bounded, font-aware size before that measurement occurs.
                val listHeight=with(androidx.compose.ui.platform.LocalDensity.current) {
                    (providers.size*(18.sp.toDp()+14.dp)+providers.sumOf {it.models.size}*(20.sp.toDp()+20.dp)+((providers.size+providers.sumOf {it.models.size}-1)*3).dp+2.dp).coerceAtMost(260.dp)
                }
                Box(Modifier.height(listHeight)) {
                    LazyColumn(state=state,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        providers.forEach {p->
                            item(key="provider:"+p.slug){Text(p.name,Modifier.padding(horizontal=8.dp,vertical=7.dp),fontSize=11.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)}
                            items(p.models,key={p.slug+":"+it}) {model->
                                val selected=model==s.model&&p.slug==s.provider
                                Row(Modifier.fillMaxWidth().desktopClick(selected=selected){onDismiss();c.selectModel(s,p.slug,model)}
                                    .semantics {testTag="model-option:${p.slug}:$model";this.selected=selected}.padding(horizontal=10.dp,vertical=10.dp),
                                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(model,Modifier.weight(1f),fontSize=13.sp,lineHeight=20.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    if(selected)Glyph("check",Modifier.size(16.dp),colors.primary)
                                }
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(4.dp))
                }
            }else Text(tr(when {c.modelCatalogLoading->"正在读取可用模型…";q.isNotBlank()->"没有匹配的模型，试试其他关键词";else->"这个工作空间还没有可选模型，请先配置提供商。"}),Modifier.padding(10.dp),fontSize=13.sp,lineHeight=21.sp,color=colors.onSurfaceVariant)
        }
        HorizontalDivider(color=colors.outlineVariant)
        DeskTextButton(onClick={onDismiss();c.settingsSection="模型";c.loadSettings()}){Glyph("settings",Modifier.size(16.dp));Spacer(Modifier.width(8.dp));Text(tr("管理模型与提供商"),fontSize=12.sp)}
    }
}
