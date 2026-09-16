package com.qingyu.hermescompanion.desktop

import org.commonmark.node.Node

internal data class MarkdownBlockAnchor(val index:Int,val top:Float,val bottom:Float)
internal data class MarkdownBlockPosition(val index:Int,val fraction:Float)

/** Block source spans keep long headings, tables, fenced code and images aligned. */
internal fun markdownBlockAnchors(nodes:List<Node>,lineTops:List<Float>):List<MarkdownBlockAnchor> {
    if(lineTops.size<2)return emptyList()
    val starts=nodes.map {node->node.sourceSpans.minOfOrNull {it.lineIndex}?:0}
    return nodes.mapIndexed {index,node->
        val first=starts[index].coerceIn(0,lineTops.lastIndex)
        val end=(starts.getOrNull(index+1)?:((node.sourceSpans.maxOfOrNull {it.lineIndex}?:first)+1)).coerceIn(first,lineTops.lastIndex)
        MarkdownBlockAnchor(index,lineTops[first],lineTops[end].coerceAtLeast(lineTops[first]+1))
    }
}
internal fun sourceToMarkdownBlock(y:Float,anchors:List<MarkdownBlockAnchor>):MarkdownBlockPosition? {
    if(anchors.isEmpty())return null
    val anchor=anchors.lastOrNull {it.top<=y}?:anchors.first()
    return MarkdownBlockPosition(anchor.index,((y-anchor.top)/(anchor.bottom-anchor.top)).coerceIn(0f,1f))
}
internal fun markdownBlockToSource(position:MarkdownBlockPosition,anchors:List<MarkdownBlockAnchor>):Float {
    val anchor=anchors.getOrNull(position.index)?:return anchors.lastOrNull()?.bottom?:0f
    return anchor.top+(anchor.bottom-anchor.top)*position.fraction.coerceIn(0f,1f)
}
