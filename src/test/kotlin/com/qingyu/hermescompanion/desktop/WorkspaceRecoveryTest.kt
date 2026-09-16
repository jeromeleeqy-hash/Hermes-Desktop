package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import org.junit.*
import org.junit.Assert.*
import java.nio.file.*
import java.util.Comparator

class WorkspaceRecoveryTest {
    private lateinit var dir:Path
    private val key=ByteArray(32){42}
    private val session=HermesSession("session","计划",profile="work",workspacePath="/work",runtimeId="runtime")
    @Before fun setup(){dir=Files.createTempDirectory("hermes-workspace-test")}
    @After fun cleanup(){Files.walk(dir).use {it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)}}
    private fun store()=SecureConfigStore(dir){key}

    @Test fun restartPreservesAttachmentsQueuesRunsAndUnsavedFilesTogether() {
        val store=store();val repo=WorkspaceRepository(store,"account")
        val attachment=PendingAttachment(name="notes.txt",mimeType="text/plain",textContent="Private attachment")
        val image=PendingAttachment(name="photo.png",mimeType="image/png",dataUrl="data:image/png;base64,cHJpdmF0ZQ==")
        val q=QueuedMessage(session=session,prompt="next",attachments=listOf(attachment,image))
        val run=RunRecord(session,"first",runtimeId="runtime",attachments=listOf(attachment),attempted=true)
        val doc=EditedDocument("work","/work/notes.md","notes.md","text/markdown","original","unsaved",session)
        val original=WorkspaceState(revision=1,drafts=mapOf(session.scopedId to DraftRecord("draft",listOf(image))),queues=mapOf(session.scopedId to listOf(q)),runs=listOf(run),documents=listOf(doc),unread=setOf(session.scopedId))
        repo.save(original)
        val restored=WorkspaceRepository(store(),"account").load()
        assertEquals(original,restored)
        Files.walk(dir).use {paths->paths.filter(Files::isRegularFile).forEach {path->val text=String(Files.readAllBytes(path));assertFalse(text.contains("Private attachment"));assertFalse(text.contains("unsaved"));assertFalse(text.contains("data:image"))}}
    }
    @Test fun laterCheckpointWinsWhenBackgroundSaveArrivesOutOfOrder() {
        val repo=WorkspaceRepository(store(),"account")
        repo.save(WorkspaceState(revision=4,drafts=mapOf("s" to DraftRecord("new"))))
        repo.save(WorkspaceState(revision=3,drafts=mapOf("s" to DraftRecord("old"))))
        assertEquals("new",WorkspaceRepository(store(),"account").load().drafts["s"]?.text)
    }
    @Test fun completedTasksKeepTheirArtifactLinksAfterRestart() {
        val result=RunCompletionSummary(session.scopedId,"完成","已生成报告",listOf(ChatArtifact("/work/report.md","报告.md","document")))
        WorkspaceRepository(store(),"account").save(WorkspaceState(revision=1,completions=listOf(result)))
        assertEquals(listOf(result),WorkspaceRepository(store(),"account").load().completions)
    }
    @Test fun profilesWithIdenticalSessionIdsAndDifferentAccountsStayIsolated() {
        val a=session;val b=session.copy(profile="personal")
        val repo=WorkspaceRepository(store(),"account-a")
        repo.save(WorkspaceState(revision=1,drafts=mapOf(a.scopedId to DraftRecord("company"),b.scopedId to DraftRecord("home"))))
        assertEquals(2,repo.load().drafts.size)
        assertTrue(WorkspaceRepository(store(),"account-b").load().drafts.isEmpty())
        assertNotEquals(accountScope("https://one.example","jerome"),accountScope("https://two.example","jerome"))
    }
    @Test fun voiceCommitAndDraftArePersistedAsOneState() {
        val store=store();val note=VoiceNote(profile="work",session=session,blob=store.saveBlob(byteArrayOf(1,2,3)),transcript="你好",committed=true)
        WorkspaceRepository(store,"a").save(WorkspaceState(revision=1,voice=listOf(note),drafts=mapOf(session.scopedId to DraftRecord("你好"))))
        val result=WorkspaceRepository(store(),"a").load()
        assertTrue(result.voice.single().committed);assertEquals(result.voice.single().transcript,result.drafts[session.scopedId]?.text)
        assertArrayEquals(byteArrayOf(1,2,3),store().readBlob(result.voice.single().blob))
    }
    @Test fun authenticatedBlobsRejectTamperingAndPathTraversal() {
        val store=store();val id=store.saveBlob("private".toByteArray());val path=dir.resolve("blobs/$id.enc")
        val bytes=Files.readAllBytes(path);bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();Files.write(path,bytes)
        assertThrows(Exception::class.java){store.readBlob(id)}
        assertThrows(IllegalArgumentException::class.java){store.readBlob("../../state")}
    }
    @Test fun previousReplyCannotCompleteTheNextIdenticalPrompt() {
        val user=ChatMessage(role=MessageRole.USER,content="再来一次")
        val old=ChatMessage(role=MessageRole.ASSISTANT,content="旧回答")
        val record=RunRecord(session,"再来一次",baseline=old.recoverySignature(),baselineUser=user.recoverySignature())
        assertNull(recoveredReply(listOf(user,old),record))
        val next=user.copy(id="new-user",createdAt="later")
        val answer=old.copy(id="new-reply",content="新回答")
        assertEquals(answer,recoveredReply(listOf(user,old,next,answer),record))
    }
    @Test fun toolWorkAndOtherUsersTurnsCannotReleaseSavedQueues() {
        val record=RunRecord(session,"执行")
        val user=ChatMessage(role=MessageRole.USER,content="执行")
        val partial=ChatMessage(role=MessageRole.ASSISTANT,content="正在查询")
        assertNull(recoveredReply(listOf(user,partial,ChatMessage(role=MessageRole.TOOL,content="running")),record))
        assertNull(recoveredReply(listOf(user,partial,ChatMessage(role=MessageRole.USER,content="另一台设备发来的消息"),partial.copy(id="other")),record))
        assertNull(recoveredReply(listOf(user,partial.copy(isStreaming=true)),record))
    }
    @Test fun completionDoesNotDuplicateAlreadyStreamedText() {
        assertEquals("第一段。\n\n第二段。",mergeCompletedAssistantText("第一段。\n\n第二段。","第二段。"))
        assertEquals("正在整理，已经完成",mergeCompletedAssistantText("正在整理","正在整理，已经完成"))
        assertEquals("已完成",mergeInterimAssistantText("已完成","已完成"))
    }
    @Test fun sourceEditorEscapesHostileMarkupAndKeepsMarkdownCode() {
        val html=editableDocumentHtml("<script>alert('x')</script>\n\n```js\nalert(1)\n```","#fff","#000","#666","#123","#eee","#ddd")
        assertFalse(html.contains("<script>alert('x')</script>"));assertTrue(html.contains("&lt;script&gt;"));assertTrue(html.contains("alert(1)"))
    }
    @Test fun independentlyCroppedAvatarsStaySeparateAfterRestart() {
        val source=java.awt.image.BufferedImage(200,100,java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g=source.createGraphics();g.color=java.awt.Color.RED;g.fillRect(0,0,100,100);g.color=java.awt.Color.BLUE;g.fillRect(100,0,100,100);g.dispose()
        val store=store();val left=store.saveBlob(cropAvatar(source,1f,-1f,0f));val right=store.saveBlob(cropAvatar(source,1f,1f,0f));assertNotEquals(left,right)
        store.put("userAvatar",left);store.put("hermesAvatar",right)
        val reloaded=store();assertEquals(left,reloaded.get("userAvatar"));assertEquals(right,reloaded.get("hermesAvatar"))
        val image=javax.imageio.ImageIO.read(reloaded.readBlob(left).inputStream());assertEquals(512,image.width);assertEquals(java.awt.Color.RED.rgb,image.getRGB(256,256))
    }
    @Test fun restoreLeavesRunsInRecoveryAndQueuesAwaitingUserAction() {
        val store=store();store.put("language","zh")
        val controller=DesktopController(false,store,autoConnect=false)
        try {
            val queued=QueuedMessage(session=session,prompt="do next")
            val record=RunRecord(session,"submitted",runtimeId="runtime",attempted=true)
            controller.restoreWorkspace(WorkspaceState(runs=listOf(record),queues=mapOf(session.scopedId to listOf(queued))))
            assertTrue(controller.runs.getValue(session.scopedId).recovering)
            assertEquals("runtime",controller.runs.getValue(session.scopedId).controller.runtimeSessionId)
            assertTrue(controller.runs.getValue(session.scopedId).controller.submissionAttempted)
            assertEquals(listOf(queued),controller.queued[session.scopedId])
            assertTrue(controller.messages.isEmpty())
        } finally {controller.close()}
    }
    @Test fun sourceModeIsChosenForMarkdownThatRichTextWouldFlatten() {
        assertTrue(requiresSourceEditor("- parent\n  - child"))
        assertTrue(requiresSourceEditor("[ref]: https://example.com"))
        assertFalse(requiresSourceEditor("# Title\n\n- One\n- Two"))
    }

    @Test fun reopeningDirtyProfileFileKeepsTheOriginalConflictBaseline() {
        val controller=DesktopController(true,store())
        try {
            val original=DocumentTab(WorkspaceDocument("MEMORY.md","/profile/MEMORY.md","text/markdown","original"),"work")
            controller.selectDocument(original);controller.setDocumentText(original,"local edit")
            controller.selectDocument(original.copy(document=original.document.copy(content="changed elsewhere")))
            assertEquals("original",controller.document!!.document.content)
            assertEquals("local edit",controller.documentText())
            controller.closeDocument(original)
            assertTrue(controller.hasUnsavedChanges())
        } finally {controller.close()}
    }

}
