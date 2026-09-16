package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureCookieJar
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.*
import java.util.concurrent.*

/** Exercises real WebSocket JSON-RPC with interleaved runtimes, without a live Hermes server. */
class ConcurrentGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var client: HermesApiClient
    private lateinit var socket: WebSocket
    private val calls = CopyOnWriteArrayList<JSONObject>()
    private val answers = LinkedBlockingQueue<JSONObject>()
    @Volatile private var resumeExtra:JSONObject?=null
    @Volatile private var emitStart=true
    private val submitted = LinkedBlockingQueue<String>()
    private val workers = Executors.newCachedThreadPool()
    private var denyDirectory = false
    private var rejectMethod=""
    private var reasoning: String? = null

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path.orEmpty().startsWith("/api/auth/ws-ticket")) return MockResponse().setBody("{\"ticket\":\"test\"}")
                return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        socket = webSocket
                        event(null, "gateway.ready")
                    }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val frame = JSONObject(text)
                        if(!frame.has("method")){answers.put(frame);return}
                        calls += frame
                        val params = frame.getJSONObject("params")
                        val method = frame.getString("method")
                        val id = params.optString("session_id")
                        val result = if(method==rejectMethod)JSONObject().put("ok",false).put("message","Rejected by server")else when (method) {
                            "session.create" -> JSONObject().put("session_id", "runtime-created").put("stored_session_id", "created")
                            "session.resume" -> JSONObject().put("session_id", "runtime-$id").put("info", JSONObject().apply { reasoning?.let { put("reasoning_effort", it) } })
                            "session.cwd.set" -> JSONObject().put("ok", !denyDirectory)
                            else -> JSONObject().put("ok", true)
                        }
                        if(method=="session.resume")resumeExtra?.let {extra->extra.keys().forEach {key->result.put(key,extra.get(key))}}
                        webSocket.send(JSONObject().put("jsonrpc", "2.0").put("id", frame.get("id")).put("result", result).toString())
                        if (method == "prompt.submit") {
                            if(emitStart)event(id, "message.start")
                            submitted.put(id)
                        }
                    }
                })
            }
        }
        server.start()
        val jar = mock(SecureCookieJar::class.java)
        client = HermesApiClient(ConnectionConfig(server.url("/").toString().trimEnd('/'), "test"), jar)
    }

    @After fun close() {
        if (this::client.isInitialized) client.close()
        workers.shutdownNow()
        server.shutdown()
    }

    private fun event(runtime: String?, type: String, text: String = "") {
        val params = JSONObject().put("type", type).put("payload", JSONObject().put("text", text))
        if (runtime != null) params.put("session_id", runtime)
        socket.send(JSONObject().put("method", "event").put("params", params).toString())
    }

    private fun start(id: String, controller: StreamController, events: MutableList<StreamEvent>): Future<*> = workers.submit {
        client.streamMessage(controller, HermesSession(id = id, title = id, workspacePath = "/projects/$id"), "prompt-$id", emptyList(), events::add)
    }

    @Test fun interleavedRepliesStayInTheirOwnConversation() {
        val a = CopyOnWriteArrayList<StreamEvent>(); val b = CopyOnWriteArrayList<StreamEvent>()
        val fa = start("a", StreamController(), a)
        val fb = start("b", StreamController(), b)
        assertEquals(setOf("runtime-a", "runtime-b"), setOf(submitted.poll(15, TimeUnit.SECONDS), submitted.poll(15, TimeUnit.SECONDS)))
        event("runtime-b", "message.delta", "B1")
        event("runtime-a", "message.delta", "A1")
        event(null, "message.delta", "ambiguous")
        event("unknown", "message.complete", "wrong")
        event("runtime-a", "message.complete", "A done")
        event("runtime-b", "message.delta", "B2")
        event("runtime-b", "message.complete", "B done")
        fa.get(10, TimeUnit.SECONDS); fb.get(10, TimeUnit.SECONDS)
        assertEquals(listOf("A1"), a.filterIsInstance<StreamEvent.AssistantDelta>().map { it.text })
        assertEquals(listOf("B1", "B2"), b.filterIsInstance<StreamEvent.AssistantDelta>().map { it.text })
        assertEquals(1, a.count { it == StreamEvent.Completed })
        assertEquals(1, b.count { it == StreamEvent.Completed })
        listOf("a", "b").forEach { id ->
            val ownCalls = calls.filter { it.getJSONObject("params").optString("session_id") == "runtime-$id" }
            assertEquals(listOf("session.cwd.set", "prompt.submit"), ownCalls.map { it.getString("method") })
            assertEquals("/projects/$id", ownCalls.first().getJSONObject("params").getString("cwd"))
        }
    }

    @Test fun stoppingOneRuntimeDoesNotStopItsPeer() {
        val ca = StreamController(); val cb = StreamController()
        val a = CopyOnWriteArrayList<StreamEvent>(); val b = CopyOnWriteArrayList<StreamEvent>()
        val fa = start("a", ca, a); val fb = start("b", cb, b)
        repeat(2) { assertNotNull(submitted.poll(15, TimeUnit.SECONDS)) }
        ca.stop(); client.stopRun("runtime-a")
        fa.get(10, TimeUnit.SECONDS)
        event("runtime-a", "message.delta", "late A")
        event("runtime-b", "message.delta", "still B")
        event("runtime-b", "message.complete", "done B")
        fb.get(10, TimeUnit.SECONDS)
        assertFalse(cb.isStopped())
        assertEquals(listOf("still B"), b.filterIsInstance<StreamEvent.AssistantDelta>().map { it.text })
        assertTrue(a.none { it is StreamEvent.AssistantDelta })
        assertEquals(setOf("runtime-a"), calls.filter { it.getString("method") == "session.interrupt" }.map { it.getJSONObject("params").getString("session_id") }.toSet())
    }

    @Test fun creatingInProjectSetsRuntimeDirectoryAndPreservesRoot() {
        val session = client.createSession("/")
        assertEquals("/", session.workspacePath)
        assertEquals("runtime-created", session.runtimeId)
        assertEquals(listOf("session.create", "session.cwd.set"), calls.map { it.getString("method") })
        assertEquals("/", calls.last().getJSONObject("params").getString("cwd"))
    }

    @Test fun invalidDirectoryFailsBeforeSubmittingAnyPrompt() {
        denyDirectory = true
        val future = start("a", StreamController(), CopyOnWriteArrayList())
        assertThrows(ExecutionException::class.java) { future.get(15, TimeUnit.SECONDS) }
        assertFalse(calls.any { it.getString("method") == "prompt.submit" })
    }

    @Test fun stoppedBeforeSubmissionDoesNotCreateRuntimeOrSendMessage() {
        val controller = StreamController().apply { stop() }
        start("a", controller, CopyOnWriteArrayList()).get(5, TimeUnit.SECONDS)
        assertTrue(calls.isEmpty())
    }

    @Test fun disconnectNotifiesAllLiveConversations() {
        val ca = StreamController(); val cb = StreamController()
        val a = CopyOnWriteArrayList<StreamEvent>(); val b = CopyOnWriteArrayList<StreamEvent>()
        val fa = start("a", ca, a); val fb = start("b", cb, b)
        repeat(2) { assertNotNull(submitted.poll(15, TimeUnit.SECONDS)) }
        socket.close(1001, "test disconnect")
        fa.get(10, TimeUnit.SECONDS); fb.get(10, TimeUnit.SECONDS)
        assertTrue(ca.wasDisconnected()); assertTrue(cb.wasDisconnected())
        assertEquals(1, a.filterIsInstance<StreamEvent.ConnectionInterrupted>().size)
        assertEquals(1, b.filterIsInstance<StreamEvent.ConnectionInterrupted>().size)
    }
    @Test fun remotePdfIsSubmittedAsServerFileNotAsAnImage() {
        val attachment=com.qingyu.hermescompanion.desktop.DesktopFiles.remoteAttachment(WorkspaceDocument("报告.pdf","/projects/source/报告.pdf","application/pdf","",byteArrayOf(1,2)))
        val future=workers.submit {
            client.streamMessage(StreamController(),HermesSession("a","A",profile="sales",runtimeId="runtime-a",workspacePath="/projects/a"),"请总结",listOf(attachment)) {}
        }
        assertEquals("runtime-a",submitted.poll(15,TimeUnit.SECONDS))
        val prompt=calls.single { it.getString("method")=="prompt.submit" }.getJSONObject("params")
        assertEquals("sales",prompt.getString("profile"))
        assertTrue(prompt.getString("text").contains("/projects/source/报告.pdf"))
        assertTrue(calls.none { it.getString("method")=="image.attach_bytes" })
        event("runtime-a","message.complete","已处理")
        future.get(10,TimeUnit.SECONDS)
    }

    @Test fun voiceFastReplyIsSessionScopedAndRestoredBeforeCompletion() {
        reasoning = "high"
        val events = CopyOnWriteArrayList<StreamEvent>()
        val task = workers.submit {
            client.streamVoiceMessage(StreamController(), HermesSession("voice", "语音", profile="work"), "今天的安排", true, {}, events::add)
        }
        assertEquals("runtime-voice", submitted.poll(15, TimeUnit.SECONDS))
        event("runtime-voice", "message.complete", "今天有三件事。")
        task.get(15, TimeUnit.SECONDS)
        val changes = calls.filter { it.optString("method") == "config.set" }.map { it.getJSONObject("params") }
        assertEquals(listOf("none", "high"), changes.map { it.getString("value") })
        assertTrue(changes.all { it.getString("scope") == "session" && it.getString("profile") == "work" && it.getString("session_id") == "runtime-voice" })
        assertEquals(1, events.count { it == StreamEvent.Completed })
    }

    @Test fun newAssistantSessionUsesCapturedProfileEvenIfClientSelectionChanged() {
        client.setProfile("personal")
        val session = client.createSessionForProfile("/work/company", "work")
        assertEquals("work", session.profile)
        assertEquals("/work/company", session.workspacePath)
        val scopedCalls = calls.filter { it.optString("method") in setOf("session.create", "session.cwd.set") }
        assertEquals(2, scopedCalls.size)
        assertTrue(scopedCalls.all { it.getJSONObject("params").getString("profile") == "work" })
    }

    @Test fun quickTextTurnKeepsAttachmentsAndRestoresReasoningWithoutChangingTheModel() {
        reasoning = "high"
        val task = workers.submit {
            client.streamVoiceMessage(StreamController(), HermesSession("quick", "快问", profile="work"), "这份资料说了什么", true, {}, {},
                attachments = listOf(com.qingyu.hermescompanion.model.PendingAttachment(name="sample.txt", mimeType="text/plain", textContent="CONTENT_SAMPLE_42")))
        }
        assertEquals("runtime-quick", submitted.poll(15, TimeUnit.SECONDS))
        val prompt = calls.single { it.optString("method") == "prompt.submit" }.getJSONObject("params")
        assertTrue(prompt.getString("text").contains("CONTENT_SAMPLE_42"))
        assertEquals("work", prompt.getString("profile"))
        event("runtime-quick", "message.complete", "这是资料摘要。")
        task.get(15, TimeUnit.SECONDS)
        assertTrue(calls.none { it.optString("method") == "slash.exec" })
        assertEquals(listOf("none", "high"), calls.filter { it.optString("method") == "config.set" }.map { it.getJSONObject("params").getString("value") })
    }

    @Test fun unsupportedVoiceReasoningStillSendsWithoutChangingConfig() {
        reasoning = null
        val notices = CopyOnWriteArrayList<String>()
        val task = workers.submit {
            client.streamVoiceMessage(StreamController(), HermesSession("old", "旧网关"), "你好", true, notices::add, {})
        }
        assertEquals("runtime-old", submitted.poll(15, TimeUnit.SECONDS))
        event("runtime-old", "message.complete", "你好。")
        task.get(15, TimeUnit.SECONDS)
        assertTrue(calls.none { it.optString("method") == "config.set" })
        assertEquals(1, notices.size)
    }

    @Test fun checkpointFailurePreventsPromptFromReachingServer() {
        val controller=StreamController().apply {beforeSubmission={throw java.io.IOException("disk full")}}
        val future=start("a",controller,CopyOnWriteArrayList())
        assertThrows(ExecutionException::class.java){future.get(15,TimeUnit.SECONDS)}
        assertTrue(controller.submissionAttempted)
        assertNotNull(controller.runtimeSessionId)
        assertFalse(calls.any {it.getString("method")=="prompt.submit"})
    }

    @Test fun explicitSubmissionRejectionIsDifferentFromAnUnknownDisconnect() {
        rejectMethod="prompt.submit"
        val controller=StreamController()
        val future=start("a",controller,CopyOnWriteArrayList())
        assertThrows(ExecutionException::class.java){future.get(15,TimeUnit.SECONDS)}
        assertTrue(controller.submissionRejected)
        assertFalse(controller.submissionAccepted)
        assertFalse(controller.wasDisconnected())
    }
    @Test fun rejectedInterruptDoesNotReportSuccess() {
        rejectMethod="session.interrupt"
        assertThrows(ApiException::class.java){client.stopRunChecked("runtime-a")}
    }

    private fun control(runtime:String,type:String,payload:JSONObject) {
        socket.send(JSONObject().put("method","event").put("params",JSONObject().put("session_id",runtime).put("type",type).put("payload",payload)).toString())
    }
    private fun serverRequest(runtime:String,id:String,method:String,payload:JSONObject) {
        socket.send(JSONObject().put("jsonrpc","2.0").put("method",method).put("id",id).put("params",payload.put("session_id",runtime)).toString())
    }
    private fun awaitRequest(events:List<StreamEvent>):AgentRequest {
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(System.nanoTime()<until){events.filterIsInstance<StreamEvent.AgentRequestPending>().firstOrNull()?.let {return it.request};Thread.sleep(10)}
        error("Request did not reach UI")
    }
    @Test fun reasoningChunksKeepWhitespaceAndSplitWordsExactly() {
        val events=CopyOnWriteArrayList<StreamEvent>();val f=start("spaces",StreamController(),events)
        assertEquals("runtime-spaces",submitted.poll(15,TimeUnit.SECONDS))
        val tokens=listOf("I", " ", "need to", " check", "\n\n", "  some", "thing.", " 中文 ")
        tokens.forEachIndexed {i,t->event("runtime-spaces",if(i%2==0)"reasoning.delta"else"thinking.delta",t)}
        event("runtime-spaces","message.complete","done");f.get(5,TimeUnit.SECONDS)
        assertEquals(tokens.joinToString(""),events.filterIsInstance<StreamEvent.ReasoningDelta>().joinToString(""){it.text})
    }
    @Test fun modernClarifyIsAnsweredWithMatchingJsonRpcResponse() {
        val events=CopyOnWriteArrayList<StreamEvent>();val f=start("rpc",StreamController(),events)
        assertEquals("runtime-rpc",submitted.poll(15,TimeUnit.SECONDS))
        serverRequest("runtime-rpc","srq-choice","clarify",JSONObject("""{"question":"Which file?","choices":["Keep","Delete"]}"""))
        val r=awaitRequest(events)
        assertEquals("srq-choice",r.requestId);assertEquals(listOf("Keep","Delete"),r.choices.map {it.label})
        client.respondAgentRequest(r,"Keep")
        val reply=answers.poll(5,TimeUnit.SECONDS)!!
        assertEquals("srq-choice",reply.getString("id"));assertEquals("Keep",reply.getJSONObject("result").getString("answer"));assertFalse(reply.has("method"))
        assertFalse(calls.any {it.optString("method")=="clarify.respond"})
        event("runtime-rpc","message.complete","Continued");f.get(5,TimeUnit.SECONDS)
    }
    @Test fun legacyClarifyBeforeMessageStartIsVisibleAndKeepsLegacyResponse() {
        emitStart=false
        val events=CopyOnWriteArrayList<StreamEvent>();val f=start("legacy",StreamController(),events)
        assertEquals("runtime-legacy",submitted.poll(15,TimeUnit.SECONDS))
        control("runtime-legacy","clarify.request",JSONObject("""{"request_id":"old-1","questions":[{"question":"Choose","options":["A","B"]}]}"""))
        val r=awaitRequest(events);assertNull(r.serverRpcId);assertTrue(r.questions.isEmpty())
        client.respondAgentRequest(r,"B")
        val params=calls.single {it.optString("method")=="clarify.respond"}.getJSONObject("params")
        assertEquals("old-1",params.getString("request_id"));assertEquals("B",params.getString("answer"))
        event("runtime-legacy","message.start");event("runtime-legacy","message.complete","done");f.get(5,TimeUnit.SECONDS)
    }
    @Test fun resumingWaitingSessionRestoresAllQuestionsAndTheirWireIds() {
        resumeExtra=JSONObject("""{"running":true,"status":"waiting","open_requests":[{"id":"srq-batch","method":"clarify","params":{"session_id":"runtime-batch","questions":[{"qid":"file","question":"File?","choices":["A","B"]},{"qid":"format","question":"Format?","choices":["PDF","Word"]}],"answers":{"file":"A"}}}]}""")
        val restored=client.resumeSession(HermesSession("batch","Batch"))
        assertEquals(true,restored.running)
        val r=restored.pendingRequests.single()
        assertEquals("srq-batch",r.requestId);assertEquals(listOf("file","format"),r.questions.map {it.id});assertEquals("A",r.questions.first().lockedAnswer)
        client.respondAgentRequest(r,"",mapOf("file" to "A","format" to "PDF"))
        val result=answers.poll(5,TimeUnit.SECONDS)!!.getJSONObject("result").getJSONObject("answers")
        assertEquals("A",result.getString("file"));assertEquals("PDF",result.getString("format"))
    }
    @Test fun approvalAndCancellationStayScopedToTheirRuntime() {
        val a=CopyOnWriteArrayList<StreamEvent>();val b=CopyOnWriteArrayList<StreamEvent>()
        val fa=start("a",StreamController(),a);val fb=start("b",StreamController(),b)
        repeat(2){assertNotNull(submitted.poll(15,TimeUnit.SECONDS))}
        serverRequest("runtime-b","srq-approval","approval",JSONObject("""{"command":"remove temp","choices":["once","deny"]}"""))
        val r=awaitRequest(b);assertEquals(AgentRequestType.APPROVAL,r.type);assertTrue(a.none {it is StreamEvent.AgentRequestPending})
        client.respondAgentRequest(r,"deny")
        assertEquals("deny",answers.poll(5,TimeUnit.SECONDS)!!.getJSONObject("result").getString("choice"))
        control("runtime-b","request.cancel",JSONObject().put("id","srq-approval"))
        event("runtime-a","message.complete","A");event("runtime-b","message.complete","B")
        fa.get(5,TimeUnit.SECONDS);fb.get(5,TimeUnit.SECONDS)
        assertEquals(1,b.filterIsInstance<StreamEvent.AgentRequestExpired>().size);assertTrue(a.none {it is StreamEvent.AgentRequestExpired})
    }

    @Test fun unfamiliarServerRequestIsVisibleAndOnlyRejectedOnUserAction() {
        val events=CopyOnWriteArrayList<StreamEvent>();val f=start("auth",StreamController(),events)
        assertEquals("runtime-auth",submitted.poll(15,TimeUnit.SECONDS))
        serverRequest("runtime-auth","srq-auth","connection",JSONObject().put("title","Connect service").put("auth_url","https://example.com/auth"))
        val r=awaitRequest(events)
        assertEquals(AgentRequestType.ACTION_REQUIRED,r.type)
        assertEquals("connection",r.method);assertEquals("https://example.com/auth",r.actionUrl)
        assertNull(answers.poll(100,TimeUnit.MILLISECONDS))
        client.respondAgentRequest(r,"unsupported")
        assertEquals(-32601,answers.poll(5,TimeUnit.SECONDS)!!.getJSONObject("error").getInt("code"))
        event("runtime-auth","message.complete","done");f.get(5,TimeUnit.SECONDS)
    }
    @Test fun directCancellationPreventsSubmittingAnExpiredAnswer() {
        val events=CopyOnWriteArrayList<StreamEvent>();val f=start("cancel",StreamController(),events)
        assertEquals("runtime-cancel",submitted.poll(15,TimeUnit.SECONDS))
        serverRequest("runtime-cancel","srq-cancel","clarify",JSONObject().put("question","Choose?"))
        val r=awaitRequest(events)
        socket.send(JSONObject().put("jsonrpc","2.0").put("method","request.cancel").put("params",JSONObject().put("id","srq-cancel")).toString())
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(events.none {it is StreamEvent.AgentRequestExpired}&&System.nanoTime()<until)Thread.sleep(10)
        assertTrue(events.any {it is StreamEvent.AgentRequestExpired})
        assertThrows(IllegalArgumentException::class.java){client.respondAgentRequest(r,"A")}
        event("runtime-cancel","message.complete","done");f.get(5,TimeUnit.SECONDS)
    }

}
