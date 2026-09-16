package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Text operations preserve the source. No HTML round-trip is involved. */
internal fun formatMarkdown(value:TextFieldValue,action:String):TextFieldValue {
    val start=value.selection.min;val end=value.selection.max
    val selected=value.text.substring(start,end)
    fun wrap(before:String,after:String,placeholder:String):TextFieldValue {
        if(start>=before.length&&end+after.length<=value.text.length&&value.text.substring(start-before.length,start)==before&&value.text.substring(end,end+after.length)==after)
            return TextFieldValue(value.text.removeRange(end,end+after.length).removeRange(start-before.length,start),TextRange(start-before.length,end-before.length))
        if(selected.startsWith(before)&&selected.endsWith(after)&&selected.length>=before.length+after.length) {
            val body=selected.substring(before.length,selected.length-after.length)
            return TextFieldValue(value.text.replaceRange(start,end,body),TextRange(start,start+body.length))
        }
        val body=selected.ifEmpty {placeholder}
        return TextFieldValue(value.text.replaceRange(start,end,before+body+after),TextRange(start+before.length,start+before.length+body.length))
    }
    fun prefix(marker:String):TextFieldValue {
        val first=value.text.lastIndexOf('\n',(start-1).coerceAtLeast(-1))+1
        val last=value.text.indexOf('\n',(end-1).coerceAtLeast(start)).let {if(it<0)value.text.length else it}
        val lines=value.text.substring(first,last).lines()
        val numbered=Regex("^\\d+\\. ")
        val remove=action!="indent"&&lines.all {if(action=="numbered")numbered.containsMatchIn(it)else it.startsWith(marker)}
        val replacement=lines.mapIndexed {i,line->if(remove){if(action=="numbered")line.replaceFirst(numbered,"")else line.removePrefix(marker)}else if(action=="numbered")"${i+1}. $line"else marker+line}.joinToString("\n")
        return TextFieldValue(value.text.replaceRange(first,last,replacement),TextRange(first,first+replacement.length))
    }
    return when(action) {
        "bold"->wrap("**","**","加粗文字")
        "italic"->wrap("*","*","强调文字")
        "strike"->wrap("~~","~~","删除文字")
        "inline-code"->wrap("`","`","代码")
        "code"->wrap("```\n","\n```","代码")
        "link"->wrap("[","](https://)","链接文字")
        "heading"->prefix("## ")
        "h1","h2","h3","h4","h5","h6"->{
            val first=value.text.lastIndexOf('\n',start-1)+1
            val last=value.text.indexOf('\n',start).let {if(it<0)value.text.length else it}
            val body=value.text.substring(first,last).replaceFirst(Regex("^#{1,6}\\s+"),"")
            val replacement="#".repeat(action.last().digitToInt())+" "+body
            TextFieldValue(value.text.replaceRange(first,last,replacement),TextRange(first+replacement.length))
        }
        "bullet"->prefix("- ")
        "numbered"->prefix("1. ")
        "quote"->prefix("> ")
        "task"->prefix("- [ ] ")
        "table"->{
            val insert="\n\n| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |\n"
            TextFieldValue(value.text.replaceRange(start,end,insert),TextRange(start+insert.length))
        }
        "indent"->prefix("  ")
        "outdent"->{
            val first=value.text.lastIndexOf('\n',(start-1).coerceAtLeast(-1))+1
            val last=value.text.indexOf('\n',(end-1).coerceAtLeast(start)).let {if(it<0)value.text.length else it}
            val replacement=value.text.substring(first,last).lines().joinToString("\n"){it.removePrefix("  ").removePrefix("\t")}
            TextFieldValue(value.text.replaceRange(first,last,replacement),TextRange(first,first+replacement.length))
        }
        else->value
    }
}

internal fun markdownMatches(text:String,query:String):List<IntRange> {
    if(query.isEmpty())return emptyList()
    val matches=mutableListOf<IntRange>();var from=0
    while(from<=text.length-query.length) {
        val index=text.indexOf(query,from,ignoreCase=true)
        if(index<0)break
        matches+=index until index+query.length;from=index+query.length
    }
    return matches
}

/** One history per open source editor; selection/composition updates do not create undo steps. */
internal class MarkdownUndoHistory {
    private val undo=ArrayDeque<TextFieldValue>();private val redo=ArrayDeque<TextFieldValue>()
    private var compositionStart:TextFieldValue?=null
    fun record(before:TextFieldValue,after:TextFieldValue) {
        if(after.composition!=null) {
            if(compositionStart==null)compositionStart=before.copy(composition=null)
            return
        }
        val baseline=compositionStart?:before
        compositionStart=null
        if(baseline.text==after.text)return
        undo.addLast(baseline.copy(composition=null))
        // Keep at least one whole-document undo, but bound history for long Markdown files.
        var size=undo.sumOf {it.text.length.toLong()}
        while(undo.size>1&&(undo.size>100||size>4_000_000)){size-=undo.removeFirst().text.length}
        redo.clear()
    }
    fun undo(current:TextFieldValue):TextFieldValue?=undo.removeLastOrNull()?.also {redo.addLast(current.copy(composition=null))}
    fun redo(current:TextFieldValue):TextFieldValue?=redo.removeLastOrNull()?.also {undo.addLast(current.copy(composition=null))}
}

/** Enter continues the current list/quote, leaves empty items, and respects fenced code and IME. */
internal fun continueMarkdownLine(value:TextFieldValue):TextFieldValue {
    if(value.composition!=null)return value
    val start=value.selection.min;val end=value.selection.max
    fun insert(text:String)=TextFieldValue(value.text.replaceRange(start,end,text),TextRange(start+text.length))
    if(start!=end)return insert("\n")
    val lineStart=value.text.lastIndexOf('\n',start-1)+1
    val before=value.text.substring(lineStart,start)
    val prefix=value.text.substring(0,lineStart)
    if(Regex("(?m)^\\s*(```|~~~)").findAll(prefix).count()%2!=0)return insert("\n"+before.takeWhile {it==' '||it=='\t'})
    val match=Regex("^(\\s*)([-*+] |\\d+[.)] |> )(?:\\[([ xX])] )?(.*)$").matchEntire(before)?:return insert("\n")
    val indent=match.groupValues[1];val marker=match.groupValues[2];val task=match.groups[3]!=null;val body=match.groupValues[4]
    if(body.isBlank())return TextFieldValue(value.text.removeRange(lineStart,start),TextRange(lineStart))
    val number=Regex("(\\d+)([.)]) ").matchEntire(marker)
    val next=if(number!=null)"${(number.groupValues[1].toIntOrNull()?:0)+1}${number.groupValues[2]} "else marker
    return insert("\n"+indent+next+if(task)"[ ] "else"")
}

internal fun replaceMarkdownMatch(value:TextFieldValue,query:String,replacement:String,all:Boolean=false):TextFieldValue {
    val matches=markdownMatches(value.text,query)
    if(matches.isEmpty())return value
    if(all) {
        val result=StringBuilder(value.text)
        matches.asReversed().forEach {result.replace(it.first,it.last+1,replacement)}
        return TextFieldValue(result.toString(),TextRange((matches.first().first+replacement.length).coerceAtMost(result.length)))
    }
    val match=matches.firstOrNull {it.first>=value.selection.min}?:matches.first()
    return TextFieldValue(value.text.replaceRange(match.first,match.last+1,replacement),TextRange(match.first,match.first+replacement.length))
}
