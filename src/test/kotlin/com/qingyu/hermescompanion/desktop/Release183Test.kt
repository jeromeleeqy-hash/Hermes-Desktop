package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.util.Base64

class Release183Test {
    @Test fun previewUsesTheExactDraftBytesAndDoesNotRemoveOrUploadThem() {
        val bytes=byteArrayOf(0,1,2,3,127,-1)
        val file=PendingAttachment(name="截图.png",mimeType="image/png",dataUrl="data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes))
        assertArrayEquals(bytes,localAttachmentDocument(file)!!.bytes)
        assertNotNull(file.dataUrl);assertNull(file.remotePath)
        val pdf=file.copy(name="合同.pdf",mimeType="application/pdf",dataUrl=null,uploadDataUrl=file.dataUrl)
        assertArrayEquals(bytes,localAttachmentDocument(pdf)!!.bytes)
        assertEquals("Hello world\n下一行",localAttachmentDocument(PendingAttachment(name="notes.txt",mimeType="text/plain",textContent="Hello world\n下一行"))!!.content)
        assertNull(localAttachmentDocument(PendingAttachment(name="remote.pdf",mimeType="application/pdf",remotePath="/remote.pdf")))
    }
    @Test fun inboundRequestsPopulateTaskCenterAndExpireOnlyTheirOwnRecord()=runBlocking<Unit>(Dispatchers.Swing) {
        val dir=Files.createTempDirectory("hermes-183-decisions")
        val c=DesktopController(true,SecureConfigStore(dir.resolve("prefs")){ByteArray(32){19}},autoConnect=false)
        try {
            c.decisions.clear();c.runs.clear();c.notifications=false
            val s=HermesSession("s","Current",runtimeId="runtime-s")
            val r=AgentRequest("srq-1","runtime-s",type=AgentRequestType.CLARIFICATION,title="Which?",serverRpcId="\"srq-1\"")
            c.receiveAgentControl(s,StreamEvent.AgentRequestPending(r))
            assertEquals(1,c.decisions.size);assertEquals("等待你的处理",c.runs[s.scopedId]?.status)
            c.receiveAgentControl(s,StreamEvent.AgentRequestPending(r))
            assertEquals(1,c.decisions.size)
            c.receiveAgentControl(s,StreamEvent.AgentRequestExpired("different"));assertEquals(1,c.decisions.size)
            c.receiveAgentControl(s,StreamEvent.AgentRequestExpired("srq-1"));assertTrue(c.decisions.isEmpty())
        }finally {c.close();dir.toFile().deleteRecursively()}
    }
    @Test fun persistedBatchRequestRetainsWireIdQuestionsAndLockedAnswers() {
        val request=AgentRequest("srq-1","runtime",type=AgentRequestType.CLARIFICATION,title="Two questions",serverRpcId="\"srq-1\"",questions=listOf(
            AgentQuestion("one","Question one",listOf(AgentRequestChoice("A","a")),lockedAnswer="a"),
            AgentQuestion("two","Question two",listOf(AgentRequestChoice("B","b")),allowMultiple=true)))
        val decision=PendingDecision("default",HermesSession("s","Task"),request)
        assertEquals(listOf(decision),DecisionCodec.decode(DecisionCodec.encode(listOf(decision))))
    }
}
