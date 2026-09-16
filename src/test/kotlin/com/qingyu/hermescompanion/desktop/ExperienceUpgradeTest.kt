package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import com.qingyu.hermescompanion.model.WorkspaceDocument
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.jsoup.Jsoup
import org.junit.Test
import org.junit.Assert.*
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ExperienceUpgradeTest {
    @Test fun maximizeUsesWorkAreaOnEveryTaskbarEdgeAndNegativeMonitor() {
        val monitor=Rectangle(-1920,-200,1920,1080)
        assertEquals(Rectangle(0,0,1920,1032),maximizeGeometry(monitor,Rectangle(-1920,-200,1920,1032)))
        assertEquals(Rectangle(48,0,1872,1080),maximizeGeometry(monitor,Rectangle(-1872,-200,1872,1080)))
        assertEquals(Rectangle(0,40,1920,1040),maximizeGeometry(monitor,Rectangle(-1920,-160,1920,1040)))
        assertEquals(Rectangle(0,0,1860,1080),maximizeGeometry(monitor,Rectangle(-1920,-200,1860,1080)))
        assertEquals(Rectangle(0,0,2560,1392),maximizeGeometry(Rectangle(1920,0,2560,1440),Rectangle(1920,0,2560,1392)))
    }
    @Test fun editorContinuesListsExitsEmptyItemsAndDoesNotInterfereWithCompositionOrCode() {
        fun enter(s:String)=continueMarkdownLine(TextFieldValue(s,TextRange(s.length))).text
        assertEquals("- 写文档\n- ",enter("- 写文档"))
        assertEquals("  - [x] 已完成\n  - [ ] ",enter("  - [x] 已完成"))
        assertEquals("9. 内容\n10. ",enter("9. 内容"))
        assertEquals("> 引用\n> ",enter("> 引用"))
        assertEquals("",enter("- [ ] "))
        assertEquals("```\n- 字符串\n",enter("```\n- 字符串"))
        val input=TextFieldValue("ni",TextRange(2),TextRange(0,2))
        assertEquals(input,continueMarkdownLine(input))
    }
    @Test fun replaceAndFormatRemainReversibleWithoutLosingOtherSource() {
        val original=TextFieldValue("# 标题\n\n你好 Hello hello",TextRange(8,13))
        val bold=formatMarkdown(original,"bold")
        assertEquals(original.text,formatMarkdown(bold,"bold").text)
        val replacement=replaceMarkdownMatch(original,"hello","世界",true)
        assertEquals("# 标题\n\n你好 世界 世界",replacement.text)
        val h=MarkdownUndoHistory();h.record(original,replacement)
        assertEquals(original.text,h.undo(replacement)?.text)
        assertEquals("### 标题",formatMarkdown(TextFieldValue("# 标题"),"h3").text)
    }
    @Test fun wordPreservesChineseHeadingsEmphasisTableAndEmbeddedImage() {
        val bytes=ByteArrayOutputStream().use {out->XWPFDocument().use {doc->
            doc.createParagraph().apply {style="Heading1";createRun().setText("项目进展")}
            doc.createParagraph().createRun().apply {isBold=true;setText("关键结论 & <原样>")}
            doc.createTable(2,2).apply {getRow(0).getCell(0).text="事项";getRow(1).getCell(1).text="已完成"}
            val image=BufferedImage(20,10,BufferedImage.TYPE_INT_RGB).pngBytes()
            doc.createParagraph().createRun().addPicture(image.inputStream(),org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,"图.png",20000,10000)
            doc.write(out)
        };out.toByteArray()}
        val html=wordPreview(bytes,false);val dom=Jsoup.parse(html)
        assertEquals("项目进展",dom.selectFirst("h1")?.text())
        assertTrue(html.contains("font-weight:600"));assertTrue(dom.text().contains("关键结论 & <原样>"))
        assertEquals(4,dom.select("td").size);assertTrue(dom.selectFirst("img")!!.attr("src").startsWith("data:image/png;base64,"))
    }
    @Test fun spreadsheetsSupportBothFormatsPaginationMergedCellsAndCachedFormulaValues() {
        for(legacy in listOf(false,true)) {
            val bytes=ByteArrayOutputStream().use {out->
                val book=if(legacy)HSSFWorkbook()else XSSFWorkbook()
                book.use {wb->
                    val sheet=wb.createSheet("工作安排")
                    for(i in 0..104)sheet.createRow(i).createCell(0).setCellValue("第${i+1}行")
                    sheet.getRow(0).createCell(1).apply {setCellValue(1234.5);cellStyle=wb.createCellStyle().apply {dataFormat=wb.createDataFormat().getFormat("#,##0.00")}}
                    sheet.getRow(1).createCell(1).cellFormula="1+2"
                    wb.creationHelper.createFormulaEvaluator().evaluateAll() // author a fixture with saved formula results
                    sheet.addMergedRegion(CellRangeAddress(2,3,1,2));sheet.getRow(2).createCell(1).setCellValue("合并单元格")
                    wb.createSheet("第二张");wb.write(out)
                };out.toByteArray()
            }
            WorkbookPreview(bytes).use {preview->
                assertEquals(listOf("工作安排","第二张"),preview.sheets.map {it.name})
                assertEquals(105,preview.sheets.first().rows)
                val html=preview.html(0);val dom=Jsoup.parse(html)
                assertTrue(dom.text().contains("1,234.50"));assertEquals(100,dom.select("tbody tr").size)
                assertEquals("合并单元格",dom.selectFirst("td[rowspan=2][colspan=2]")!!.text())
                assertEquals(5,Jsoup.parse(preview.html(0,100)).select("tbody tr").size)
                assertTrue(dom.select("tbody tr")[1].text().contains("3"))
            }
        }
    }
    @Test fun pdfAndBothPowerpointFormatsRenderFromOneOpenResource() {
        val pdf=ByteArrayOutputStream().use {out->PDDocument().use {doc->
            val p=PDPage();doc.addPage(p);doc.addPage(PDPage())
            PDPageContentStream(doc,p).use {stream->stream.beginText();stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA),16f);stream.newLineAtOffset(40f,700f);stream.showText("Hermes document preview");stream.endText()};doc.save(out)
        };out.toByteArray()}
        loadPagedPreview(pdf,true).use {p->assertEquals(2,p.count);assertTrue(p.text(0).contains("Hermes"));assertNotNull(ImageIO.read(p.page(0,600).inputStream()));assertNotNull(ImageIO.read(p.page(1,600).inputStream()))}
        for(legacy in listOf(false,true)) {
            val ppt=ByteArrayOutputStream().use {out->
                val deck=if(legacy)HSLFSlideShow()else XMLSlideShow()
                deck.use {doc->doc.createSlide();doc.createSlide();doc.write(out)};out.toByteArray()
            }
            loadPagedPreview(ppt,false).use {p->assertEquals(2,p.count);assertEquals(600,ImageIO.read(p.page(0,600).inputStream()).width);assertNotNull(ImageIO.read(p.page(1,300).inputStream()))}
        }
    }
    @Test fun webpageBundlesRelativeAssetsAndRemovesActiveContent() {
        val image=BufferedImage(8,8,BufferedImage.TYPE_INT_RGB).pngBytes()
        val fetched=mutableListOf<String>()
        val source=WorkspaceDocument("index.html","/project/site/index.html","text/html","""<link rel="stylesheet" href="css/site.css"><script>alert(1)</script><h1 onclick="bad()">网页</h1><img src="../photo.png"><img src="https://remote.invalid/track"><iframe src="file:///secret"></iframe><a href="javascript:bad()">bad</a>""")
        val html=webpagePreview(source){path->fetched+=path;when(path){"/project/site/css/site.css"->WorkspaceDocument("site.css",path,"text/css","h1{color:red}body{background-image:url('../../photo.png')} @import 'https://evil.invalid/a.css';");"/project/photo.png"->WorkspaceDocument("photo.png",path,"image/png","",image);else->error(path)}}
        val dom=Jsoup.parse(html)
        assertEquals(setOf("/project/site/css/site.css","/project/photo.png"),fetched.toSet())
        assertTrue(dom.select("script,iframe,[onclick]").isEmpty());assertFalse(html.contains("javascript:"));assertFalse(html.contains("remote.invalid"));assertFalse(html.contains("evil.invalid"))
        assertTrue(dom.selectFirst("img")!!.attr("src").startsWith("data:image/png;base64,"));assertTrue(html.contains("color:red"))
        assertEquals("/project/说明.md",relativeDocumentPath("/project/site/index.md","../%E8%AF%B4%E6%98%8E.md#标题"))
        assertNull(relativeDocumentPath(source.path,"file:///etc/passwd"))
    }
    @Test fun avatarCropIsTransparentPngAndImageScalingIsStableAtDifferentViewports() {
        val source=BufferedImage(400,200,BufferedImage.TYPE_INT_ARGB)
        source.createGraphics().apply {color=java.awt.Color.RED;fillRect(0,0,200,200);color=java.awt.Color.BLUE;fillRect(200,0,200,200);dispose()}
        val left=ImageIO.read(cropAvatar(source,1f,-1f,0f).inputStream());val right=ImageIO.read(cropAvatar(source,1f,1f,0f).inputStream())
        assertEquals(512,left.width);assertEquals(java.awt.Color.RED.rgb,left.getRGB(256,256));assertEquals(java.awt.Color.BLUE.rgb,right.getRGB(256,256))
        assertEquals(.46f,fitImageScale(1000,500,IntSize(500,600),0),.001f)
        assertEquals(.56f,fitImageScale(1000,500,IntSize(500,600),1),.001f)
        assertEquals(androidx.compose.ui.geometry.Offset(270f,0f),clampImagePan(androidx.compose.ui.geometry.Offset(9000f,9000f),1000,500,IntSize(500,600),1f,0))
    }
}
