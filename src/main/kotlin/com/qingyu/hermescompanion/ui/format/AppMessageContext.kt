package com.qingyu.hermescompanion.ui.format

private val appContextHeader=Regex("(?:^|\\n\\n)<!-- hermes-mobile-context-v1:(\\d+) -->\\n")
private const val appContextFooter="\n<!-- /hermes-mobile-context-v1 -->"

/** Strip only complete, length-checked App envelopes appended outside a code fence.
 * Presentation only: the stored message and model context are never changed. */
fun visibleAppMessage(raw:String):String {
    var text=raw
    repeat(12) {
        val match=appContextHeader.findAll(text).lastOrNull()?:return text
        val length=match.groupValues[1].toIntOrNull()?:return text
        val start=match.range.last+1
        if(length !in 1..1_000_000||length>text.length-start)return text
        val end=start+length
        if(!text.startsWith(appContextFooter,end)||text.substring(end+appContextFooter.length).isNotBlank())return text
        var fence:Char?=null;var width=0
        text.substring(0,match.range.first).lineSequence().forEach {line->
            val value=line.trimStart()
            val marker=value.takeWhile {it=='`'||it=='~'}
            if(marker.length>=3&&marker.all {it==marker.first()}) {
                if(fence==null){fence=marker.first();width=marker.length}
                else if(fence==marker.first()&&marker.length>=width&&value.drop(marker.length).isBlank())fence=null
            }
        }
        if(fence!=null)return text
        text=text.substring(0,match.range.first)
    }
    return text
}
