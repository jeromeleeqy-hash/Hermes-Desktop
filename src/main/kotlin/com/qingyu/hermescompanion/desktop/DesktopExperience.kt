package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.data.ChatInsightParser
import com.qingyu.hermescompanion.ui.format.conversationPreview
import java.time.*
import java.time.format.DateTimeFormatter

/** A presentation turn retains every source id so search can still locate a folded event. */
data class DisplayTurn(val message:ChatMessage,val sourceIds:Set<String>)
fun displayTurns(messages:List<ChatMessage>):List<DisplayTurn> {
    val result=mutableListOf<DisplayTurn>()
    val pending=mutableListOf<ChatMessage>()
    fun flush() {
        if(pending.isEmpty())return
        val useful=pending.filter { it.content.isNotBlank()||it.reasoning.isNotBlank()||it.images.isNotEmpty()||it.isStreaming }
        if(useful.isNotEmpty()) {
            val final=useful.last()
            result+=DisplayTurn(final.copy(
                id=useful.first().id,
                content=useful.map {it.content.trim()}.filter(String::isNotBlank).distinct().joinToString("\n\n"),
                reasoning=useful.map {it.reasoning.trim()}.filter(String::isNotBlank).distinct().joinToString("\n\n"),
                images=useful.flatMap {it.images}.distinctBy {it.source},
                isStreaming=useful.any {it.isStreaming},
            ),pending.map {it.id}.toSet())
        }
        pending.clear()
    }
    messages.forEach { m->when(m.role) {
        MessageRole.USER->{flush();result+=DisplayTurn(m,setOf(m.id))}
        MessageRole.ASSISTANT->pending+=m
        MessageRole.TOOL->Unit
        MessageRole.SYSTEM->flush()
    } }
    flush();return result
}

/** Hide only standalone transport markers whose target is rendered as a file card. */
fun visibleAssistantText(text:String):String {
    var fenceChar:Char?=null;var fenceLength=0
    return text.lines().filter {line->
        val trimmed=line.trimStart()
        val markerFence=trimmed.takeWhile {it=='`'||it=='~'}
        if(markerFence.length>=3&&markerFence.all {it==markerFence.first()}) {
            if(fenceChar==null){fenceChar=markerFence.first();fenceLength=markerFence.length}
            else if(markerFence.first()==fenceChar&&markerFence.length>=fenceLength&&trimmed.drop(markerFence.length).isBlank())fenceChar=null
            true
        }
        else {
            val marker=line.trim()
            val path=if(marker.startsWith("MEDIA:",true))marker.substringAfter(':').trim().removeSurrounding("\"").removeSurrounding("'")else ""
            fenceChar!=null||path.isBlank()||ChatInsightParser.artifactsFromText(marker).none {it.path==path}
        }
    }.joinToString("\n").trim()
}

fun parseDesktopInstant(value:String):Instant? {
    if(value.isBlank())return null
    value.toDoubleOrNull()?.let {number->return runCatching {Instant.ofEpochMilli(if(number<100_000_000_000) (number*1000).toLong() else number.toLong())}.getOrNull()}
    return runCatching {Instant.parse(value)}.getOrNull()
        ?:runCatching {OffsetDateTime.parse(value.replace(' ','T')).toInstant()}.getOrNull()
}
fun friendlyTime(value:String,zone:ZoneId=ZoneId.systemDefault(),now:Instant=Instant.now()):String {
    val instant=parseDesktopInstant(value)?:return value.ifBlank {"—"}
    val date=instant.atZone(zone);val today=now.atZone(zone).toLocalDate()
    val day=when(date.toLocalDate()){today->"今天";today.minusDays(1)->"昨天";today.plusDays(1)->"明天";else->date.format(DateTimeFormatter.ofPattern(if(date.year==today.year)"M月d日"else"yyyy年M月d日"))}
    return "$day ${date.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}
fun sessionSummary(messages:List<ChatMessage>,fallback:String):String {
    val latest=messages.lastOrNull {it.role in setOf(MessageRole.ASSISTANT,MessageRole.USER)&&visibleAssistantText(it.content).isNotBlank()}
    return conversationPreview(visibleUserText(visibleAssistantText(latest?.content?:fallback))).replace(Regex("(?m)^#{1,6}\\s+"),"").take(180)
}
fun artifactMatchesProject(a:RecentArtifact,p:HermesProject?):Boolean = p==null||a.workspacePath==p.primaryPath||a.path.startsWith(p.primaryPath.trimEnd('/')+"/")
fun mergeRecentArtifacts(old:List<RecentArtifact>,incoming:List<RecentArtifact>):List<RecentArtifact> =
    (incoming+old).groupBy {"${it.profile}:${it.sessionId}:${it.path}"}.values.map {values->values.maxBy {it.seenAtMillis}}
        .sortedWith(compareByDescending<RecentArtifact> {it.seenAtMillis}.thenBy {it.path}).take(300)

data class ScheduleChoice(val mode:String="每天",val hour:Int=9,val minute:Int=0,val weekdays:Set<Int> = setOf(1)) {
    fun expression()="$minute $hour * * ${when(mode){"工作日"->"1-5";"每周"->weekdays.sorted().joinToString(",");else->"*"}}"
}
fun simpleSchedule(expression:String):ScheduleChoice? {
    val p=expression.trim().split(Regex("\\s+"));if(p.size!=5||p[2]!="*"||p[3]!="*")return null
    val minute=p[0].toIntOrNull()?.takeIf {it in 0..59}?:return null
    val hour=p[1].toIntOrNull()?.takeIf {it in 0..23}?:return null
    if(p[4]=="*")return ScheduleChoice("每天",hour,minute)
    if(p[4]=="1-5")return ScheduleChoice("工作日",hour,minute)
    val days=p[4].split(',').map {it.toIntOrNull()?:return null}.map {if(it==7)0 else it}.toSet()
    if(days.isEmpty()||days.any {it !in 0..6})return null
    return ScheduleChoice("每周",hour,minute,days)
}
val weekdayNames=mapOf(1 to "周一",2 to "周二",3 to "周三",4 to "周四",5 to "周五",6 to "周六",0 to "周日")
fun scheduleLabel(expression:String):String = simpleSchedule(expression)?.let {s->
    val day=if(s.mode=="每周")s.weekdays.sortedBy {if(it==0)7 else it}.joinToString("、") {weekdayNames[it].orEmpty()}else s.mode
    "$day %02d:%02d".format(s.hour,s.minute)
}?:expression
fun cronValidation(expression:String):String? {
    val fields=expression.trim().split(Regex("\\s+"));if(fields.size!=5)return "请填写 5 段 Cron：分、时、日、月、星期。"
    val ranges=listOf(0..59,0..23,1..31,1..12,0..7)
    fields.forEachIndexed {index,field->
        if(field.isBlank())return "时间表达式不能为空。"
        for(part in field.split(',')) {
            val stepped=part.split('/');if(stepped.size>2||stepped.size==2&&(stepped[1].toIntOrNull()?.let {it>0&&it<=ranges[index].last+1}!=true))return "时间步长无效，请检查第 ${index+1} 段。"
            val base=stepped[0];if(base=="*")continue
            val bounds=base.split('-').map {it.toIntOrNull()?:return "第 ${index+1} 段请使用数字、*、范围或步长。"}
            if(bounds.size !in 1..2||bounds.any {it !in ranges[index]}||bounds.size==2&&bounds[0]>bounds[1])return "第 ${index+1} 段超出有效范围 ${ranges[index].first}–${ranges[index].last}。"
        }
    };return null
}
/** Preview only schedules created by the basic editor; the server remains authoritative. */
fun nextScheduledTimes(choice:ScheduleChoice,zone:ZoneId,now:Instant=Instant.now(),count:Int=3):List<ZonedDateTime> {
    val start=now.atZone(zone).toLocalDate()
    return (0..35).asSequence().map {start.plusDays(it.toLong())}.filter {date->when(choice.mode){"工作日"->date.dayOfWeek.value<=5;"每周"->date.dayOfWeek.value%7 in choice.weekdays;else->true}}
        .map {it.atTime(choice.hour,choice.minute).atZone(zone)}.filter {it.toInstant()>now}.take(count).toList()
}
fun taskStatusLabel(raw:String)=when(raw.lowercase()) {
    "ok","success","completed","done"->"执行成功";"error","failed","failure"->"执行失败"
    "running","executing"->"正在执行";"paused","disabled"->"已暂停";"scheduled","pending","idle"->"等待执行";else->raw.ifBlank {"尚未执行"}
}
fun fileSizeLabel(bytes:Long?):String=when {
    bytes==null->"大小未知";bytes<1024->"$bytes B";bytes<1024*1024->"%.1f KB".format(bytes/1024.0);else->"%.1f MB".format(bytes/(1024.0*1024))
}
internal fun fileGlyph(name:String,directory:Boolean=false)=if(directory)"folder"else when(name.substringAfterLast('.',"").lowercase()) {
    "png","jpg","jpeg","webp","gif","svg","bmp"->"image"
    "csv","tsv","xlsx","xls"->"table"
    "kt","java","py","js","ts","tsx","jsx","json","yaml","yml","html","css","sh","ps1"->"code"
    "mp3","wav","ogg","m4a","flac"->"audio"
    else->"document"
}

data class DiffLine(val kind:Char,val text:String)
fun documentDiff(before:String,after:String):List<DiffLine> {
    if(before==after)return emptyList()
    val a=before.lines();val b=after.lines()
    if(a.size.toLong()*b.size>1_000_000)return a.map {DiffLine('-',it)}+b.map {DiffLine('+',it)}
    val dp=Array(a.size+1){IntArray(b.size+1)}
    for(i in a.lastIndex downTo 0)for(j in b.lastIndex downTo 0)dp[i][j]=if(a[i]==b[j])dp[i+1][j+1]+1 else maxOf(dp[i+1][j],dp[i][j+1])
    return buildList {var i=0;var j=0;while(i<a.size||j<b.size)when {
        i<a.size&&j<b.size&&a[i]==b[j]->{add(DiffLine(' ',a[i]));i++;j++}
        j<b.size&&(i==a.size||dp[i][j+1]>=dp[i+1][j])->{add(DiffLine('+',b[j]));j++}
        else->{add(DiffLine('-',a[i]));i++}
    }}
}
