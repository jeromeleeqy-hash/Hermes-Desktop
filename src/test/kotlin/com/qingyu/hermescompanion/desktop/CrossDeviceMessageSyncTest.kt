package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.data.StreamController
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import okhttp3.mockwebserver.*
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real HTTP -> controller -> Compose state; the second device changes the stored tail. */
class CrossDeviceMessageSyncTest {
    private lateinit var server:MockWebServer
    private lateinit var c:DesktopController
    private lateinit var root:Path
    @Volatile private var agentRunning=false
    @Volatile private var ticketStatus=200
    @Volatile private var socket:WebSocket?=null
    private val promptSubmits=AtomicInteger()
    private val key="default::daily"
    @Volatile private var total=70
    @Volatile private var messageStatus=200
    @Volatile private var blockMessages=false
    private val reading=CountDownLatch(1)
    private val release=CountDownLatch(1)
    private val messageReads=AtomicInteger()
    private val mobileUser="记个账\n@image:/root/.hermes/images/receipt.jpg"

    @Before fun setup()=runBlocking<Unit> {
        root=Files.createTempDirectory("hermes-cross-device")
        server=MockWebServer()
        server.dispatcher=object:Dispatcher(){override fun dispatch(r:RecordedRequest):MockResponse {
            val u=r.requestUrl!!
            return when(u.encodedPath){
                "/api/status"->json("""{"auth_required":true,"auth_providers":["basic"]}""")
                "/api/auth/providers"->json("""{"providers":[{"name":"basic","supports_password":true}]}""")
                "/auth/password-login"->json("""{"ok":true}""").addHeader("Set-Cookie","hermes_session=test-only; Path=/; HttpOnly")
                "/api/auth/ws-ticket"->json("""{"ticket":"test-ticket"}""",ticketStatus)
                "/api/ws"->MockResponse().withWebSocketUpgrade(object:WebSocketListener(){
                    override fun onOpen(ws:WebSocket,response:Response){socket=ws;ws.send("""{"method":"event","params":{"type":"gateway.ready","payload":{}}}""")}
                    override fun onClosing(ws:WebSocket,code:Int,reason:String){ws.close(code,reason)}
                    override fun onMessage(ws:WebSocket,text:String){
                        val frame=JSONObject(text)
                        if(frame.optString("method")=="prompt.submit")promptSubmits.incrementAndGet()
                        val result=if(frame.optString("method")=="session.resume")JSONObject()
                            .put("session_id","runtime-daily").put("running",agentRunning).put("open_requests",JSONArray()).put("messages",JSONArray())
                            else JSONObject().put("ok",true)
                        ws.send(JSONObject().put("jsonrpc","2.0").put("id",frame.get("id")).put("result",result).toString())
                    }
                })
                "/api/auth/me"->json("""{"user_id":"test"}""")
                "/api/profiles"->json("""{"profiles":[{"name":"default"}]}""")
                "/api/projects"->json("""{"projects":[]}""")
                // Deliberately stale sidebar count: body synchronization must not depend on it.
                "/api/sessions"->json("""{"sessions":[{"id":"daily","title":"日常助理","message_count":70}],"total":1}""")
                "/api/sessions/daily"->json("""{"id":"daily","title":"日常助理","message_count":$total}""")
                "/api/sessions/daily/messages"->{
                    messageReads.incrementAndGet()
                    if(blockMessages){reading.countDown();if(!release.await(10,TimeUnit.SECONDS))return json("{}",500)}
                    if(messageStatus!=200)return json("""{"detail":"temporary failure"}""",messageStatus)
                    val offset=u.queryParameter("offset")!!.toInt();val limit=u.queryParameter("limit")!!.toInt()
                    json(JSONObject().put("messages",JSONArray().apply {
                        (offset until minOf(total,offset+limit)).forEach {i->put(JSONObject().put("id","m$i").put("role",if(i%2==0)"user" else "assistant")
                            .put("content",when(i){70->mobileUser;71->"已记录。✅";else->"历史消息 $i"}))}
                    }).toString())
                }
                else->json("{}")
            }
        }}
        server.start()
        withContext(Dispatchers.Swing){c=DesktopController(false,SecureConfigStore(root){ByteArray(32){21}},autoConnect=false);c.connect(server.url("/").toString(),"test","test-password")}
        awaitState {c.connected&&!c.busy&&!c.sessionsLoading&&!c.artifactsIndexing&&c.sessions.isNotEmpty()}
        withContext(Dispatchers.Swing){c.currentSession=c.sessions.single();c.page=Page.CHAT;c.syncVisibleConversations()}
        awaitState {c.messages[key]?.lastOrNull()?.id=="m69"&&c.messageLoading[key]!=true}
    }
    @After fun close()=runBlocking<Unit> {
        release.countDown()
        if(::c.isInitialized){withContext(Dispatchers.Swing){c.close()};withTimeout(12_000){c.scope.coroutineContext[Job]?.join()}}
        if(::server.isInitialized)server.shutdown()
        if(::root.isInitialized)Files.walk(root).use {it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)}
    }
    @Test fun realtimeOutageDoesNotPauseHttpMessagesAndReconnectDoesNotResend()=runBlocking<Unit>{
        awaitState {socket!=null}
        ticketStatus=503
        socket!!.close(1012,"test restart")
        awaitState {!c.realtimeConnected}
        withContext(Dispatchers.Swing){total=72;c.syncVisibleConversations()}
        awaitState {c.messages[key]?.lastOrNull()?.id=="m71"}
        withContext(Dispatchers.Swing){assertEquals("online",c.connectionHealth);ticketStatus=200;c.checkConnection()}
        awaitState {c.realtimeConnected&&!c.connectionChecking}
        assertEquals(0,promptSubmits.get())
    }
    @Test fun mobileTaskShowsRunningThenClearsWithoutReplayingSavedQueue()=runBlocking<Unit>{
        withContext(Dispatchers.Swing){total=71;agentRunning=true;c.syncVisibleConversations()}
        awaitState {c.runs[key]?.record?.observed==true}
        withContext(Dispatchers.Swing){
            assertTrue(c.runs[key]!!.status.contains("处理"))
            c.queued[key]=listOf(QueuedMessage(session=c.currentSession!!,prompt="需要手动继续"))
            agentRunning=false;total=72;c.reconcileRuns(force=true)
        }
        awaitState {c.runs[key]==null&&c.messages[key]?.lastOrNull()?.id=="m71"}
        withContext(Dispatchers.Swing){assertEquals(1,c.queued[key]!!.size);assertFalse(key in c.unread);assertEquals(72,c.readMessageCounts[key])}
        assertEquals(0,promptSubmits.get())
    }
    @Test fun visibleUnreadCursorWaitsForBodyAndFailedReadRemainsUnreadOnLeaving()=runBlocking<Unit>{
        withContext(Dispatchers.Swing){
            blockMessages=true;messageStatus=503;total=72
            c.reconcileUnreadSessions(listOf(c.currentSession!!.copy(messageCount=72)),emptyMap())
            assertEquals(70,c.readMessageCounts[key]);assertFalse(key in c.unread)
        }
        assertTrue(withContext(Dispatchers.IO){reading.await(5,TimeUnit.SECONDS)})
        release.countDown();awaitState {c.messageLoading[key]!=true&&c.messageErrors[key]!=null}
        withContext(Dispatchers.Swing){
            assertEquals(70,c.readMessageCounts[key]);c.onWindowFocusChanged(false)
            c.reconcileUnreadSessions(listOf(c.currentSession!!.copy(messageCount=72)),emptyMap())
            assertTrue(key in c.unread);messageStatus=200;c.onWindowFocusChanged(true)
        }
        awaitState {c.readMessageCounts[key]==72&&key !in c.unread}
    }
    @Test fun knownRemoteCompletionEventRefreshesTheBodyWithoutWaitingForTheTimer()=runBlocking<Unit>{
        awaitState {c.currentSession?.runtimeId=="runtime-daily"}
        total=72
        socket!!.send("""{"method":"event","params":{"type":"message.complete","session_id":"runtime-daily","payload":{"text":"已记录。"}}}""")
        withTimeout(2500){while(!withContext(Dispatchers.Swing){c.messages[key]?.lastOrNull()?.id=="m71"})delay(10)}
        assertEquals(0,promptSubmits.get())
    }
    @Test fun foregroundTimerReceivesMobileTurnWithoutReopeningTheConversation()=runBlocking<Unit>{
        total=72
        awaitState {c.messages[key]?.lastOrNull()?.id=="m71"}
        withContext(Dispatchers.Swing){assertEquals(mobileUser,c.messages[key]!!.first {it.id=="m70"}.content);assertEquals(72,c.currentSession!!.messageCount)}
    }
    @Test fun focusReturnSynchronizesAndKeepsLoadedHistoryDraftAndAttachment()=runBlocking<Unit>{
        val attachment=PendingAttachment(name="draft.txt",mimeType="text/plain",textContent="not sent")
        withContext(Dispatchers.Swing){c.loadOlder(c.currentSession!!)}
        awaitState {c.messages[key]?.firstOrNull()?.id=="m0"&&c.messageLoading[key]!=true}
        withContext(Dispatchers.Swing){c.setDraft(key,"还没写完");c.attachments[key]=listOf(attachment);c.onWindowFocusChanged(false);total=72;c.onWindowFocusChanged(true)}
        awaitState {c.messages[key]?.lastOrNull()?.id=="m71"}
        withContext(Dispatchers.Swing){assertEquals("m0",c.messages[key]!!.first().id);assertEquals(72,c.messages[key]!!.size);assertEquals("还没写完",c.drafts[key]);assertEquals(listOf(attachment),c.attachments[key])}
    }
    @Test fun manualRefreshUpdatesOpenMessagesEvenWhenTheSessionListCountHasNotChanged()=runBlocking<Unit>{
        withContext(Dispatchers.Swing){total=72;c.refresh()}
        awaitState {c.messages[key]?.lastOrNull()?.id=="m71"}
    }
    @Test fun failedSyncRetainsMessagesAndNextSuccessfulReadRecovers()=runBlocking<Unit>{
        val before=withContext(Dispatchers.Swing){c.messages[key]!!.toList()}
        withContext(Dispatchers.Swing){messageStatus=503;c.syncVisibleConversations()}
        awaitState {c.messageErrors[key]!=null&&c.messageLoading[key]!=true}
        withContext(Dispatchers.Swing){assertEquals(before,c.messages[key]);messageStatus=200;total=72;c.syncVisibleConversations()}
        awaitState {c.messages[key]?.lastOrNull()?.id=="m71"}
        withContext(Dispatchers.Swing){assertNull(c.messageErrors[key])}
    }
    @Test fun aReadAlreadyInFlightCannotReplaceALocallyStreamingTurn()=runBlocking<Unit>{
        withContext(Dispatchers.Swing){blockMessages=true;c.syncVisibleConversations()}
        assertTrue(withContext(Dispatchers.IO){reading.await(5,TimeUnit.SECONDS)})
        val local=ChatMessage(id="local-answer",role=MessageRole.ASSISTANT,content="正在生成",isStreaming=true)
        withContext(Dispatchers.Swing){
            c.messages[key]=c.messages[key].orEmpty()+local
            c.runs[key]=DesktopRun(c.currentSession!!,StreamController(),local.id)
        }
        release.countDown();awaitState {c.messageLoading[key]!=true}
        withContext(Dispatchers.Swing){assertEquals(local,c.messages[key]!!.last());val reads=messageReads.get();c.syncVisibleConversations();assertEquals(reads,messageReads.get())}
    }
    @Test fun restartDefaultsToWorkbenchAndKeepsThePreviousConversationAndDraft()=runBlocking<Unit>{
        withContext(Dispatchers.Swing){
            c.setDraft(key,"下次接着写");c.flushCheckpoint();val store=c.store;c.close()
            store.put("today:deep","true") // Old mobile-style preference must not restore another desktop home mode.
            c=DesktopController(false,store,autoConnect=false);c.connect(server.url("/").toString(),"test","test-password")
        }
        awaitState {c.connected&&!c.busy&&!c.sessionsLoading}
        withContext(Dispatchers.Swing){assertEquals(Page.HOME,c.page);assertFalse(c.today.attentionOpen);assertEquals("daily",c.currentSession?.id);assertEquals("下次接着写",c.drafts[key])}
    }
    @Test fun notificationClickBeforeConnectionWaitsThenOpensItsConversation()=runBlocking<Unit>{
        withContext(Dispatchers.Swing){
            val target=c.notificationTarget(c.currentSession!!);val store=c.store;c.close()
            c=DesktopController(false,store,autoConnect=false);c.openNotification(target)
            assertEquals(Page.HOME,c.page)
            c.connect(server.url("/").toString(),"test","test-password")
        }
        awaitState {c.connected&&!c.busy&&c.page==Page.CHAT}
        withContext(Dispatchers.Swing){assertEquals("daily",c.currentSession?.id)}
    }
    private suspend fun awaitState(predicate:()->Boolean){withTimeout(12_000){while(!withContext(Dispatchers.Swing){predicate()})delay(10)}}
    private fun json(body:String,status:Int=200)=MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(body)
}
