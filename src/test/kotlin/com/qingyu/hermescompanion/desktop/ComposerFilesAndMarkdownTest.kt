package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import java.awt.datatransfer.*
import java.io.File
import java.nio.file.Files
import java.util.Base64
import javax.swing.SwingUtilities
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.commonmark.node.SoftLineBreak
import org.junit.*
import org.junit.Assert.*

class ComposerFilesAndMarkdownTest {
    @Test fun windowsFileListAndUriListKeepRealFilesWithoutTreatingTextAsAFile() {
        val root=Files.createTempDirectory("hermes-file-drop").toFile()
        try {
            val file=File(root,"有 空格.md").apply {writeText("# Hello")}
            fun transfer(flavor:DataFlavor,value:Any)=object:Transferable {
                override fun getTransferDataFlavors()=arrayOf(flavor)
                override fun isDataFlavorSupported(candidate:DataFlavor)=candidate==flavor
                override fun getTransferData(candidate:DataFlavor)=if(candidate==flavor)value else throw UnsupportedFlavorException(candidate)
            }
            assertEquals(listOf(file),transferredFiles(transfer(DataFlavor.javaFileListFlavor,listOf(file,file))))
            val uris="# file list\r\n${file.toURI()}\r\nhttps://example.test/do-not-fetch\r\n"
            assertEquals(listOf(file),transferredFiles(transfer(DataFlavor("text/uri-list;class=java.lang.String"),uris)))
            assertFalse(hasFileTransfer(StringSelection(file.absolutePath)))
            assertTrue(transferredFiles(StringSelection("ordinary text")).isEmpty())
        }finally {root.deleteRecursively()}
    }
    @Test fun officeAndPdfBytesSurviveDraftCheckpointAndTextStaysInline() {
        val dir=Files.createTempDirectory("hermes-file-checkpoint")
        try {
            val file=dir.resolve("讲稿.pptx").toFile().apply {writeBytes(byteArrayOf(80,75,0,4,12))}
            val md=dir.resolve("笔记.md").toFile().apply {writeText("# 标题\n\n正文。")}
            val attachment=DesktopFiles.attachment(file)
            assertTrue(Base64.getDecoder().decode(attachment.uploadDataUrl!!.substringAfter(',')).contentEquals(file.readBytes()))
            assertEquals(md.readText(),DesktopFiles.attachment(md).textContent)
            val store=SecureConfigStore(dir.resolve("state")){ByteArray(32){37}}
            WorkspaceRepository(store,"account").save(WorkspaceState(revision=1,drafts=mapOf("work/session" to DraftRecord("分析这份讲稿",listOf(attachment)))))
            val restored=WorkspaceRepository(store,"account").load().drafts.getValue("work/session")
            assertEquals(attachment,restored.attachments.single());assertEquals("分析这份讲稿",restored.text)
        }finally {dir.toFile().deleteRecursively()}
    }
    @Test fun batchKeepsReadableFilesAndReportsRejectedOnesToTheOriginalDraft()=runBlocking<Unit>(Dispatchers.Swing) {
        val root=Files.createTempDirectory("hermes-batch-files")
        val c=DesktopController(true,SecureConfigStore(root.resolve("prefs")){ByteArray(32){18}},autoConnect=false)
        try {
            val file=root.resolve("report.pdf").toFile().apply {writeText("%PDF placeholder")}
            val directory=root.resolve("folder").toFile().apply {mkdir()}
            c.addFilesToDraft("work:a",listOf(file,directory))
            c.currentSession=HermesSession("b","B",profile="personal")
            withTimeout(4000){while((c.attachmentLoads["work:a"]?:0)>0)delay(15)}
            assertEquals(listOf("report.pdf"),c.attachments["work:a"]!!.map {it.name})
            assertTrue(c.attachmentErrors["work:a"]!!.contains("folder"));assertNull(c.attachments["personal:b"])
        }finally {c.close();root.toFile().deleteRecursively()}
    }
    @Test fun markdownBlockMapIncludesTablesCodeAndBlankLinesAndRoundTrips() {
        val raw="# 标题\n\n第一段\n折行内容\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n```kotlin\nval x = 1\n```\n\n结束。"
        val nodes=generateSequence(markdownParser.parse(raw).firstChild){it.next}.toList()
        val lines=(0..raw.lines().size).map {24f+it*25f}
        val anchors=markdownBlockAnchors(nodes,lines)
        assertEquals(nodes.size,anchors.size);assertEquals(5,anchors.size)
        assertEquals(24f+5*25,anchors[2].top,0f)
        anchors.forEach {a->val y=a.top+(a.bottom-a.top)*.43f;val p=sourceToMarkdownBlock(y,anchors)!!;assertEquals(a.index,p.index);assertEquals(y,markdownBlockToSource(p,anchors),.01f)}
        assertEquals(0f,sourceToMarkdownBlock(-30f,anchors)!!.fraction,0f)
    }
    @Test fun softLineBreaksFlowInChineseAndEnglishWithoutChangingHardBreaks() {
        fun soft(raw:String):SoftLineBreak {
            val node=markdownParser.parse(raw).firstChild
            return generateSequence(node.firstChild){it.next}.filterIsInstance<SoftLineBreak>().first()
        }
        assertEquals("",markdownSoftBreak(soft("你好\n世界")))
        assertEquals(" ",markdownSoftBreak(soft("hello\nworld")))
        val hard=markdownParser.parse("hello  \nworld").firstChild
        assertTrue(generateSequence(hard.firstChild){it.next}.any {it is org.commonmark.node.HardLineBreak})
    }
    @Test fun htmlStaticPreviewRemovesActiveContentAndBoundsAssetFanout() {
        var fetched=0
        val content="<html><head><style>p{animation:spin 1s infinite}</style><script>while(true){}</script></head><body>"+(0..30).joinToString(""){"<img src='image$it.png'>"}+"<iframe src='https://example.test'></iframe><p onclick='loop()'>Hello</p></body></html>"
        val doc=WorkspaceDocument("index.html","/work/index.html","text/html",content,content.toByteArray())
        val result=webpagePreview(doc){path->fetched++;WorkspaceDocument("x.png",path,"image/png","",byteArrayOf(1,2,3))}
        assertFalse(result.contains("<script"));assertFalse(result.contains("onclick"));assertFalse(result.contains("<iframe"))
        assertTrue(result.contains("animation:none!important"));assertEquals(20,fetched)
    }
    @Test fun webEditorFlushBridgeCommitsBeforeTheSaveCallback() {
        val sequence=mutableListOf<String>()
        SwingUtilities.invokeAndWait {EditorBridge {sequence+=it}.onChanged("draft");sequence+="save"}
        assertEquals(listOf("draft","save"),sequence)
    }
}
