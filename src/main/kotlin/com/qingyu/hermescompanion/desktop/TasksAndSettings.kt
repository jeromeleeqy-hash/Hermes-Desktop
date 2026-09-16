package com.qingyu.hermescompanion.desktop

import kotlinx.coroutines.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.BuildConfig
import com.qingyu.hermescompanion.model.*

@Composable fun TasksView(c:DesktopController) {
    var selected by remember {mutableStateOf<String?>(null)};var editCron by remember {mutableStateOf<CronJob?>(null)}
    var details by remember {mutableStateOf<CronJob?>(null)};var creating by remember {mutableStateOf(false)};var deleteCron by remember {mutableStateOf<CronJob?>(null)}
    var query by remember {mutableStateOf("")};val pending=remember(c.profile){mutableStateMapOf<String,Boolean>()};val errors=remember(c.profile){mutableStateMapOf<String,String>()}
    LaunchedEffect(c.profile) {val p=c.profile;if(!c.demo&&c.settings==null)c.request(p,{it.serverSettings()}){if(c.profile==p)c.settings=it}}
    LaunchedEffect(c.profile,c.taskTab) {val p=c.profile;while(isActive&&!c.demo&&c.taskTab=="定时任务") {delay(15_000);c.request(p,{it.listCronJobs(p)}){if(c.profile==p)c.cronJobs=it}}}
    val zone=c.settings?.conversation?.timezone?.takeIf {it.isNotBlank()}?.let {runCatching {ZoneId.of(it)}.getOrNull()}
    DesktopPage(scrollable=false,tag="tasks-content") {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SegmentTabs(listOf("执行中心","定时任务","执行记录"),c.taskTab){c.taskTab=it}
            Spacer(Modifier.weight(1f))
            if(c.taskTab=="定时任务")SmallButton("新建定时任务",{creating=true},true)
        }
        when(c.taskTab) {
            "执行中心"->TaskBoard(c,{selected=it},Modifier.weight(1f))
            "执行记录"->{
                SubtleText("服务器返回的定时任务会话 · 打开可查看实际回复与生成文件",maxLines=2)
                if(c.cronSessions.isEmpty())PanelEmpty("clock","暂无执行记录","任务执行后，服务器提供的记录会同步到这里。")
                else LazyColumn(Modifier.weight(1f)) {items(c.cronSessions,key={it.scopedId}) {s->SessionRow(s,{c.openSession(s)}){SubtleText(friendlyTime(s.updatedAt))}}}
            }
            else->{
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    CompactInput(query,{query=it},"搜索定时任务",Modifier.weight(1f))
                    SubtleText("${c.cronJobs.count {it.enabled}} / ${c.cronJobs.size} 已启用")
                }
                val jobs=c.cronJobs.filter {it.name.contains(query,true)||it.prompt.contains(query,true)}
                if(jobs.isEmpty())PanelEmpty("clock",if(query.isBlank())"还没有定时任务"else"没有匹配的任务","创建后，Hermes 会按你设定的时间执行。")
                else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {items(jobs,key={it.id}) {job->
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=.06f),LocalDesktopDesign.current.controlShape),contentAlignment=Alignment.Center){Glyph("clock",Modifier.size(19.dp),MaterialTheme.colorScheme.primary)}
                                Column(Modifier.weight(1f)) {Text(job.name,fontWeight=FontWeight.SemiBold,fontSize=15.sp);SubtleText(scheduleLabel(job.schedule.expression))}
                                StatusPill(if(!job.enabled)"已暂停"else taskStatusLabel(job.lastStatus.ifBlank {job.state}),error=job.lastStatus in setOf("error","failed"),muted=!job.enabled)
                                Switch(job.enabled,{v->val p=c.profile;pending[job.id]=true;c.request(p,{if(v)it.resumeCronJob(job.id)else it.pauseCronJob(job.id)},finished={pending.remove(job.id)},failed={errors[job.id]=it}){if(c.profile==p)c.cronJobs=c.cronJobs.map {if(it.id==job.id)it.copy(enabled=v)else it};errors.remove(job.id)}},enabled=pending[job.id]!=true)
                            }
                            if(job.prompt.isNotBlank())SubtleText(job.prompt.replace(Regex("\\s+")," "),maxLines=2)
                            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                                SubtleText("下次：${if(!job.enabled)"已暂停"else if(job.nextRunAt.isBlank())"等待服务器安排"else friendlyTime(job.nextRunAt,zone?:ZoneId.systemDefault())}")
                                if(job.lastRunAt.isNotBlank())SubtleText("上次：${friendlyTime(job.lastRunAt,zone?:ZoneId.systemDefault())}")
                            }
                            errors[job.id]?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                SmallButton("查看详情",{details=job;val p=c.profile;c.request(p,{it.cronJob(job.id)}){if(c.profile==p&&details?.id==job.id)details=it}})
                                SmallButton("编辑",{editCron=job},enabled=pending[job.id]!=true)
                                SmallButton(if(pending[job.id]==true)"正在提交…"else"立即运行",{val p=c.profile;pending[job.id]=true;errors.remove(job.id)
                                    c.request(p,{it.triggerCronJob(job.id)},finished={pending.remove(job.id)},failed={errors[job.id]=it}){
                                        if(c.profile==p){c.cronJobs=c.cronJobs.map {if(it.id==job.id)it.copy(state="running",lastStatus="running")else it};c.notice="运行请求已提交，可在执行记录中查看结果"}
                                    }
                                },enabled=pending[job.id]!=true&&job.lastStatus!="running")
                                Spacer(Modifier.weight(1f))
                                var more by remember {mutableStateOf(false)}
                                Box {Hint("定时任务操作"){DeskIconButton(onClick={more=true}){Glyph("more",Modifier.size(18.dp))}};DeskMenu(more,{more=false}){DeskMenuItem(text={Text(tr("删除任务"),color=MaterialTheme.colorScheme.error)},onClick={more=false;deleteCron=job})}}
                            }
                        }
                    }
                }}
            }
        }
    }
    selected?.let {id->c.decisions[id]?.let {decision->
        HermesDialog(onDismissRequest={selected=null},title={Text(tr(when(decision.request.type){AgentRequestType.APPROVAL->"审批详情";AgentRequestType.CLARIFICATION->"澄清详情";else->"处理详情"}))},text={DecisionPanel(c,decision,Modifier.width(552.dp).height(if(decision.request.type==AgentRequestType.APPROVAL)280.dp else 340.dp))},confirmButton={DeskTextButton(onClick={selected=null}){Text(tr("返回任务"))}})
    }}
    details?.let {job->HermesDialog(onDismissRequest={details=null},title={Text(job.name)},text={Column(Modifier.width(520.dp).heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        StatusPill(taskStatusLabel(job.lastStatus.ifBlank {job.state}));Text(scheduleLabel(job.schedule.expression),fontWeight=FontWeight.SemiBold)
        SubtleText("执行时区：${zone?.id?:"服务器时区（尚未读取）"}")
        Text(job.prompt);HorizontalDivider();SubtleText("上次：${friendlyTime(job.lastRunAt,zone?:ZoneId.systemDefault())}");SubtleText("下次：${friendlyTime(job.nextRunAt,zone?:ZoneId.systemDefault())}")
        SubtleText("投递方式：${job.deliver}");if(job.model.isNotBlank())SubtleText("模型：${job.provider} / ${job.model}")
    }},confirmButton={SmallButton("查看执行记录",{details=null;c.taskTab="执行记录"})},dismissButton={DeskTextButton(onClick={details=null}){Text(tr("关闭"))}})}
    if(creating||editCron!=null)CronEditor(c,editCron){creating=false;editCron=null}
    deleteCron?.let {job->ConfirmDialog("删除定时任务？",job.name,{deleteCron=null}){val p=c.profile;c.request(p,{it.deleteCronJob(job.id)}){if(c.profile==p)c.cronJobs=c.cronJobs.filterNot {it.id==job.id};c.notice="定时任务已删除"};deleteCron=null}}
}

@Composable internal fun DecisionPanel(c:DesktopController,value:PendingDecision,modifier:Modifier) {
    val live=c.decisions.values.firstOrNull {it.session.scopedId==value.session.scopedId&&it.request.requestId==value.request.requestId}
    if(live==null){Text("此请求已处理或已过期。");return}
    val r=live.request;var dismiss by remember {mutableStateOf(false)}
    var answer by remember(r.requestId){mutableStateOf("")}
    val answers=remember(r.requestId){mutableStateMapOf<String,String>().apply {r.questions.forEach {q->q.lockedAnswer?.let {put(q.id,it)}}}}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(r.title,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(r.detail.isNotBlank())Text(r.detail)
            Caption("来自：${value.session.title}")
            if(r.type==AgentRequestType.CLARIFICATION) {
                if(r.questions.isEmpty())ClarificationAnswer(r.requestId,r.choices,r.allowMultiple,!r.isResponding){answer=it}
                else r.questions.forEachIndexed {index,q->
                    Text("${index+1}. ${q.title}",fontWeight=FontWeight.Medium)
                    if(q.lockedAnswer!=null)Text("已确认：${q.lockedAnswer}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    else ClarificationAnswer("${r.requestId}:${q.id}",q.choices,q.allowMultiple,!r.isResponding){answers[q.id]=it}
                }
            }
            DeskTextButton(onClick={c.openSession(value.session)}){Text(tr("查看来源对话 →"))}
            DeskTextButton(onClick={dismiss=true},enabled=!r.isResponding){Text(tr("移出待处理列表"))}
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outline)
        if(r.type==AgentRequestType.ACTION_REQUIRED) {
            Caption("服务器需要处理：${r.method}。当前终端不能直接填写此类请求；不会自动批准或发送凭据。")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                r.actionUrl?.let {url->SmallButton("打开授权页面",{runCatching {DesktopFiles.openLink(url)}.onFailure {c.error=it.message}})}
                SmallButton("检查处理结果",{c.refreshDecision(value)})
                if(r.serverRpcId!=null)SmallButton("拒绝此请求",{c.respond(live,"unsupported")},enabled=!r.isResponding)
            }
        }else if(r.type==AgentRequestType.APPROVAL) {
            val choices=r.choices.ifEmpty { listOf(AgentRequestChoice("拒绝","deny"),AgentRequestChoice("允许一次","once")) }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){choices.forEach {choice->
                SmallButton(when(choice.label){"once"->"允许一次";"session"->"本会话允许";"always"->"始终允许";"deny"->"拒绝";else->choice.label},
                    {c.respond(live,choice.value)},primary=choice.value in listOf("once","allow_once","allow"),enabled=!r.isResponding)
            }}
        }else {
            val allAnswered=if(r.questions.isEmpty())answer.isNotBlank() else r.questions.all {q->q.lockedAnswer!=null||!answers[q.id].isNullOrBlank()}
            SmallButton(if(r.isResponding)"正在提交…"else"提交回答",{c.respond(live,answer,answers.toMap()+r.questions.mapNotNull {q->q.lockedAnswer?.let {q.id to it}})},true,!r.isResponding&&allAnswered)
        }
    }
    if(dismiss)ConfirmDialog("移出待处理列表？","此操作只移除本机记录，不会替你回答或停止服务器任务。",{dismiss=false}){c.decisions.entries.firstOrNull {it.value==value}?.let {c.dismissDecision(it.key)};dismiss=false}
}

@Composable private fun ClarificationAnswer(id:String,choices:List<AgentRequestChoice>,multiple:Boolean,enabled:Boolean,onChange:(String)->Unit) {
    var text by remember(id){mutableStateOf("")}
    var single by remember(id){mutableStateOf<String?>(null)}
    val selected=remember(id){mutableStateListOf<String>()}
    fun publish() {onChange(if(multiple)(selected.toList()+listOfNotNull(text.takeIf {it.isNotBlank()})).joinToString("\n")else text.ifBlank {single.orEmpty()})}
    choices.forEach {item->
        val choose={
            if(multiple){if(item.value in selected)selected.remove(item.value)else selected.add(item.value)}
            else {single=item.value;text=""}
            publish()
        }
        Row(Modifier.fillMaxWidth().desktopClick(enabled=enabled,onClick=choose),verticalAlignment=Alignment.CenterVertically) {
            if(multiple)Checkbox(item.value in selected,{choose()},enabled=enabled)
            else RadioButton(single==item.value&&text.isBlank(),{choose()},enabled=enabled)
            Column(Modifier.weight(1f)){Text(item.label);if(item.description.isNotBlank())Caption(item.description)}
        }
    }
    OutlinedTextField(text,{text=it;publish()},label={Text(tr(if(multiple)"补充说明"else"或直接输入回答"))},modifier=Modifier.fillMaxWidth(),minLines=2,enabled=enabled)
}

@Composable internal fun CronEditor(c:DesktopController,job:CronJob?,inlinePreview:Boolean=false,onDismiss:()->Unit) {
    val initial=mapOf("name" to job?.name.orEmpty(),"prompt" to job?.prompt.orEmpty(),"schedule" to (job?.schedule?.expression?:"0 9 * * *"))
    val draft=settingDraft(c,"cron:${job?.id?:"new"}",initial,SettingCodec::text,SettingCodec::text)
    var value by draft
    val schedule=value["schedule"].orEmpty()
    var advanced by remember(job?.id){mutableStateOf(simpleSchedule(schedule)==null)}
    val choice=simpleSchedule(schedule)?:ScheduleChoice()
    val zone=c.settings?.conversation?.timezone?.takeIf {it.isNotBlank()}?.let {runCatching {ZoneId.of(it)}.getOrNull()}
    val validation=if(job!=null&&schedule==job.schedule.expression)null else cronValidation(schedule)
    fun changeSchedule(next:ScheduleChoice){if(!draft.saving)value=value+("schedule" to next.expression())}
    HermesDialog(inlinePreview=inlinePreview,onDismissRequest={if(!draft.saving)onDismiss()},title={Text(tr(if(job==null)"新建定时任务"else"编辑定时任务"))},text={DialogForm {
        FormInput("任务名称",value["name"].orEmpty(),{value=value+("name" to it)},enabled=!draft.saving)
        FormInput("让 Hermes 做什么",value["prompt"].orEmpty(),{value=value+("prompt" to it)},4,enabled=!draft.saving)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(tr("执行时间"),Modifier.weight(1f),fontWeight=FontWeight.Medium);DeskTextButton(onClick={if(advanced&&simpleSchedule(schedule)==null)changeSchedule(ScheduleChoice());advanced=!advanced},enabled=!draft.saving){Text(tr(if(advanced)"使用时间选择器"else"高级 Cron"))}}
        if(advanced)FormInput("Cron 表达式",schedule,{value=value+("schedule" to it)},enabled=!draft.saving,error=validation)
        else {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Picker(choice.mode,listOf("每天","工作日","每周"),{it}){changeSchedule(choice.copy(mode=it))}
                Picker("%02d 时".format(choice.hour),(0..23).toList(),{"%02d".format(it)}){changeSchedule(choice.copy(hour=it))}
                Picker("%02d 分".format(choice.minute),(0..59).toList(),{"%02d".format(it)}){changeSchedule(choice.copy(minute=it))}
            }
            if(choice.mode=="每周")FlowRow(horizontalArrangement=Arrangement.spacedBy(3.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {weekdayNames.forEach {(day,label)->DeskChip(day in choice.weekdays,{val days=if(day in choice.weekdays)choice.weekdays-day else choice.weekdays+day;if(days.isNotEmpty())changeSchedule(choice.copy(weekdays=days))},label={Text(label,fontSize=12.sp)},enabled=!draft.saving)}}
        }
        SubtleText("执行时区：${zone?.id?:"尚未读取，请先读取服务器设置"}",maxLines=2)
        if(zone==null)SmallButton("读取时区",{val p=c.profile;c.request(p,{it.serverSettings()}){if(c.profile==p)c.settings=it}})
        if(zone!=null&&simpleSchedule(schedule)!=null) {
            Text(tr("接下来三次执行"),fontSize=12.sp,fontWeight=FontWeight.Medium)
            nextScheduledTimes(choice,zone).forEach {SubtleText(it.format(DateTimeFormatter.ofPattern("M月d日 EEEE HH:mm",desktopLocale())))}
            SubtleText("时间预览供核对；最终安排以服务器返回为准。",maxLines=2)
        }
        draft.error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
        if(draft.dirty)SubtleText("关闭后会保留本机草稿。")
    }},confirmButton={SmallButton(if(draft.saving)"正在保存…"else"保存任务",{
        val p=c.profile;val submitted=value;draft.saving=true;draft.error=null
        c.request(p,{api->if(job==null)api.createCronJob(submitted.getValue("name"),submitted.getValue("prompt"),submitted.getValue("schedule"),p)else api.updateCronJob(job.id,submitted.getValue("name"),submitted.getValue("prompt"),submitted.getValue("schedule"))},finished={draft.saving=false},failed={draft.error=it}) {saved->
            if(c.profile==p)c.cronJobs=(listOf(saved)+c.cronJobs).distinctBy {it.id}
            draft.reset();c.settingsDrafts.remove(c.storageKey("settingsDraft:$p:cron:${job?.id?:"new"}"));c.notice="定时任务已保存";onDismiss()
        }
    },true,enabled=!draft.saving&&value["name"].orEmpty().isNotBlank()&&value["prompt"].orEmpty().isNotBlank()&&validation==null)},dismissButton={DeskTextButton(onClick=onDismiss,enabled=!draft.saving){Text(tr("稍后再写"))}})
}
