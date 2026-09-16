package com.qingyu.hermescompanion.desktop

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.sl.usermodel.PictureData
import org.junit.Test
import org.junit.Assert.*
import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.*

internal object OfficeDisplayFixtures {
    fun photo():ByteArray=BufferedImage(180,70,BufferedImage.TYPE_INT_RGB).apply {createGraphics().apply {color=Color(62,114,235);fillRect(0,0,180,70);color=Color.WHITE;fillRect(16,16,148,10);fillRect(16,40,98,10);dispose()}}.pngBytes()
    fun word():ByteArray=ByteArrayOutputStream().use {out->XWPFDocument().use {doc->
        doc.createParagraph().apply {style="Heading1";createRun().setText("Hermes · 项目周报")}
        doc.createParagraph().createRun().setText("这份文档在软件内直接阅读。中文、标题、表格和图片都应清晰显示。")
        doc.createTable(3,3).apply {getRow(0).getCell(0).text="事项";getRow(0).getCell(1).text="负责人";getRow(0).getCell(2).text="进展";getRow(1).getCell(0).text="文档预览";getRow(1).getCell(1).text="Jerome";getRow(1).getCell(2).text="已完成";getRow(2).getCell(0).text="桌面助手";getRow(2).getCell(2).text="验证中"}
        doc.createParagraph().createRun().addPicture(photo().inputStream(),org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,"example.png",180*9525,70*9525)
        doc.write(out)
    };out.toByteArray()}
    fun workbook(legacy:Boolean=false):ByteArray=ByteArrayOutputStream().use {out->
        val wb=if(legacy)HSSFWorkbook()else XSSFWorkbook()
        wb.use {b->val s=b.createSheet("项目清单");for(r in 0..104){val row=s.createRow(r);listOf("第 ${r+1} 项","Hermes","完成",(r*20).toString()).forEachIndexed {i,t->row.createCell(i).setCellValue(t)}};b.write(out)};out.toByteArray()
    }
    fun slides(legacy:Boolean):ByteArray=ByteArrayOutputStream().use {out->
        if(legacy)HSLFSlideShow().use {doc->
            val slide=doc.createSlide();slide.createTextBox().apply {anchor=java.awt.geom.Rectangle2D.Double(35.0,30.0,550.0,100.0);setText("Hermes Desktop — 工作计划")}
            val picture=doc.addPicture(photo(),PictureData.PictureType.PNG);slide.createPicture(picture).anchor=java.awt.geom.Rectangle2D.Double(50.0,150.0,400.0,160.0);doc.write(out)
        }else XMLSlideShow().use {doc->
            val slide=doc.createSlide();slide.createTextBox().apply {anchor=java.awt.geom.Rectangle2D.Double(35.0,30.0,550.0,100.0);setText("Hermes Desktop — 工作计划")}
            val picture=doc.addPicture(photo(),PictureData.PictureType.PNG);slide.createPicture(picture).anchor=java.awt.geom.Rectangle2D.Double(50.0,150.0,400.0,160.0);doc.write(out)
        };out.toByteArray()
    }
    fun render(html:String,zoom:Double=1.0):BufferedImage {
        val page=compatiblePage(html,zoom);val result=BufferedImage(1050,760,BufferedImage.TYPE_INT_RGB)
        fun paint(){val editor=JEditorPane().apply {isEditable=false;editorKit=page.kit;document=page.document;background=Color.WHITE;setSize(1050,760)};val g=result.createGraphics();g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);editor.printAll(g);g.dispose()}
        if(SwingUtilities.isEventDispatchThread())paint()else SwingUtilities.invokeAndWait(::paint)
        return result
    }
}

class OfficeDisplayTest {
    @Test fun officeContentActuallyPaintsWithoutJavafx() {
        val word=wordPreview(OfficeDisplayFixtures.word(),false)
        for(html in listOf(word,WorkbookPreview(OfficeDisplayFixtures.workbook()).use {it.html(0)})) {
            val page=compatiblePage(html,1.0);assertTrue(page.document.length>40)
            val image=OfficeDisplayFixtures.render(html)
            var ink=0
            for(y in 0 until image.height step 3)for(x in 0 until image.width step 3){val color=Color(image.getRGB(x,y));if(color.red<150&&color.green<150&&color.blue<150)ink++}
            assertTrue("Document should paint readable text, not a blank rectangle: $ink ink pixels",ink>100)
        }
    }
    @Test fun embeddedImagesUseOfflineCacheAndActiveHtmlIsRemoved() {
        val source="<script>BAD_SCRIPT</script><iframe src='https://bad.invalid'></iframe><h1>可读内容</h1><img src='data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(OfficeDisplayFixtures.photo())+"'><img src='file:///secret' alt='不载入本地路径'><a href='javascript:alert(1)'>点击</a>"
        val page=compatiblePage(source,1.0)
        val text=page.document.getText(0,page.document.length)
        assertTrue(text.contains("可读内容"));assertFalse(text.contains("BAD_SCRIPT"));assertTrue(text.contains("不载入本地路径"))
        assertEquals(1,(page.document.getProperty("imageCache") as Map<*,*>).size)
    }
    @Test fun pptAndPptxPaintTextAndPicturesInsteadOfOnlyCreatingBlankSlides() {
        for(legacy in listOf(false,true))loadPagedPreview(OfficeDisplayFixtures.slides(legacy),false).use {p->
            val image=ImageIO.read(p.page(0,1000).inputStream());var blue=0;var ink=0
            for(y in 0 until image.height step 3)for(x in 0 until image.width step 3){val c=Color(image.getRGB(x,y));if(c.blue>c.red+60)blue++;if(c.red<150&&c.green<150&&c.blue<150)ink++}
            assertTrue("PowerPoint picture is visible",blue>100);assertTrue("PowerPoint text is visible",ink>25)
        }
    }
}
