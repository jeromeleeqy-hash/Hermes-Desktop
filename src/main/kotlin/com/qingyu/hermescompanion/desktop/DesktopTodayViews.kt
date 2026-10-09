package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.today.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun HomeView(c:DesktopController){
    val t=c.today
    LaunchedEffect(c.profile,c.connected){if(c.connected){if(t.state.profile!=c.profile || !t.state.loaded)t.reset() else t.sync(false)}}
    val savedPages=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    savedPages.SaveableStateProvider("${c.profile}:${t.attentionOpen}") {
        if(t.attentionOpen)AttentionView(c) else SimpleHomeView(c)
    }
    t.selectedId?.let {id->t.state.board?.cards?.firstOrNull {it.id==id}?.let {card->TodayDetail(c,card){t.selectedId=null}}}
    if(t.settingsOpen)TodaySettings(c){t.settingsOpen=false}
}

@Composable internal fun TodayToolbar(c:DesktopController){
    val t=c.today
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Glyph("inbox",Modifier.size(17.dp),colors.primary)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text("为你留意 · ${t.state.board?.cards.orEmpty().count {!it.isClosed}} 件事",fontSize=13.sp,fontWeight=FontWeight.Medium)
            val updated=t.state.board?.generatedAt?.atZoneSameInstant(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
            SubtleText(if(t.state.loading)"正在同步…"else updated?.let {"内容更新于 $it"}?:"与你的手机共享同一份进展",maxLines=1)
        }
        Hint("同步首页"){DeskIconButton(onClick={t.sync()},enabled=!t.state.loading){Glyph("refresh",Modifier.size(17.dp),colors.onSurfaceVariant)}}
        DeskTextButton(onClick={t.refreshOverview()},enabled=!t.preparing&&!t.refreshState.busy&&t.state.rootVerified){
            Text(if(t.refreshState.busy)"正在整理…"else"更新进展",fontSize=12.sp)
        }
        Hint("事项整理设置"){DeskIconButton(onClick={t.settingsOpen=true}){Glyph("settings",Modifier.size(18.dp),colors.onSurfaceVariant)}}
    }
    if(t.changes.unread.isNotEmpty())DeskTextButton(onClick={t.reveal();t.acknowledge()}){Text("${t.changes.unread.size} 项新进展 · 点击查看",fontSize=12.sp)}
    t.state.error?.let {SyncProblem(c,"首页暂未同步",it,t.state.loading){t.sync()}}
    if(t.refreshState.message.isNotBlank())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        SubtleText(t.refreshState.message,maxLines=2)
        if(t.refreshState.sessionId.isNotBlank())DeskTextButton(onClick={t.openProcessing()}){Text("处理详情")}
    }
    if(t.state.fromCache)SubtleText("正在显示本机保存的概览，连接后会核对最新内容",maxLines=2)
}

@Composable internal fun TodayPreview(c:DesktopController){
    val t=c.today;val cards=focusCards(t.state.board?.cards.orEmpty()).take(3)
    SectionCard(Modifier.fillMaxWidth().semantics {testTag="attention-module"}){
        SectionTitle("值得留意的事",t.state.board?.headline?.ifBlank {"关注事项、进展与提醒"}?:"关注事项、进展与提醒",
            trailing={DeskTextButton(onClick={t.openAttention()},modifier=Modifier.semantics {testTag="open-attention"}){Text("查看全部 →")}})
        Column(Modifier.padding(horizontal=18.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            cards.forEach {card->Row(Modifier.fillMaxWidth().desktopClick {t.selectedId=card.id}.padding(vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                StatusPill(card.attentionGroup.label)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(card.title,fontSize=14.sp,fontWeight=FontWeight.Medium);SubtleText(card.readableContext,maxLines=2)}
                if(card.id in t.changes.newCardIds)StatusPill("新增")
            }}
            if(cards.isEmpty())Text(when {t.state.loading->"正在同步关注事项…";t.state.error!=null->"暂时未能同步，打开查看详情或重试。";else->"暂时没有待关注的事项，可以打开查看或整理进展。"},
                modifier=Modifier.padding(vertical=12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun AttentionView(c:DesktopController){
    val t=c.today;var group by remember {mutableStateOf<TodayAttentionGroup?>(null)}
    var domain by remember {mutableStateOf<TodayDomain?>(null)};var closed by remember {mutableStateOf(false)}
    val board=t.state.board
    DesktopPage(scrollable=false,maxContentWidth=1440.dp,tag="attention-content"){
        DeskTextButton(onClick={t.closeAttention()},modifier=Modifier.semantics {testTag="back-to-workbench"}){Glyph("chevron-left",Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("返回工作台")}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("值得留意的事",fontSize=25.sp,fontWeight=FontWeight.SemiBold)
                if(!board?.headline.isNullOrBlank())Text(board!!.headline,fontSize=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(!board?.summary.isNullOrBlank())Text(board!!.summary,fontSize=14.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SmallButton("开始对话",{c.newSession()},true)
        }
        TodayToolbar(c)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            DeskChip(group==null,{group=null},label={Text("全部事项")})
            TodayAttentionGroup.entries.forEach {g->DeskChip(group==g,{group=g},label={Text("${g.label} ${board?.cards.orEmpty().count {!it.isClosed&&it.attentionGroup==g}}")})}
            DeskChip(closed,{closed=!closed},label={Text("含已结束")})
        }
        val domains=board?.cards.orEmpty().map {it.domain}.distinct()
        if(domains.size>1)FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            DeskTextButton(onClick={domain=null}){Text(if(domain==null)"✓ 全部领域"else"全部领域")}
            domains.forEach {d->DeskTextButton(onClick={domain=if(domain==d)null else d}){Text((if(domain==d)"✓ "else"")+d.label)}}
        }
        val available=focusCards(board?.cards.orEmpty(),closed,domain).filter {group==null || it.attentionGroup==group}
        val cards=if(closed)available else t.order.cards(available)
        val hidden=available.count {a->cards.none {it.id==a.id}}
        if(hidden>0)SmallButton("显示 $hidden 项新事项",{t.reveal()})
        if(board==null){
            if(t.state.loading)LoadingRows() else Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("连接已有记录，整理关注事项和提醒。",color=MaterialTheme.colorScheme.onSurfaceVariant)
                SmallButton("开始整理",{t.refreshOverview()},true,enabled=!t.preparing&&t.state.rootVerified)
            }
        }else LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)){
            items(cards,key={it.id}){card->TodayCardTile(c,card)}
            if(t.changes.recentCompleted.isNotEmpty())item("recent-completed"){
                SectionCard(Modifier.fillMaxWidth()){
                    SectionTitle("最近完成","依据服务器记录更新")
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){t.changes.recentCompleted.forEach {e->
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){StatusPill("已完成");Column{Text(e.title,fontSize=14.sp);SubtleText(e.summary,maxLines=2)}}
                    }}
                }
            }
            item("sync-times"){
                fun time(value:Instant?)=value?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))?:"—"
                SubtleText("内容更新 ${time(board.generatedAt.toInstant())}  ·  内容核对 ${time(board.currentContentCheckedAt?.toInstant())}  ·  本机同步 ${time(t.state.syncedAt?.let(Instant::ofEpochMilli))}",maxLines=3)
                if(board.coverageNote.isNotBlank())SubtleText(board.coverageNote,maxLines=4)
            }
        }
    }
}

@Composable private fun TodayCardTile(c:DesktopController,card:TodayCard){
    val t=c.today;val p=card.presentation;val colors=MaterialTheme.colorScheme
    SectionCard(Modifier.fillMaxWidth()){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                StatusPill(if(card.isClosed)"已结束"else card.attentionGroup.label,muted=card.isClosed)
                SubtleText(card.domain.label)
                if(card.whenLabel.isNotBlank())SubtleText(card.whenLabel)
                if(card.id in t.changes.newCardIds)StatusPill("新增")
            }
            Text(card.title,fontSize=19.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold)
            if(card.readableContext.isNotBlank())Text(card.readableContext,fontSize=14.sp,lineHeight=24.sp,color=colors.onSurfaceVariant)
            when(p.layout){
                "progress"->{
                    val total=p.steps.size;val done=p.steps.count {it.done}
                    if(total>0){LinearProgressIndicator(progress={done.toFloat()/total},modifier=Modifier.fillMaxWidth().height(5.dp));SubtleText("$done / $total 已完成")}
                    p.steps.take(6).forEach {SubtleText((if(it.done)"✓ "else"○ ")+it.title,maxLines=3)}
                }
                "metrics"->TodayMetrics(p.metrics)
                "schedule"->if(card.whenLabel.isNotBlank())Text(card.whenLabel,fontSize=21.sp,color=colors.primary)
                "receipt"->StatusPill(if(card.isClosed)"记录已完成"else"进展已记录")
            }
            if(p.layout!="metrics" && p.metrics.isNotEmpty())TodayMetrics(p.metrics)
            if(p.dataNote.isNotBlank())SubtleText(p.dataNote,maxLines=3)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SmallButton("聊聊这件事",{t.discuss(card)},true,enabled=t.state.rootVerified&&!t.preparing)
                SmallButton(if(p.interaction!=null)"查看并处理"else"查看详情",{t.selectedId=card.id})
            }
        }
    }
}
@Composable private fun TodayMetrics(metrics:List<TodayMetric>){
    FlowRow(horizontalArrangement=Arrangement.spacedBy(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){metrics.forEach {m->
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)){SubtleText(m.label);Text(m.value+(if(m.unit.isNotBlank())" ${m.unit}"else""),fontSize=23.sp,fontWeight=FontWeight.Medium)}
    }}
}

@Composable internal fun TodayDetail(c:DesktopController,card:TodayCard,inlinePreview:Boolean=false,dismiss:()->Unit){
    val t=c.today;val a=t.actions["${t.state.profile}\n${t.state.root}\n${card.id}"]
    var raw by remember(card.id,card.interactionFingerprint){mutableStateOf(if(card.presentation.interaction!=null)initialTodayInput(card)else"")}
    var validation by remember {mutableStateOf<String?>(null)}
    HermesDialog(onDismissRequest=dismiss,inlinePreview=inlinePreview,maxWidth=800.dp,title={Text("事项详情")},text={
        val scroll=rememberScrollState()
        Box(Modifier.fillMaxWidth().heightIn(max=520.dp)){
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end=14.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                StatusPill(card.domain.label,muted=true);StatusPill(if(card.isClosed)"已结束"else card.attentionGroup.label)
                if(card.whenLabel.isNotBlank())SubtleText(card.whenLabel,maxLines=2)
            }
            SelectionContainer {Text(card.title,fontSize=22.sp,lineHeight=31.sp,fontWeight=FontWeight.SemiBold)}
            val summary=card.readableContext.ifBlank {card.summary}
            val background=listOf(card.presentation.background,card.summary).filter {it.isNotBlank()&&it!=summary}.distinct().joinToString("\n\n")
            if(summary.isNotBlank())Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha=.055f),RoundedCornerShape(10.dp)).padding(16.dp)){
                TodayReading(c,summary)
            }
            if(card.presentation.metrics.isNotEmpty())TodayMetrics(card.presentation.metrics)
            if(background.isNotBlank()){
                Text("背景与进展",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TodayReading(c,background)
            }
            if(card.presentation.facts.isNotEmpty())Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("关键事实",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                card.presentation.facts.forEach {TodayReading(c,"- $it")}
            }
            if(card.presentation.steps.isNotEmpty())Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("进度",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                card.presentation.steps.forEach {step->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Glyph(if(step.done)"check-square"else"checkbox",Modifier.size(18.dp));Text(step.title,fontSize=14.sp,lineHeight=22.sp)}}
            }
            if(card.presentation.dataNote.isNotBlank())TodayReading(c,card.presentation.dataNote)
            card.presentation.issue?.let {Text("这张卡的交互格式需要修复，仍可通过聊天继续。",color=MaterialTheme.colorScheme.error)}
            if(card.sources.isNotEmpty()){
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
                Text("来源资料",fontSize=13.sp,fontWeight=FontWeight.Medium)
                card.sources.forEach {path->DeskTextButton(onClick={c.openDocument(path,p=t.state.profile);dismiss()}){Glyph("document",Modifier.size(16.dp));Spacer(Modifier.width(7.dp));Text(path.substringAfterLast('/'),maxLines=2,fontSize=13.sp)}}
            }
            card.unavailableSources.forEach {SubtleText("当前工作区不可访问：$it",maxLines=2)}
            a?.let {Text(it.message, color=if(it.status=="failed")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)}
            if(a?.pending==true)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SmallButton("同步核对",{t.sync()});SmallButton("处理详情",{t.openProcessing(a.sessionId,a.profile)})
                if(a.status=="uncertain")SmallButton("恢复原操作",{t.recover(card.id)},enabled=!t.preparing)
            }
            if(card.presentation.interaction!=null&&!card.isClosed){
                HorizontalDivider();Text(card.presentation.interaction.type.label,fontWeight=FontWeight.Medium)
                TodayInteractionEditor(card,raw,{raw=it;validation=null},a?.pending!=true)
                validation?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                SmallButton(card.presentation.interaction.type.actionLabel,{
                    runCatching {normalizeTodayInput(card,raw)}.onSuccess {t.submit(card,raw)}.onFailure {validation=it.message}
                },true,enabled=a?.pending!=true&&!t.preparing&&t.state.rootVerified&&t.state.error==null)
            }
        }
        if(scroll.maxValue>0)VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp))
        }
    },confirmButton={SmallButton("聊聊这件事",{t.discuss(card);dismiss()},true,enabled=!t.preparing&&t.state.rootVerified)},dismissButton={DeskTextButton(onClick=dismiss){Text("关闭")}})
}

@Composable private fun TodayReading(c:DesktopController,text:String){
    val formatted=remember(text){todayReadingText(text)}
    SelectionContainer {Markdown(formatted){url->runCatching {DesktopFiles.openLink(url)}.onFailure {c.error=it.message}}}
}

/** All twenty schema types share validated primitives; action semantics remain in the shared protocol. */
@Composable private fun TodayInteractionEditor(card:TodayCard,raw:String,update:(String)->Unit,enabled:Boolean){
    val i=requireNotNull(card.presentation.interaction);val input=JSONObject(raw)
    fun edit(block:(JSONObject)->Unit){val next=JSONObject(raw);block(next);update(next.toString())}
    val selected=input.getJSONArray("selected_ids").let {a->(0 until a.length()).map {a.getString(it)}}
    val order=input.getJSONArray("order").let {a->(0 until a.length()).map {a.getString(it)}}
    if(i.text.isNotBlank())Text(i.text,fontSize=13.sp,lineHeight=22.sp)
    if(i.before.isNotBlank()){SubtleText("当前记录");Text(i.before);SubtleText("拟更新为");Text(i.after)}
    if(i.note.isNotBlank())SubtleText(i.note,maxLines=5)
    if(i.timezone.isNotBlank())SubtleText("时区：${i.timezone}")
    val single=i.type in setOf(TodayLayout.CLARIFICATION,TodayLayout.COMPARISON,TodayLayout.TIME_PICKER)
    val multi=i.type in setOf(TodayLayout.CHECKLIST,TodayLayout.MEETING_ACTIONS,TodayLayout.REVISION,TodayLayout.EVIDENCE)
    order.mapNotNull {id->i.items.firstOrNull {it.id==id}}.forEachIndexed {index,item->
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(single)RadioButton(input.optString("selection")==item.id,{edit {it.put("selection",item.id)}},enabled=enabled)
                if(multi)Checkbox(item.id in selected,{value->edit {it.put("selected_ids",JSONArray(if(value)selected+item.id else selected-item.id))}},enabled=enabled)
                Column(Modifier.weight(1f)){Text(item.title,fontSize=14.sp,fontWeight=FontWeight.Medium);if(item.detail.isNotBlank())SubtleText(item.detail,maxLines=5);if(item.value.isNotBlank())SubtleText(item.value,maxLines=3)}
                if(i.type==TodayLayout.PRIORITIES){
                    SmallButton("↑",{edit {val ids=order.toMutableList();java.util.Collections.swap(ids,index,index-1);it.put("order",JSONArray(ids))}},enabled=enabled&&index>0)
                    SmallButton("↓",{edit {val ids=order.toMutableList();java.util.Collections.swap(ids,index,index+1);it.put("order",JSONArray(ids))}},enabled=enabled&&index<order.lastIndex)
                }
            }
            if(i.type==TodayLayout.MEETING_ACTIONS){
                val row=input.getJSONObject("rows").getJSONObject(item.id)
                FormInput("负责人",row.optString("owner"),{v->edit {it.getJSONObject("rows").getJSONObject(item.id).put("owner",v)}},enabled=enabled)
                FormInput("日期 YYYY-MM-DD",row.optString("due"),{v->edit {it.getJSONObject("rows").getJSONObject(item.id).put("due",v)}},enabled=enabled)
            }
            if(i.type==TodayLayout.TRIAGE)FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("archive" to "归档","lead" to "线索","later" to "稍后").forEach {(v,label)->DeskChip(input.getJSONObject("rows").getJSONObject(item.id).getString("classification")==v,{if(enabled)edit {it.getJSONObject("rows").getJSONObject(item.id).put("classification",v)}},label={Text(label)})}
            }
        }
    }
    if(i.type==TodayLayout.MILESTONES)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(input.getBoolean("reviewed"),{v->edit {it.put("reviewed",v)}},enabled=enabled);Text("已核对验收条件")}
    val operations=when(i.type){TodayLayout.DELIVERABLE->listOf("adopt" to "采用","revise" to "需要修改");TodayLayout.CHANGE->listOf("accept" to "接受变化","keep" to "保留原状");TodayLayout.MEMORY_CHANGE->listOf("correct" to "校正","undo" to "撤销");else->emptyList()}
    if(operations.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){operations.forEach {(v,label)->DeskChip(input.optString("operation")==v,{if(enabled)edit {it.put("operation",v)}},label={Text(label)})}}
    i.fields.forEach {f->
        val value=input.getJSONObject("fields").optString(f.id)
        if(f.kind=="select"){
            Text(f.label+(if(f.required)" *"else""));FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){f.choices.forEach {v->DeskChip(value==v,{if(enabled)edit {it.getJSONObject("fields").put(f.id,v)}},label={Text(v)})}}
        }else FormInput(f.label+(if(f.required)" *"else"")+when(f.kind){"date"->" · YYYY-MM-DD";"time"->" · HH:mm";"number"->" · ${f.min}–${f.max}";else->""},value,{v->edit {it.getJSONObject("fields").put(f.id,v)}},enabled=enabled)
    }
    if(i.series.isNotEmpty()){
        val max=i.series.maxOf {kotlin.math.abs(it.value)}.coerceAtLeast(1.0)
        i.series.forEach {point->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(point.label,Modifier.width(90.dp),fontSize=12.sp);Box(Modifier.weight(1f).height(12.dp)){Box(Modifier.fillMaxWidth((kotlin.math.abs(point.value)/max).toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary,RoundedCornerShape(4.dp)))};Text(point.value.toString(),fontSize=12.sp)}}
    }
    FormInput(when(i.type){TodayLayout.BLOCKER->"补充缺少的信息";TodayLayout.REFLECTION->"今天的复盘";TodayLayout.TIMELINE->"记录具体进展";TodayLayout.PERSON_FOLLOWUP->"跟进要点（生成草稿）";else->"补充说明"},input.optString("note"),{v->edit {it.put("note",v)}},enabled=enabled)
}

@Composable internal fun TodaySettings(c:DesktopController,inlinePreview:Boolean=false,dismiss:()->Unit){
    val t=c.today
    var morning by remember {mutableStateOf(t.schedule.morning.ifBlank {"09:00"})};var evening by remember {mutableStateOf(t.schedule.evening.ifBlank {"21:00"})};var zone by remember {mutableStateOf(t.schedule.timezone.ifBlank {"Asia/Shanghai"})}
    var migration by remember {mutableStateOf(false)}
    LaunchedEffect(Unit){if(t.state.rootVerified)t.checkSchedule()}
    LaunchedEffect(t.schedule.verified){if(t.schedule.verified){morning=t.schedule.morning;evening=t.schedule.evening;zone=t.schedule.timezone}}
    HermesDialog(onDismissRequest=dismiss,inlinePreview=inlinePreview,title={Text("事项整理设置")},text={Column(Modifier.widthIn(max=620.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("早晚整理",fontWeight=FontWeight.Medium)
        SubtleText("沿用同一服务器工作区的已有任务。调整后核对真实运行时间，手机和电脑共享整理结果。",maxLines=3)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Column(Modifier.weight(1f)){FormInput("早间 HH:mm",morning,{morning=it})};Column(Modifier.weight(1f)){FormInput("晚间 HH:mm",evening,{evening=it})}}
        FormInput("地区时区",zone,{zone=it})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SmallButton("保存早晚安排",{t.configure(morning,evening,zone)},true,enabled=!t.preparing&&t.state.rootVerified);SmallButton(if(t.schedule.loading)"核对中…"else"核对是否生效",{t.checkSchedule()},enabled=!t.schedule.loading&&!t.preparing&&t.state.rootVerified)}
        if(t.schedule.message.isNotBlank())Text(t.schedule.message,fontSize=13.sp,color=if(t.schedule.verified)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        t.schedule.jobs.forEach {SubtleText(it,maxLines=3)}
        HorizontalDivider();Text("内容与存储",fontWeight=FontWeight.Medium)
        SmallButton("优化已有首页和整理规则",{t.prepare("improve_overview_and_schedule")},enabled=!t.preparing&&t.state.rootVerified)
        SubtleText("保留事项、来源和已有任务的时间安排。",maxLines=2)
        if(t.state.fileExists && !t.state.filePath.contains("/.hermes-app/today/")){
            SmallButton("收纳旧版首页文件…",{migration=true},enabled=!t.preparing&&t.state.rootVerified)
            SubtleText("将旧版概览和快照移入专用目录，保留业务文档。",maxLines=2)
        }
    }},confirmButton={SmallButton("完成",dismiss,true)})
    if(migration)HermesDialog(onDismissRequest={migration=false},title={Text("收纳旧版首页文件？")},text={Text("Hermes 会先核对并暂停匹配的首页整理任务，校验新旧文件后再迁移，随后恢复原安排。遇到冲突会保留两份文件供你核对。")},confirmButton={SmallButton("开始收纳",{migration=false;t.prepare("migrate_today_storage")},true)},dismissButton={DeskTextButton(onClick={migration=false}){Text("取消")}})
}
