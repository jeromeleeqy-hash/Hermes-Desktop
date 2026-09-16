package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.data.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.*

/** Real controller, encrypted voice notes, HTTP transcription and websocket submission;
 * only the physical microphone is replaced with a deterministic WAV source. */
class HoldToTalkTest {
    private lateinit var dir:Path
    private lateinit var c:DesktopController
    private lateinit var server:MockWebServer
    private var sample:((VoiceInputSample)->Unit)?=null
    private val starts=AtomicInteger()
    private val stops=AtomicInteger()
    private val transcriptions=AtomicInteger()
    private val requests=CopyOnWriteArrayList<JSONObject>()
    @Volatile private var socket:WebSocket?=null
    @Volatile private var transcript="帮我整理今天的计划"
    @Volatile private var lookupGate:CountDownLatch?=null
    @Volatile private var lookupEntered=false
    @Volatile private var transcriptionGate:CountDownLatch?=null
    private val daily=HermesSession("a","日常助理")
    private val main=HermesSession("b","主窗口会话")

    @Before fun setup()=runBlocking<Unit>(Dispatchers.Swing) {
        dir=Files.createTempDirectory("hermes-hold-to-talk")
        server=MockWebServer()
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(r:RecordedRequest):MockResponse {
                if(r.getHeader("Upgrade")?.equals("websocket",true)==true)return MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(ws:WebSocket,response:Response){socket=ws;event("gateway.ready")}
                    override fun onClosing(ws:WebSocket,code:Int,reason:String){ws.close(code,reason)}
                    override fun onMessage(ws:WebSocket,text:String) {
                        val frame=JSONObject(text);requests+=frame
                        val result=if(frame.optString("method")=="session.resume")JSONObject().put("session_id","runtime-a")else JSONObject().put("ok",true)
                        ws.send(JSONObject().put("jsonrpc","2.0").put("id",frame.get("id")).put("result",result).toString())
                        if(frame.optString("method")=="prompt.submit")event("message.start")
                    }
                })
                return when(val path=r.requestUrl!!.encodedPath) {
                    "/api/audio/transcribe"->{transcriptions.incrementAndGet();transcriptionGate?.await(8,TimeUnit.SECONDS);json(JSONObject().put("ok",true).put("transcript",transcript).put("provider","test").toString())}
                    "/api/auth/ws-ticket"->json("""{"ticket":"test"}""")
                    "/api/sessions"->{lookupEntered=true;lookupGate?.await(8,TimeUnit.SECONDS);json("""{"sessions":[{"id":"a","title":"日常助理"}],"total":1}""")}
                    "/api/sessions/a"->json("""{"id":"a","title":"日常助理"}""")
                    else->if(path.endsWith("/messages"))json("""{"messages":[]}""")else json("{}")
                }
            }
        }
        server.start()
        val audio=mock(DesktopAudio::class.java)
        doAnswer { invocation->sample=invocation.getArgument(0);starts.incrementAndGet();null }.`when`(audio).start(ArgumentMatchers.any<((VoiceInputSample)->Unit)>()?:{})
        doAnswer {
            stops.incrementAndGet()
            val file=Files.createTempFile(dir,"recording-",".wav").toFile()
            AudioInputStream(ByteArrayInputStream(ByteArray(6400)),AudioFormat(16000f,16,1,true,false),3200).use {AudioSystem.write(it,AudioFileFormat.Type.WAVE,file)}
            file
        }.`when`(audio).stop()
        c=DesktopController(false,SecureConfigStore(dir){ByteArray(32){31}},autoConnect=false,voiceAudio=audio)
        val api=HermesApiClient(ConnectionConfig(server.url("/").toString(),"test"),mock(SecureCookieJar::class.java))
        @Suppress("UNCHECKED_CAST")
        val clients=DesktopController::class.java.getDeclaredField("clients").apply {isAccessible=true}.get(c) as MutableMap<String,HermesApiClient>
        clients["default"]=api
        c.connected=true;c.notifications=false;c.currentSession=main;c.sessions=listOf(daily,main);c.page=Page.FILES
        c.voicePreferences=c.voicePreferences.copy(engine="server",autoSend=false,autoRead=false,continuous=true,fastReply=false)
        c.messages[daily.scopedId]=listOf(ChatMessage(role=MessageRole.ASSISTANT,content="准备好了"))
    }
    @After fun cleanup()=runBlocking<Unit>(Dispatchers.Swing) {
        lookupGate?.countDown();transcriptionGate?.countDown()
        if(::c.isInitialized)c.close()
        if(::server.isInitialized)withContext(Dispatchers.IO){server.shutdown()}
        if(::dir.isInitialized)dir.toFile().deleteRecursively()
    }
    private suspend fun ready(){c.companion.open();await {c.companion.session!=null&&!c.companion.preparing}}
    private suspend fun await(condition:()->Boolean){withTimeout(10_000){while(!condition())delay(20)}}
    private fun prompts()=requests.filter {it.optString("method")=="prompt.submit"}
    private fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
    private fun event(type:String,text:String="") {
        val params=JSONObject().put("type",type).put("payload",JSONObject().put("text",text))
        if(type!="gateway.ready")params.put("session_id","runtime-a")
        socket!!.send(JSONObject().put("method","event").put("params",params).toString())
    }

    @Test fun releaseStopsAndSendsExactlyOnceToTheChosenSessionWithoutRestarting()=runBlocking<Unit>(Dispatchers.Swing) {
        ready();c.companion.beginHoldToTalk()
        assertEquals(VoicePhase.LISTENING,c.voice.phase);assertTrue(c.voice.holdToTalk)
        // A pause that ends an ordinary continuous turn must not end a mouse hold.
        suspend fun frames(count:Int,db:Float){repeat(count){sample!!(VoiceInputSample(db,.2f));delay(70)}}
        frames(8,-70f);frames(15,-15f);frames(22,-70f)
        assertEquals(VoicePhase.LISTENING,c.voice.phase);assertEquals(0,stops.get());assertTrue(prompts().isEmpty())
        c.companion.releaseHoldToTalk();c.companion.releaseHoldToTalk()
        assertEquals(1,stops.get());assertEquals(VoicePhase.TRANSCRIBING,c.voice.phase)
        await {prompts().size==1}
        assertEquals(transcript,prompts().single().getJSONObject("params").getString("text"))
        assertEquals(main,c.currentSession);assertEquals(Page.FILES,c.page)
        assertTrue(c.runs.containsKey(daily.scopedId));assertFalse(c.runs.containsKey(main.scopedId))
        assertEquals(1,transcriptions.get());assertTrue(c.voiceNotes.single().committed)
        event("message.complete","计划已整理")
        await {!c.runs.containsKey(daily.scopedId)&&c.voice.phase==VoicePhase.IDLE}
        delay(100);assertEquals(1,starts.get());assertEquals(1,prompts().size)
    }
    @Test fun aBoundedRecordingStillWaitsForReleaseWhenRecognitionFinishesFirst()=runBlocking<Unit>(Dispatchers.Swing) {
        ready();c.companion.beginHoldToTalk()
        c.voice.stopCapture() // Same completion path as the recording duration limit.
        await {c.voice.phase==VoicePhase.IDLE&&c.voiceNotes.firstOrNull()?.committed==true}
        assertTrue(prompts().isEmpty());assertTrue(c.companion.holdingToTalk)
        c.companion.releaseHoldToTalk();c.companion.releaseHoldToTalk()
        await {prompts().size==1}
        assertEquals(1,stops.get());assertEquals(1,transcriptions.get())
    }
    @Test fun autoReadingAHeldReplyDoesNotRestartTheMicrophone()=runBlocking<Unit>(Dispatchers.Swing) {
        ready();c.companion.beginHoldToTalk();c.companion.releaseHoldToTalk();await {prompts().size==1}
        c.voicePreferences=c.voicePreferences.copy(engine="system",autoRead=true,continuous=true)
        event("message.complete","这是一条会朗读的回复")
        await {!c.runs.containsKey(daily.scopedId)&&c.voice.phase==VoicePhase.IDLE}
        assertTrue(c.voice.message.contains("朗读结束"));assertEquals(1,starts.get())
    }
    @Test fun releaseDuringSessionPreparationNeverStartsALateMicrophone()=runBlocking<Unit>(Dispatchers.Swing) {
        lookupGate=CountDownLatch(1)
        c.companion.beginHoldToTalk();await {lookupEntered}
        assertTrue(c.companion.preparing)
        c.companion.releaseHoldToTalk();lookupGate!!.countDown()
        await {!c.companion.preparing&&c.companion.session!=null}
        assertEquals(0,starts.get());assertEquals(0,stops.get());assertFalse(c.voice.active);assertTrue(prompts().isEmpty())
        c.companion.beginHoldToTalk();assertEquals(1,starts.get());assertEquals(VoicePhase.LISTENING,c.voice.phase)
    }
    @Test fun repeatedHoldDuringRecognitionDoesNotCancelOrDuplicateTheFirstTurn()=runBlocking<Unit>(Dispatchers.Swing) {
        ready();transcriptionGate=CountDownLatch(1)
        c.companion.beginHoldToTalk();c.companion.releaseHoldToTalk();await {transcriptions.get()==1}
        c.companion.beginHoldToTalk();c.companion.releaseHoldToTalk()
        assertEquals(1,starts.get());assertEquals(1,stops.get())
        transcriptionGate!!.countDown();await {prompts().size==1}
        assertEquals(1,transcriptions.get());assertEquals(1,c.voiceNotes.size)
    }
    @Test fun cancelledHoldPreservesTextWithoutSendingAndRegularRecordingStillWorks()=runBlocking<Unit>(Dispatchers.Swing) {
        ready();c.companion.beginHoldToTalk();c.companion.cancelHoldToTalk();c.companion.releaseHoldToTalk()
        await {c.voice.phase==VoicePhase.IDLE&&c.voiceNotes.firstOrNull()?.committed==true}
        assertEquals(transcript,c.drafts[daily.scopedId]);assertTrue(prompts().isEmpty())
        c.companion.close();assertFalse(c.voice.holdToTalk)
        c.voice.startCapture(settingsTest=true)
        assertEquals(2,starts.get());assertEquals(VoicePhase.LISTENING,c.voice.phase)
    }
    @Test fun changingConversationsCannotRedirectTheHeldRecording()=runBlocking<Unit>(Dispatchers.Swing) {
        ready();c.companion.beginHoldToTalk();c.companion.select(main);c.companion.releaseHoldToTalk()
        assertEquals(daily.scopedId,c.voiceNotes.single().session?.scopedId)
        assertTrue(c.store.readBlob(c.voiceNotes.single().blob).size>3200)
        assertTrue(c.drafts[main.scopedId].isNullOrEmpty());assertTrue(prompts().isEmpty())
        assertEquals(1,stops.get());assertFalse(c.voice.active)
    }
    @Test fun emptyRecognitionKeepsTheRecordingAndRetryOnlyRestoresTheDraft()=runBlocking<Unit>(Dispatchers.Swing) {
        ready();transcript=""
        c.companion.beginHoldToTalk();c.companion.releaseHoldToTalk()
        await {c.voice.phase==VoicePhase.ERROR}
        assertTrue(prompts().isEmpty());assertFalse(c.voiceNotes.single().committed)
        assertTrue(c.store.readBlob(c.voiceNotes.single().blob).size>3200)
        transcript="重试后的内容";c.voice.retry(c.voiceNotes.single())
        await {c.voiceNotes.single().committed}
        assertEquals(transcript,c.drafts[daily.scopedId]);assertTrue(prompts().isEmpty());assertEquals(2,transcriptions.get())
    }
    @Test fun modelSwitchPreservesTheRecognizedDraftInsteadOfLosingTheQuestion()=runBlocking<Unit>(Dispatchers.Swing) {
        ready();c.modelSwitching[daily.scopedId]=true
        c.companion.beginHoldToTalk();c.companion.releaseHoldToTalk()
        await {c.voice.phase==VoicePhase.IDLE&&c.voiceNotes.firstOrNull()?.committed==true}
        assertEquals(transcript,c.drafts[daily.scopedId]);assertTrue(prompts().isEmpty());assertTrue(c.voice.message.contains("模型正在切换"))
    }
    @Test fun anOlderVoiceReplyCannotStopANewHoldAndBusySessionsQueueTheNextQuestion()=runBlocking<Unit>(Dispatchers.Swing) {
        ready();c.companion.beginHoldToTalk();c.companion.releaseHoldToTalk();await {prompts().size==1}
        c.companion.beginHoldToTalk();assertEquals(VoicePhase.LISTENING,c.voice.phase)
        c.voice.onReply(daily.scopedId,"前一条回复")
        c.voice.onRunFailure(daily.scopedId,"前一条任务结束")
        assertEquals(VoicePhase.LISTENING,c.voice.phase);assertEquals(1,stops.get())
        transcript="第二条语音";c.companion.releaseHoldToTalk()
        await {c.queued[daily.scopedId]?.size==1}
        assertEquals(1,prompts().size);assertEquals("第二条语音",c.queued.getValue(daily.scopedId).single().prompt)
        event("message.complete","第一条已完成")
        await {prompts().size==2}
        assertEquals("第二条语音",prompts().last().getJSONObject("params").getString("text"))
        assertEquals(2,starts.get());assertEquals(2,stops.get())
    }
}
