package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.WorkspaceDocument
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.sl.usermodel.SlideShowFactory
import org.apache.poi.sl.draw.DrawFontManagerDefault
import org.apache.poi.sl.draw.Drawable
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xwpf.usermodel.*
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.converter.WordToHtmlConverter
import org.jsoup.Jsoup
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal fun htmlEscape(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
internal fun imageData(bytes:ByteArray,mime:String)="data:$mime;base64,"+Base64.getEncoder().encodeToString(bytes)
internal fun BufferedImage.pngBytes()=ByteArrayOutputStream().use {ImageIO.write(this,"png",it);it.toByteArray()}
internal fun documentHtml(body:String,extraStyle:String="")="""<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"><style>
body{margin:0;padding:40px;background:#f3f5f8;color:#242b36;font:16px/1.8 'Segoe UI','Microsoft YaHei',sans-serif}article{max-width:880px;box-sizing:border-box;margin:auto;background:white;padding:44px 54px;box-shadow:0 2px 12px #1b2a4210;border:1px solid #e7ebf0;border-radius:8px;min-height:80vh}h1,h2,h3{line-height:1.35;margin:1.4em 0 .65em}h1{font-size:30px}p{margin:.6em 0 1em}table{border-collapse:collapse;width:100%;margin:20px 0}td,th{border:1px solid #dfe4ec;padding:9px 12px;vertical-align:top}th{background:#f1f4fa}img{max-width:100%;height:auto}a{color:#2864ec}blockquote{border-left:3px solid #8da8e8;padding-left:18px;color:#626b7b}pre{white-space:pre-wrap} $extraStyle
@media(max-width:600px){body{padding:16px}article{padding:24px}}
</style></head><body><article>$body</article></body></html>"""

/** Read-only Office conversion; no macros, formula evaluation or external relationships are executed. */
internal fun wordPreview(bytes:ByteArray,legacy:Boolean):String {
    require(bytes.size<=64*1024*1024){"文档较大，请下载后打开。"}
    if(legacy)HWPFDocument(bytes.inputStream()).use {doc->
        val converter=WordToHtmlConverter(DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument())
        converter.setPicturesManager {data,type,_,_,_->imageData(data,type.mime)}
        converter.processDocument(doc)
        val out=java.io.StringWriter();TransformerFactory.newInstance().newTransformer().transform(DOMSource(converter.document),StreamResult(out))
        val html=Jsoup.parse(out.toString())
        return documentHtml(html.body().html(),html.select("style").joinToString("\n"){it.data()})
    }
    XWPFDocument(bytes.inputStream()).use {doc->
        val listCounters=mutableMapOf<Pair<java.math.BigInteger,java.math.BigInteger>,Int>()
        fun paragraph(p:XWPFParagraph):String {
            val heading=Regex("(?i)(heading|标题)\\s*([1-6])").find(p.style.orEmpty())?.groupValues?.get(2)
            val tag=heading?.let {"h$it"}?:"p"
            val align=when(p.alignment){ParagraphAlignment.CENTER->"center";ParagraphAlignment.RIGHT->"right";ParagraphAlignment.BOTH->"justify";else->"left"}
            val runs=p.runs.joinToString(""){r->
                val css=buildString {
                    if(r.isBold)append("font-weight:600;");if(r.isItalic)append("font-style:italic;")
                    if(r.isStrikeThrough)append("text-decoration:line-through;")else if(r.underline!=UnderlinePatterns.NONE)append("text-decoration:underline;")
                    r.color?.takeIf {it.matches(Regex("[0-9a-fA-F]{6}"))}?.let {append("color:#$it;")}
                    r.fontSizeAsDouble?.takeIf {it in 6.0..72.0}?.let {append("font-size:${it}pt;")}
                }
                val content=htmlEscape(r.text()).replace("\n","<br>").replace("\t","&emsp;")+r.embeddedPictures.joinToString(""){picture->
                    picture.pictureData?.let {"<img alt=\"文档图片\" src=\"${imageData(it.data,it.packagePart.contentType)}\">"}.orEmpty()
                }
                val link=(r as? XWPFHyperlinkRun)?.getHyperlink(doc)?.url
                val text="<span style=\"$css\">$content</span>"
                if(link!=null&&link.startsWith("https://",true))"<a href=\"${htmlEscape(link)}\">$text</a>"else text
            }
            val prefix=if(p.numID!=null) {
                if(p.numFmt=="bullet")"• &nbsp;"else {
                    val key=p.numID to (p.numIlvl?:java.math.BigInteger.ZERO)
                    val n=listCounters[key]?:p.numStartOverride?.toInt()?:1
                    listCounters[key]=n+1;"$n. &nbsp;"
                }
            }else ""
            return "<$tag style=\"text-align:$align\">$prefix${runs.ifBlank {"&nbsp;"}}</$tag>"
        }
        fun body(elements:List<IBodyElement>,depth:Int=0):String {
            if(depth>8)return ""
            return elements.joinToString(""){e->when(e){
                is XWPFParagraph->paragraph(e)
                is XWPFTable->"<table>"+e.rows.joinToString(""){row->"<tr>"+row.tableCells.joinToString(""){cell->"<td>"+body(cell.bodyElements,depth+1)+"</td>"}+"</tr>"}+"</table>"
                else->""
            }}
        }
        return documentHtml(body(doc.bodyElements))
    }
}

internal data class SheetPreview(val name:String,val rows:Int,val columns:Int)
internal class WorkbookPreview(bytes:ByteArray):AutoCloseable {
    init {require(bytes.size<=64*1024*1024){"工作簿较大，请下载后打开。"}}
    private val workbook=WorkbookFactory.create(bytes.inputStream())
    private val formatter=DataFormatter(java.util.Locale.getDefault()).apply {setUseCachedValuesForFormulaCells(true)}
    val sheets=workbook.map {s->SheetPreview(s.sheetName,
        maxOf(if(s.physicalNumberOfRows==0)0 else s.lastRowNum+1,(s.mergedRegions.maxOfOrNull {it.lastRow}?:-1)+1),
        maxOf(s.maxOfOrNull {it.lastCellNum.toInt().coerceAtLeast(0)}?:0,(s.mergedRegions.maxOfOrNull {it.lastColumn}?:-1)+1))}
    @Synchronized fun html(index:Int,startRow:Int=0,startColumn:Int=0):String {
        val sheet=workbook.getSheetAt(index);val meta=sheets[index]
        val bottom=(startRow+100).coerceAtMost(meta.rows);val right=(startColumn+30).coerceAtMost(meta.columns)
        val merged=sheet.mergedRegions.filter {it.lastRow>=startRow&&it.firstRow<bottom&&it.lastColumn>=startColumn&&it.firstColumn<right}
        val body=buildString {
            append("<table><thead><tr><th class=\"row\"></th>")
            for(col in startColumn until right)append("<th>${CellReference.convertNumToColString(col)}</th>")
            append("</tr></thead><tbody>")
            for(rowIndex in startRow until bottom) {
                append("<tr><th class=\"row\">${rowIndex+1}</th>")
                for(col in startColumn until right) {
                    val merge=merged.firstOrNull {it.isInRange(rowIndex,col)}
                    if(merge!=null&&(rowIndex!=maxOf(startRow,merge.firstRow)||col!=maxOf(startColumn,merge.firstColumn)))continue
                    val cell=sheet.getRow(merge?.firstRow?:rowIndex)?.getCell(merge?.firstColumn?:col)
                    val style=cell?.cellStyle
                    val span=merge?.let {" rowspan=\"${minOf(bottom,it.lastRow+1)-maxOf(startRow,it.firstRow)}\" colspan=\"${minOf(right,it.lastColumn+1)-maxOf(startColumn,it.firstColumn)}\""}.orEmpty()
                    val alignment=when(style?.alignment){HorizontalAlignment.RIGHT->"right";HorizontalAlignment.CENTER->"center";else->if(cell?.cellType==CellType.NUMERIC)"right"else"left"}
                    val bold=style?.let {workbook.getFontAt(it.fontIndex).bold}?:false
                    val text=cell?.let {formatter.formatCellValue(it)}.orEmpty()
                    append("<td$span style=\"text-align:$alignment;${if(bold)"font-weight:600;"else""}\">${htmlEscape(text).replace("\n","<br>")}</td>")
                }
                append("</tr>")
            }
            append("</tbody></table>")
        }
        return documentHtml(body,"body{padding:0}article{max-width:none;padding:0;border:0;box-shadow:none}table{margin:0;width:auto;min-width:100%}td{min-width:110px;max-width:360px;white-space:pre-wrap;word-wrap:break-word;font:14px/1.6 'Segoe UI','Microsoft YaHei',sans-serif}thead{position:sticky;top:0;z-index:2}th{min-width:110px;font:12px 'Segoe UI',sans-serif}.row{position:sticky;left:0;min-width:42px;width:42px;z-index:1}tbody tr:nth-child(even){background:#fafbfd}tbody tr:hover{background:#eef4ff}")
    }
    @Synchronized override fun close(){workbook.close()}
}

/** Loaded once per tab. Page rendering is serialized; closing cannot race a render. */
internal interface PagedPreview:AutoCloseable {val count:Int;fun page(index:Int,width:Int):ByteArray;fun text(index:Int):String}
internal fun loadPagedPreview(bytes:ByteArray,pdf:Boolean,password:String=""):PagedPreview {
    require(bytes.size<=96*1024*1024){"文件较大，请下载后打开。"}
    return if(pdf)object:PagedPreview {
        private val document=Loader.loadPDF(bytes,password)
        private val renderer=PDFRenderer(document).apply {isSubsamplingAllowed=true}
        override val count=document.numberOfPages
        @Synchronized override fun page(index:Int,width:Int):ByteArray {
            val box=document.getPage(index).cropBox
            require(box.width.isFinite()&&box.height.isFinite()&&box.width>0&&box.height>0){"此页尺寸无效。"}
            val scale=minOf(width.coerceIn(120,2200)/box.width,3f,8192f/maxOf(box.width,box.height),kotlin.math.sqrt(16_000_000.0/(box.width.toDouble()*box.height)).toFloat())
            return renderer.renderImage(index,scale).pngBytes()
        }
        @Synchronized override fun text(index:Int)=PDFTextStripper().apply {startPage=index+1;endPage=index+1}.getText(document)
        @Synchronized override fun close(){document.close()}
    }else object:PagedPreview {
        private val document=if(password.isBlank())SlideShowFactory.create(bytes.inputStream())else SlideShowFactory.create(bytes.inputStream(),password)
        override val count=document.slides.size
        @Synchronized override fun page(index:Int,width:Int):ByteArray {
            val size=document.pageSize
            require(size.width>0&&size.height>0){"幻灯片尺寸无效。"}
            val renderWidth=width.coerceIn(120,2200)
            val scale=minOf(renderWidth.toDouble()/size.width,8192.0/maxOf(size.width,size.height),kotlin.math.sqrt(16_000_000.0/(size.width.toDouble()*size.height)))
            val image=BufferedImage((size.width*scale).toInt().coerceAtLeast(1),(size.height*scale).toInt().coerceAtLeast(1),BufferedImage.TYPE_INT_RGB)
            val g=image.createGraphics()
            try {
                g.color=java.awt.Color.WHITE;g.fillRect(0,0,image.width,image.height)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY)
                g.setRenderingHint(Drawable.FONT_FALLBACK,mapOf("*" to "SansSerif"))
                g.scale(scale,scale);document.slides[index].draw(g)
            }finally {g.dispose()}
            return image.pngBytes()
        }
        @Synchronized override fun text(index:Int):String {
            fun contents(shapes:Iterable<*>):List<String> = shapes.flatMap {shape->when(shape) {
                is org.apache.poi.sl.usermodel.TextShape<*,*>->listOf(shape.text)
                is org.apache.poi.sl.usermodel.GroupShape<*,*>->contents(shape.shapes)
                else->emptyList()
            }}
            return contents(document.slides[index].shapes).filter {it.isNotBlank()}.joinToString("\n\n")
        }
        @Synchronized override fun close(){document.close()}
    }
}

internal fun relativeDocumentPath(base:String,target:String):String? {
    val raw=target.trim().substringBefore('#').substringBefore('?')
    if(raw.isBlank()||raw.startsWith("//")||Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(raw))return null
    val decoded=runCatching {java.net.URI(raw).path}.getOrDefault(raw).replace('\\','/')
    val path=if(decoded.startsWith('/'))decoded else base.replace('\\','/').substringBeforeLast('/',"")+"/"+decoded
    val parts=ArrayDeque<String>()
    path.split('/').forEach {when(it){"","."->Unit;".."->{if(parts.isNotEmpty())parts.removeLast()};else->parts.addLast(it)}}
    return (if(path.startsWith('/'))"/"else"")+parts.joinToString("/")
}

/** Bundle workspace assets into HTML; the embedded renderer never receives file:// or remote URLs. */
internal fun webpagePreview(document:WorkspaceDocument,read:(String)->WorkspaceDocument):String {
    require(document.bytes.size<=4*1024*1024 && document.content.length<=4*1024*1024){"网页内容较大，请切换到源码或下载查看。"}
    val html=Jsoup.parse(document.content.ifBlank {document.bytes.toString(Charsets.UTF_8)})
    require(html.allElements.size<=20000){"网页结构过于复杂，请切换到源码或下载查看。"}
    html.select("script,iframe,frame,object,embed,base,meta[http-equiv],form input,form button,link[rel!=stylesheet]").remove()
    var budget=12*1024*1024;var fetched=0
    val deadline=System.nanoTime()+8_000_000_000L
    val cache=mutableMapOf<String,WorkspaceDocument?>()
    fun asset(path:String):WorkspaceDocument?=cache.getOrPut(path){
        if(fetched++>=20||budget<=0||System.nanoTime()>deadline)null else runCatching {read(path)}.getOrNull()?.takeIf {it.bytes.size<=4*1024*1024&&it.bytes.size<=budget}?.also {budget-=it.bytes.size}
    }
    fun resource(url:String,base:String):String {
        if(url.startsWith("data:image/")&&url.length<4*1024*1024)return url
        val path=relativeDocumentPath(base,url)?:return ""
        val file=asset(path)?.takeIf {it.mimeType.startsWith("image/")||it.name.substringAfterLast('.').lowercase() in setOf("png","jpg","jpeg","gif","webp","svg")}?:return ""
        val mime=if(file.mimeType.startsWith("image/"))file.mimeType else when(file.name.substringAfterLast('.').lowercase()){ "svg"->"image/svg+xml";"jpg","jpeg"->"image/jpeg";else->"image/"+file.name.substringAfterLast('.').lowercase()}
        return imageData(file.bytes,mime)
    }
    fun css(text:String,base:String)=text.replace(Regex("(?is)@import\\s+[^;]+;"),"").replace(Regex("(?is)url\\(\\s*(['\"]?)(.*?)\\1\\s*\\)")){"url(\"${resource(it.groupValues[2],base)}\")"}.replace(Regex("(?is)expression\\s*\\([^)]*\\)"),"")
    html.select("link[rel=stylesheet]").forEach {link->
        val path=relativeDocumentPath(document.path,link.attr("href"))
        val sheet=path?.let {asset(it)}
        if(sheet!=null)html.head().appendElement("style").text(css(sheet.bytes.toString(Charsets.UTF_8),path))
        link.remove()
    }
    html.select("style").forEach {it.text(css(it.data().ifBlank {it.html()},document.path))}
    html.allElements.forEach {element->
        element.attributes().asList().filter {it.key.startsWith("on",true)||it.key in setOf("srcset","formaction","action","background","poster")}.forEach {element.removeAttr(it.key)}
        if(element.hasAttr("style"))element.attr("style",css(element.attr("style"),document.path))
        if(element.hasAttr("src"))if(element.tagName()=="img") {
            val original=element.attr("src");val bundled=resource(original,document.path)
            element.attr("src",bundled)
            if(bundled.isBlank())element.attr("alt",element.attr("alt").ifBlank {"图片未载入"})
        }else element.removeAttr("src")
        if(element.hasAttr("href")&&element.tagName()!="a")element.removeAttr("href")
        if(element.tagName()=="a") {
            val link=element.attr("href")
            if(!link.startsWith('#')&&!link.startsWith("https://")&&!link.startsWith("http://"))element.removeAttr("href")
        }
    }
    html.head().prepend("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">")
    html.head().appendElement("style").text("html{color-scheme:light}body{overflow:auto}img{max-width:100%;height:auto}*,*::before,*::after{animation:none!important;transition:none!important;scroll-behavior:auto!important}")
    return html.outerHtml()
}
