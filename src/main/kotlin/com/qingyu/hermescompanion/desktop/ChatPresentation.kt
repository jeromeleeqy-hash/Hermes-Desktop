package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.platform.DesktopHost

/** Presentation only: never rewrite stored messages or the model's conversation. */
internal data class MessageNotice(val label:String,val raw:String,val problem:Boolean=false)
internal data class PresentedText(val body:String,val notices:List<MessageNotice> = emptyList())
private val modelNotice = Regex("""^\[System: The active model for this chat has changed to ([^\r\n\]]+?) via provider ([^\r\n\]]+?)\. From this point forward, use this runtime metadata when answering questions about what model/provider is active\.\]""")
private val interruptedNotice = Regex("""^Operation interrupted: waiting for model response \(([0-9.]+)s elapsed\)\.$""")

internal fun presentUserText(raw:String,imageSources:List<String> = emptyList()):PresentedText {
    var body=raw
    val notices=mutableListOf<MessageNotice>()
    while(true) {
        val match=modelNotice.find(body)?:break
        notices+=MessageNotice("已切换模型 · ${match.groupValues[1]}",match.value)
        body=body.substring(match.range.last+1).trimStart('\r','\n',' ')
    }
    if(body.startsWith("[Hermes Desktop ·")&&body.contains("[原始问题]"))body=body.substringAfter("[原始问题]").trim()
    // Only remove transport prefixes whose image is already represented by a parsed attachment.
    // Quoted examples, unknown paths and the stored message remain untouched.
    while(true) {
        val source=imageSources.firstOrNull {path->
            val prefix="@image:"+path
            body.startsWith(prefix)&&(body.length==prefix.length||body[prefix.length].isWhitespace())
        }?:break
        body=body.removePrefix("@image:"+source).trimStart()
    }
    return PresentedText(body,notices)
}
internal fun presentAssistantText(raw:String):PresentedText {
    val notices=mutableListOf<MessageNotice>()
    var fence:Char?=null;var fenceLength=0
    val body=raw.lines().filter {line->
        val trimmed=line.trim()
        val marker=trimmed.takeWhile {it=='`'||it=='~'}
        if(marker.length>=3&&marker.all {it==marker.first()}) {
            if(fence==null){fence=marker.first();fenceLength=marker.length}
            else if(fence==marker.first()&&marker.length>=fenceLength&&trimmed.drop(marker.length).isBlank())fence=null
            true
        }else {
            val match=if(fence==null)interruptedNotice.matchEntire(trimmed)else null
            if(match!=null){notices+=MessageNotice("等待模型响应已中断 · ${match.groupValues[1]} 秒",line,true);false}else true
        }
    }.joinToString("\n").trim()
    return PresentedText(body,notices)
}

/** One rule for home and chat. Only the focused editor handles its send keys. */
internal fun shouldSendOnKey(sendOnEnter:Boolean,event:KeyEvent,composing:Boolean):Boolean {
    if(event.type!=KeyEventType.KeyDown||event.key!=Key.Enter||composing||event.isShiftPressed||event.isAltPressed)return false
    val primary=DesktopHost.primaryModifier(event.isMetaPressed,event.isCtrlPressed)
    return if(sendOnEnter)!event.isCtrlPressed&&!event.isMetaPressed else primary
}
internal fun shouldInsertNewlineOnKey(sendOnEnter:Boolean,event:KeyEvent,composing:Boolean)=
    event.type==KeyEventType.KeyDown&&event.key==Key.Enter&&!composing&&!event.isAltPressed&&!event.isCtrlPressed&&!event.isMetaPressed&&(event.isShiftPressed||!sendOnEnter)
internal fun insertComposerNewline(value:TextFieldValue):TextFieldValue {
    val start=value.selection.min;val end=value.selection.max
    return TextFieldValue(value.text.replaceRange(start,end,"\n"),TextRange(start+1))
}
internal fun sendKeyLabel(sendOnEnter:Boolean)=if(sendOnEnter)"Enter 发送 · Shift+Enter 换行"else"${shortcutKey()}+Enter 发送 · Enter 换行"
internal fun activityModeLabel(mode:String)=when(mode){"expanded"->"展开过程";"answer"->"仅看回答";else->"简洁记录"}
internal fun runningSendLabel(mode:String)=when(mode){"steer"->"补充指令";"interrupt"->"停止后发送";else->"排队发送"}
internal fun toolLabel(name:String)=mapOf("read_file" to "读取文件","write_file" to "写入文件","edit_file" to "修改文件","terminal" to "执行命令","web_search" to "搜索资料","web_extract" to "读取网页","browser" to "浏览网页","delegate_task" to "分配子任务","todo" to "更新任务清单")[name]?:name
data class ReadingPosition(val messageId:String,val index:Int,val offset:Int,val following:Boolean)
internal fun outlineText(message:ChatMessage)=visibleUserText(message.content).lineSequence().firstOrNull {it.isNotBlank()}.orEmpty().take(100)
internal fun sessionGroup(session:HermesSession):String {
    if(session.isPinned)return "置顶"
    val date=parseDesktopInstant(session.updatedAt)?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()?:return "更早"
    val today=java.time.LocalDate.now()
    return when {date==today->"今天";date==today.minusDays(1)->"昨天";date>=today.minusDays(7)->"最近 7 天";else->"更早"}
}
