package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.awt.Color
import java.awt.Font
import java.awt.Image
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.net.URL
import java.util.Base64
import java.util.Hashtable
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

/** Offline, JVM-only document renderer. Used for Office and as a WebKit fallback. */
internal data class CompatiblePage(val kit:HTMLEditorKit,val document:HTMLDocument)

internal fun compatiblePage(source:String,zoom:Double):CompatiblePage {
    require(source.length<=24*1024*1024){"预览内容过大，请下载后查看。"}
    val html=Jsoup.parse(source)
    html.select("article").forEach {it.tagName("div").addClass("hermes-document")}
    html.select("script,iframe,frame,object,embed,link,base,meta,form,video,audio").remove()
    val images=Hashtable<URL,Image>()
    var imageBudget=24_000_000L
    html.select("img").forEachIndexed {index,img->
        val src=img.attr("src")
        val decoded=if(src.startsWith("data:image/")&&src.contains(";base64,")&&src.length<=12*1024*1024)runCatching {
            val bytes=Base64.getDecoder().decode(src.substringAfter(','))
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use {input->
                val reader=ImageIO.getImageReaders(input).asSequence().firstOrNull()?:return@runCatching null
                try {reader.input=input;val pixels=reader.getWidth(0).toLong()*reader.getHeight(0)
                    if(pixels>imageBudget||pixels<=0)null else {imageBudget-=pixels;reader.read(0)}
                }finally {reader.dispose()}
            }
        }.getOrNull()else null
        if(decoded==null)img.replaceWith(org.jsoup.nodes.TextNode(img.attr("alt").ifBlank {"[图片]"}))
        else {
            // ImageView reads from HTMLDocument's cache. It never opens a network connection.
            val url=URL("https://hermes-preview.invalid/image/$index")
            images[url]=decoded;img.attr("src",url.toExternalForm())
            val width=(img.attr("width").toIntOrNull()?:decoded.width).coerceAtMost(760)
            img.attr("width",(width*zoom).toInt().coerceAtLeast(1).toString())
            img.attr("height",(width.toDouble()/decoded.width*decoded.height*zoom).toInt().coerceAtLeast(1).toString())
        }
    }
    html.allElements.forEach {node->
        node.attributes().asList().filter {it.key.startsWith("on",true)||it.key in setOf("background","srcset","action","formaction")}.forEach {node.removeAttr(it.key)}
        if(node.tagName()!="img")node.removeAttr("src")
        if(node.tagName()!="a")node.removeAttr("href")
        else if(!node.attr("href").startsWith("https://")&&!node.attr("href").startsWith("http://")&&!node.attr("href").startsWith('#'))node.removeAttr("href")
    }
    // Swing supports CSS 2. Keep useful typography/table styles, remove resource-fetching CSS.
    fun css(value:String)=value.replace(Regex("(?is)@import[^;]*;|url\\([^)]*\\)"),"")
        .replace("article","div.hermes-document").replace("'Segoe UI','Microsoft YaHei',sans-serif","SansSerif").replace("'Segoe UI',sans-serif","SansSerif")
        .replace(Regex("([0-9]+(?:\\.[0-9]+)?)(px|pt)")){m->"${m.groupValues[1].toDouble()*zoom}${m.groupValues[2]}"}
    html.select("style").forEach {it.text(css(it.data()))}
    html.select("[style]").forEach {it.attr("style",css(it.attr("style")))}
    val kit=HTMLEditorKit()
    kit.styleSheet.addRule("body { font-family: SansSerif; font-size: ${(15*zoom).toInt()}pt; color: #242b36; background: #ffffff; margin: 22px; }")
    kit.styleSheet.addRule("p { margin-top: 8px; margin-bottom: 14px; } h1 {font-size: ${(26*zoom).toInt()}pt;} h2 {font-size: ${(22*zoom).toInt()}pt;} h3 {font-size: ${(18*zoom).toInt()}pt;}")
    kit.styleSheet.addRule("td, th { border: 1px solid #dfe4ec; padding: 8px; } th {background: #eef2f8;} a {color: #2e65f5;} pre {font-family: Monospaced;}")
    val document=kit.createDefaultDocument() as HTMLDocument
    document.putProperty("imageCache",images)
    kit.read(StringReader(html.outerHtml()),document,0)
    return CompatiblePage(kit,document)
}

@Composable internal fun CompatibleDocument(html:String,modifier:Modifier=Modifier,zoom:Double=1.0) {
    val result by produceState<Result<CompatiblePage>?>(null,html,zoom) {
        value=null;value=withContext(Dispatchers.IO){runCatching {compatiblePage(html,zoom)}}
    }
    when(val page=result?.getOrNull()) {
        null->Box(modifier.fillMaxSize()) {
            if(result==null)LinearProgressIndicator(Modifier.fillMaxWidth())
            else Column(Modifier.padding(24.dp)){Heading("文档暂时无法排版");Caption(result?.exceptionOrNull()?.message?:"请下载后查看。")}
        }
        else->key(page){SwingPanel(modifier=modifier.fillMaxSize(),factory={
            val editor=object:JEditorPane() {
                override fun paintComponent(graphics:java.awt.Graphics) {
                    val g=graphics.create() as java.awt.Graphics2D
                    try {g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);super.paintComponent(g)}finally {g.dispose()}
                }
            }.apply {
                isEditable=false;putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES,true)
                font=Font(Font.SANS_SERIF,Font.PLAIN,(16*zoom).toInt());background=Color.WHITE
                editorKit=page.kit;document=page.document;caretPosition=0
                addHyperlinkListener {e->if(e.eventType==HyperlinkEvent.EventType.ACTIVATED) {
                    if(e.description.startsWith('#'))scrollToReference(e.description.substring(1))
                    else runCatching {DesktopFiles.openLink(e.description)}
                }}
            }
            nativeDocumentContainer(JScrollPane(editor).apply {border=BorderFactory.createEmptyBorder();verticalScrollBar.unitIncrement=24;horizontalScrollBar.unitIncrement=24})
        })}
    }
}
