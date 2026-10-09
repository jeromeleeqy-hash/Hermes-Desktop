package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureCookieJar
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.mock
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class Desktop200GatewayTest {
    private val server=MockWebServer()
    private val pool=Executors.newCachedThreadPool()
    private val calls=CopyOnWriteArrayList<JSONObject>()
    private val opened=CountDownLatch(1)
    private val count=AtomicInteger()
    private lateinit var client:HermesApiClient
    private lateinit var socket:WebSocket
    @Volatile private var autoReady=true
    @Volatile private var snapshot=false
    @Volatile private var lazyInfo=true
    @Before fun setup(){
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse{
            if(request.path!!.startsWith("/api/auth/ws-ticket"))return MockResponse().setBody("{\"ticket\":\"ticket\"}")
            if(request.path!!.startsWith("/api/sessions"))return MockResponse().setResponseCode(404)
            return MockResponse().withWebSocketUpgrade(object:WebSocketListener(){
                override fun onOpen(ws:WebSocket,r:Response){socket=ws;opened.countDown();if(autoReady)ready()}
                override fun onClosing(ws:WebSocket,code:Int,reason:String){ws.close(code,reason)}
                override fun onMessage(ws:WebSocket,text:String){
                    val f=JSONObject(text);calls+=f;val p=f.optJSONObject("params")?:JSONObject()
                    val result=when(f.optString("method")){
                        "session.create"->JSONObject().put("session_id","fresh").put("stored_session_id","stored")
                        "session.resume"->JSONObject().put("session_id","resumed-${count.incrementAndGet()}").put("running",false)
                            .put("info",JSONObject().put("lazy",lazyInfo).put("model","old").put("provider","old-provider"))
                        "session.events.since"->JSONObject().apply{if(snapshot)put("open_requests",JSONArray())}
                        else->JSONObject().put("ok",true)
                    }
                    ws.send(JSONObject().put("jsonrpc","2.0").put("id",f.get("id")).put("result",result).toString())
                    if(f.optString("method")=="prompt.submit"){
                        ws.send(JSONObject().put("method","event").put("params",JSONObject().put("session_id",p.getString("session_id")).put("type","message.start").put("payload",JSONObject())).toString())
                        ws.send(JSONObject().put("method","event").put("params",JSONObject().put("session_id",p.getString("session_id")).put("type","message.complete").put("payload",JSONObject().put("text","done"))).toString())
                    }
                }
            })
        }}
        server.start();client=HermesApiClient(ConnectionConfig(server.url("/").toString().trimEnd('/'),"user"),mock(SecureCookieJar::class.java))
    }
    private fun ready(){socket.send("{\"method\":\"event\",\"params\":{\"type\":\"gateway.ready\",\"payload\":{}}}")}
    @After fun close(){client.close();pool.shutdownNow();server.shutdown()}
    @Test(timeout=20000) fun concurrentCommandsWaitForGatewayReadyAndCapabilities(){
        autoReady=false
        val a=pool.submit<HermesSession>{client.createSession(null)}
        assertTrue(opened.await(5,TimeUnit.SECONDS))
        val b=pool.submit<HermesSession>{client.createSession(null)}
        Thread.sleep(150);assertTrue(calls.isEmpty());assertFalse(a.isDone);assertFalse(b.isDone)
        ready();a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS)
        assertEquals("client.capabilities",calls.first().getString("method"))
    }
    @Test(timeout=20000) fun firstPromptOnLazySessionSkipsHistoryAndNextTurnResumesStoredId(){
        val s=client.createSession(null)
        assertTrue(client.loadLatestMessages(s).isEmpty())
        val events=mutableListOf<StreamEvent>()
        client.streamMessage(StreamController(),s,"first",emptyList(),events::add)
        client.streamMessage(StreamController(),s,"second",emptyList(),events::add)
        assertEquals(listOf("fresh","resumed-1"),calls.filter{it.optString("method")=="prompt.submit"}.map {it.getJSONObject("params").getString("session_id")})
        assertEquals(1,calls.count {it.optString("method")=="session.resume"})
        assertEquals(2,events.count {it==StreamEvent.PromptAccepted})
    }
    @Test(timeout=20000) fun staleRuntimeIsNeverTrustedForNewTurn(){
        client.streamMessage(StreamController(),HermesSession("stored","Title",runtimeId="stale-runtime"),"hello",emptyList()){}
        assertEquals("stored",calls.first {it.optString("method")=="session.resume"}.getJSONObject("params").getString("session_id"))
        assertEquals("resumed-1",calls.first {it.optString("method")=="prompt.submit"}.getJSONObject("params").getString("session_id"))
    }
    @Test(timeout=20000) fun idleDoesNotMeanNoPendingRequestsAndExplicitFallbackDoes(){
        val s=HermesSession("stored","Title")
        assertFalse(client.resumeSession(s).requestSnapshotKnown)
        assertFalse(client.resumeSession(s,inspectRequests=true).requestSnapshotKnown)
        snapshot=true
        assertTrue(client.resumeSession(s,inspectRequests=true).requestSnapshotKnown)
    }
    @Test(timeout=20000) fun acknowledgedModelChoiceSurvivesLazyDefaultMetadata(){
        val s=client.createSession(null)
        val changed=client.switchSessionModel(s,"custom","new-model")
        assertEquals("new-model",changed.model)
        val resumed=client.resumeSession(changed).session
        assertEquals("new-model",resumed.model);assertEquals("custom",resumed.provider)
        lazyInfo=false
        assertEquals("old",client.resumeSession(changed).session.model)
    }
    @Test(timeout=20000) fun recurringForegroundChecksDoNotDisableHeartbeatExpiry(){
        var now=0L;val h=GatewayHeartbeat {now};h.start()
        repeat(11){now+=5000;h.setForeground(true);h.tick()}
        now=60000;h.setForeground(true);assertEquals(GatewayHeartbeat.Tick.EXPIRED,h.tick())
    }
    @Test(timeout=20000) fun providerInferenceRejectsAmbiguousModel(){
        val catalog=ModelCatalog(providers=listOf(ModelProvider("a","A",listOf("same")),ModelProvider("b","B",listOf("same"))))
        assertNull(catalog.providerFor(HermesSession("s","s",model="same")))
        assertEquals("b",catalog.providerFor(HermesSession("s","s",model="same",provider="b"))!!.slug)
    }
}
