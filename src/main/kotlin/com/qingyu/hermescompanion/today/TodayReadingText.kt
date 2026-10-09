package com.qingyu.hermescompanion.today

/** Presentation only: never alter the stored card, action payload or fingerprint. */
fun todayReadingText(value:String):String {
    val code=Regex("""(?s)(`{3}.*?(?:`{3}|$)|~{3}.*?(?:~{3}|$)|`[^`\n]*`)""")
    val breaks=Regex("""(?<!\\)\\(?:r\\n|n)""")
    fun prose(text:String)=breaks.replace(text) { match->
        // Windows paths and URLs can legitimately contain \n as ordinary text.
        val token=text.substring(0,match.range.first).takeLastWhile {!it.isWhitespace()}
        if(Regex("""^(?:[A-Za-z]:|[A-Za-z][A-Za-z0-9+.-]*://|/)""").containsMatchIn(token))match.value else "\n"
    }
    val decoded=buildString {
        var start=0
        code.findAll(value).forEach {match->append(prose(value.substring(start,match.range.first)));append(match.value);start=match.range.last+1}
        append(prose(value.substring(start)))
    }
    var fence:String?=null
    return decoded.lines().joinToString("\n") {line->
        val trimmed=line.trimStart()
        if(trimmed.startsWith("```")||trimmed.startsWith("~~~")){
            val marker=trimmed.take(3);if(fence==null)fence=marker else if(fence==marker)fence=null
            line
        }else if(fence!=null)line
        else {
            val heading=Regex("""^【([^】]{1,64})】[ \t]*(.*)$""").matchEntire(line)
            val item=Regex("""^([①②③④⑤⑥⑦⑧⑨⑩])\s*(.*)$""").matchEntire(line)
            when {
                heading!=null->"### ${heading.groupValues[1]}\n\n${heading.groupValues[2]}"
                item!=null->"${"①②③④⑤⑥⑦⑧⑨⑩".indexOf(item.groupValues[1])+1}. ${item.groupValues[2]}"
                else->line
            }
        }
    }.trim()
}
