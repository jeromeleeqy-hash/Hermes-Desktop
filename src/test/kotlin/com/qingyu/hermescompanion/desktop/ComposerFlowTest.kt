@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.platform.DesktopHost
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import com.qingyu.hermescompanion.data.HermesApiClient
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.mock
import java.nio.file.*
import java.util.concurrent.CopyOnWriteArrayList

/** Pointer/keyboard -> actual composer -> controller -> WebSocket -> rendered reply. */
class ComposerFlowTest {
    private lateinit var dir:Path
    private lateinit var server:MockWebServer
    private lateinit var client:HermesApiClient
    private lateinit var c:DesktopController
    private lateinit var scene:ImageComposeScene
    @Volatile private var socket:WebSocket?=null
    @Volatile private var activeModel="old-model"
    @Volatile private var activeProvider="provider"
    @Volatile private var retainModel=false
    @Volatile private var rejectPrompts=false
    @Volatile private var holdModel=false
    @Volatile private var rejectInterrupt=false
    @Volatile private var heldModel:JSONObject?=null
    @Volatile private var modelCatalogResponse="""{"providers":[{"slug":"provider","name":"Provider","models":["old-model","new-model"]}]}"""
    @Volatile private var modelCatalogStatus=200
    @Volatile private var rejectImages=true
    private val calls=CopyOnWriteArrayList<JSONObject>()
    private val session=HermesSession("a","发送测试",model="old-model",provider="provider")
    private var previousLanguage="zh"

    @Before fun setup()=runBlocking<Unit>(Dispatchers.Swing) {
        System.setProperty("java.awt.headless","true")
        System.setProperty("skiko.renderApi","SOFTWARE")
        previousLanguage=com.qingyu.hermescompanion.i18n.desktopLanguage
        dir=Files.createTempDirectory("hermes-composer-flow")
        server=MockWebServer()
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.requestUrl!!.encodedPath=="/api/auth/ws-ticket")return json("""{"ticket":"test"}""")
                if(request.getHeader("Upgrade")?.equals("websocket",true)!=true) {
                    return when(request.requestUrl!!.encodedPath) {
                        "/api/files/read"->if(rejectImages)json("{\"detail\":\"图片已过期\"}").setResponseCode(404)else {
                            val output=java.io.ByteArrayOutputStream()
                            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",output)
                            json(JSONObject().put("data_url","data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(output.toByteArray())).toString())
                        }
                        "/api/model/options"->json(modelCatalogResponse).setResponseCode(modelCatalogStatus)
                        "/api/sessions/a"->json("""{"id":"a","title":"发送测试"}""")
                        "/api/sessions"->json("""{"sessions":[],"total":0}""")
                        else->json("{}")
                    }
                }
                return MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                    override fun onOpen(webSocket:WebSocket,response:Response){socket=webSocket;event(null,"gateway.ready")}
                    override fun onClosing(webSocket:WebSocket,code:Int,reason:String){webSocket.close(code,reason)}
                    override fun onMessage(webSocket:WebSocket,text:String) {
                        val frame=JSONObject(text);calls+=frame
                        val method=frame.getString("method")
                        if(method=="slash.exec"&&holdModel){heldModel=frame;return}
                        val result=when {
                            method=="prompt.submit"&&rejectPrompts->JSONObject().put("ok",false).put("message","模型暂不可用")
                            method=="session.interrupt"&&rejectInterrupt->JSONObject().put("ok",false).put("message","暂时无法停止")
                            method=="session.resume"->JSONObject().put("session_id","runtime-a").put("info",JSONObject().put("model",activeModel).put("provider",activeProvider))
                            else->JSONObject().put("ok",true)
                        }
                        reply(frame,result)
                        if(method=="prompt.submit"&&!rejectPrompts)event("runtime-a","message.start")
                    }
                })
            }
        }
        server.start()
        client=HermesApiClient(ConnectionConfig(server.url("/").toString().trimEnd('/'),"test"),mock(SecureCookieJar::class.java))
        c=DesktopController(false,SecureConfigStore(dir){ByteArray(32){23}},autoConnect=false)
        c.changeLanguage("zh")
        @Suppress("UNCHECKED_CAST")
        val clients=DesktopController::class.java.getDeclaredField("clients").apply {isAccessible=true}.get(c) as MutableMap<String,HermesApiClient>
        clients["default"]=client
        c.connected=true;c.notifications=false;c.reduceMotion=true;c.page=Page.CHAT;c.setFilesPanel(false)
        c.sessions=listOf(session);c.currentSession=session
        c.messages[session.scopedId]=listOf(ChatMessage(id="before",role=MessageRole.ASSISTANT,content="可以开始了。"))
        scene=ImageComposeScene(1280,800) {HermesTheme(c){DesktopBackdrop{DesktopWorkspace(c)}}}
        frames()
    }
    @After fun close()=runBlocking<Unit>(Dispatchers.Swing) {
        if(::scene.isInitialized)scene.close()
        if(::c.isInitialized)c.close()
        setDesktopLanguage(previousLanguage)
        if(::server.isInitialized)withContext(Dispatchers.IO){server.shutdown()}
        if(::dir.isInitialized)Files.walk(dir).use {it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)}
    }
    @Test fun clickingTheSendIconSubmitsOnceAndRendersTheReply()=runBlocking<Unit>(Dispatchers.Swing) {
        type("请整理计划");frames()
        val send=node("send-message")
        // Hit the icon side, not just the label. A second click must not repeat a prompt.
        click(Offset(send.boundsInRoot.right-14,send.boundsInRoot.center.y))
        click(Offset(send.boundsInRoot.right-14,send.boundsInRoot.center.y))
        await {prompts().size==1}
        assertTrue(c.runs.containsKey(session.scopedId))
        assertEquals("请整理计划",prompts().single().getJSONObject("params").getString("text"))
        event("runtime-a","message.complete","计划已整理完成。")
        await {!c.runs.containsKey(session.scopedId)}
        frames()
        assertTrue(nodes().any {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text.contains("计划已整理完成。")}==true})
        assertTrue(c.drafts[session.scopedId].orEmpty().isEmpty())
        assertEquals(1,prompts().size)
    }
    @Test fun rejectedSubmissionKeepsDraftAndAllowsRetry()=runBlocking<Unit>(Dispatchers.Swing) {
        rejectPrompts=true;type("保留这条内容");frames();click(node("send-message").boundsInRoot.center)
        await {c.sendErrors[session.scopedId]!=null&&!c.runs.containsKey(session.scopedId)}
        frames()
        assertEquals("保留这条内容",c.drafts[session.scopedId])
        assertEquals("模型暂不可用",c.sendErrors[session.scopedId])
        assertTrue(nodes().any {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text=="这次发送未完成"}==true})
        rejectPrompts=false;click(node("send-message").boundsInRoot.center)
        await {prompts().size==2}
        event("runtime-a","message.complete","重试成功")
        await {!c.runs.containsKey(session.scopedId)}
        assertFalse(c.sendErrors.containsKey(session.scopedId))
    }
    @Test fun enterModeKeepsShiftEnterAsNewline()=runBlocking<Unit>(Dispatchers.Swing) {
        c.setSendMode(true);type("第一行");frames();click(node("chat-input").boundsInRoot.center)
        press(shift=true);frames()
        assertTrue(c.drafts[session.scopedId].orEmpty().contains("\n"));assertTrue(prompts().isEmpty())
        press();await {prompts().size==1}
        event("runtime-a","message.complete","完成");await {!c.runs.containsKey(session.scopedId)}
    }
    @Test fun ctrlEnterModeKeepsEnterAsNewline()=runBlocking<Unit>(Dispatchers.Swing) {
        c.setSendMode(false);type("第一行");frames();click(node("chat-input").boundsInRoot.center)
        press();frames()
        assertTrue(c.drafts[session.scopedId].orEmpty().contains("\n"));assertTrue(prompts().isEmpty())
        press(ctrl=true);await {prompts().size==1}
        event("runtime-a","message.complete","完成");await {!c.runs.containsKey(session.scopedId)}
    }
    @Test fun imeConfirmationAndShiftModifiedShortcutsDoNotSubmit() {
        assertFalse(shouldSendOnKey(true,KeyEvent(Key.Enter,KeyEventType.KeyDown),true))
        assertFalse(shouldSendOnKey(false,KeyEvent(Key.Enter,KeyEventType.KeyDown,isCtrlPressed=true),true))
        assertFalse(shouldSendOnKey(false,KeyEvent(Key.Enter,KeyEventType.KeyDown,isCtrlPressed=true,isShiftPressed=true),false))
        assertFalse(shouldSendOnKey(true,KeyEvent(Key.Enter,KeyEventType.KeyUp),false))
    }
    @Test fun aDelayedModelChangeCannotNavigateBackToTheOldConversation()=runBlocking<Unit>(Dispatchers.Swing) {
        holdModel=true;c.selectModel(session,"other-provider","new-model")
        await {heldModel!=null}
        assertTrue(c.modelSwitching[session.scopedId]==true)
        type("等模型切换完成");c.submitDraft();assertTrue(prompts().isEmpty())
        val other=HermesSession("b","另一个会话",model="keep-model")
        c.currentSession=other;c.sessions=c.sessions+other
        reply(heldModel!!,JSONObject().put("ok",true))
        await {c.modelSwitching[session.scopedId]!=true}
        assertEquals(other,c.currentSession)
        assertEquals("new-model",c.sessions.first {it.id=="a"}.model)
        assertEquals("keep-model",c.sessions.first {it.id=="b"}.model)
        assertEquals("等模型切换完成",c.drafts[session.scopedId])
    }
    @Test fun modelSelectionUsesServerReportedModelInsteadOfRequestedModel()=runBlocking<Unit>(Dispatchers.Swing) {
        retainModel=true;c.selectModel(session,"provider","new-model")
        await {calls.any {it.optString("method")=="slash.exec"}&&c.modelSwitching[session.scopedId]!=true}
        assertEquals("old-model",c.currentSession?.model)
    }
    @Test fun queuedMessageWaitsForCompletionBeforeSubmitting()=runBlocking<Unit>(Dispatchers.Swing) {
        type("先做这件事");c.submitDraft();await {prompts().size==1}
        type("完成后做第二件事");c.runningSendMode="queue";c.submitDraft()
        assertEquals(1,c.queued[session.scopedId]?.size);assertEquals(1,prompts().size)
        event("runtime-a","message.complete","第一件已完成")
        await {prompts().size==2}
        assertEquals("完成后做第二件事",prompts().last().getJSONObject("params").getString("text"))
        assertTrue(c.queued[session.scopedId].orEmpty().isEmpty())
        event("runtime-a","message.complete","第二件已完成");await {!c.runs.containsKey(session.scopedId)}
    }
    @Test fun failedStopNeverSubmitsReplacementAndKeepsTheDraft()=runBlocking<Unit>(Dispatchers.Swing) {
        type("正在进行的事");c.submitDraft();await {prompts().size==1}
        rejectInterrupt=true;type("改做这件事");c.runningSendMode="interrupt";c.submitDraft()
        await {c.error!=null}
        assertEquals(1,prompts().size);assertEquals("改做这件事",c.drafts[session.scopedId])
        assertTrue(c.runs.containsKey(session.scopedId));assertFalse(c.runs.getValue(session.scopedId).stopping)
        rejectInterrupt=false;c.error=null;c.submitDraft();await {prompts().size==2}
        assertEquals("改做这件事",prompts().last().getJSONObject("params").getString("text"))
        event("runtime-a","message.complete","新任务完成");await {!c.runs.containsKey(session.scopedId)}
    }
    @Test fun sendSettingWorksWithoutServerSettingsAndSurvivesRestart()=runBlocking<Unit>(Dispatchers.Swing) {
        c.settings=null;c.page=Page.PROFILE;c.settingsSection="对话与记忆";frames()
        click(node("setting-send-enter").boundsInRoot.center);frames();assertTrue(c.sendOnEnter)
        val restored=DesktopController(false,SecureConfigStore(dir){ByteArray(32){23}},autoConnect=false)
        try {assertTrue(restored.sendOnEnter)}finally {restored.close()}
        click(node("setting-send-ctrl").boundsInRoot.center);frames();assertFalse(c.sendOnEnter)
    }
    @Test fun modelRestartGuardIsNonBlockingAndRetryRestoresSelection()=runBlocking<Unit>(Dispatchers.Swing) {
        modelCatalogStatus=503
        modelCatalogResponse=JSONObject().put("detail","Restart required: This process is running code from old but the checkout on disk is now new. The model picker would risk a stale-module crash — restart this Hermes process.").toString()
        type("保留我正在写的内容");c.loadModelCatalog(force=true)
        await {c.modelCatalogError!=null&&!c.modelCatalogLoading}
        assertNull(c.error);assertEquals("old-model",c.currentSession!!.model)
        assertEquals("保留我正在写的内容",c.drafts[session.scopedId]);assertTrue(c.modelCatalogError!!.restartRequired)
        c.selectModel(session,"provider","new-model");frames()
        assertTrue(calls.none {it.optString("method")=="slash.exec"})
        scene.close();scene=ImageComposeScene(380,540){HermesTheme(c){ModelPickerContent(c,session)}};frames()
        assertTrue(nodes().any {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text=="服务器需要完成重启"}==true})
        assertFalse(nodes().any {it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("model-option:")==true})
        modelCatalogStatus=200;modelCatalogResponse="""{"providers":[{"slug":"provider","models":["old-model","new-model"]}]}"""
        click(node("model-catalog-retry").boundsInRoot.center)
        await {c.modelCatalogError==null&&!c.modelCatalogLoading};frames()
        click(node("model-option:provider:new-model").boundsInRoot.center)
        await {c.currentSession?.model=="new-model"}
        assertEquals(1,calls.count {it.optString("method")=="slash.exec"})
        assertEquals("保留我正在写的内容",c.drafts[session.scopedId]);assertNull(c.error)
    }
    @Test fun malformedModelCatalogIsNeverAnEmptySuccess()=runBlocking<Unit>(Dispatchers.Swing) {
        modelCatalogResponse="{}";c.loadModelCatalog(force=true)
        await {c.modelCatalogError!=null&&!c.modelCatalogLoading}
        assertFalse(c.modelCatalogError!!.restartRequired);assertNull(c.error)
        assertEquals("old-model",c.currentSession!!.model)
        modelCatalogResponse="{\"providers\":[]}";c.loadModelCatalog(force=true)
        await {c.modelCatalogError==null&&!c.modelCatalogLoading}
        assertTrue(c.modelCatalog.providers.isEmpty())
    }
    @Test fun openingTheActualModelPopupAndSelectingAModelCompletes()=runBlocking<Unit>(Dispatchers.Swing) {
        c.loadModelCatalog(force=true);await {c.modelCatalogLoadedAt!=null&&!c.modelCatalogLoading};frames()
        click(node("composer-model").boundsInRoot.center);frames()
        assertTrue("Last model row must remain fully clickable",node("model-option:provider:new-model").boundsInRoot.height>=38f)
        click(node("model-option:provider:new-model").boundsInRoot.center)
        await {c.currentSession?.model=="new-model"};frames()
        assertFalse(nodes().any {it.config.getOrNull(SemanticsProperties.TestTag)=="model-option:provider:new-model"})
        assertEquals(1,calls.count {it.optString("method")=="slash.exec"})
    }
    @Test fun unavailableHistoricalImageStaysInlineAndCanBeRetried()=runBlocking<Unit>(Dispatchers.Swing) {
        scene.close();scene=ImageComposeScene(420,400){HermesTheme(c){ChatImageView(c,"/root/attachments/test.png","default")}}
        await {nodes().any {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text=="图片暂时无法显示"}==true}}
        assertNull(c.error);rejectImages=false
        click(nodes().first {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text=="重试"}==true}.boundsInRoot.center)
        await {nodes().any {it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("对话图片")==true}}
        assertNull(c.error)
    }
    @Test fun customCaptionButtonsDispatchIndependentActions()=runBlocking<Unit>(Dispatchers.Swing) {
        var minimized=0;var maximized=0;var closed=0
        scene.close();scene=ImageComposeScene(640,100){HermesTheme(c){DesktopWindowChrome(DesktopWindowControls(minimize={minimized++},toggleMaximize={maximized++},close={closed++}))}};frames()
        click(node("window-minimize").boundsInRoot.center)
        click(node("window-maximize").boundsInRoot.center)
        click(node("window-close").boundsInRoot.center);frames()
        assertEquals(1,minimized);assertEquals(1,maximized);assertEquals(1,closed)
        val drag=node("window-drag-area").boundsInRoot
        assertTrue(drag.right<=node("window-minimize").boundsInRoot.left)
    }
    private fun json(value:String)=MockResponse().setHeader("Content-Type","application/json").setBody(value)
    private fun reply(frame:JSONObject,result:JSONObject) {
        if(frame.optString("method")=="slash.exec"&&result.optBoolean("ok")&&!retainModel) {
            val command=frame.getJSONObject("params").getString("command").split(" ")
            activeModel=command[1];activeProvider=command[command.indexOf("--provider")+1]
        }
        socket!!.send(JSONObject().put("jsonrpc","2.0").put("id",frame.get("id")).put("result",result).toString())}
    private fun event(runtime:String?,type:String,text:String="") {
        val params=JSONObject().put("type",type).put("payload",JSONObject().put("text",text))
        if(runtime!=null)params.put("session_id",runtime)
        socket!!.send(JSONObject().put("method","event").put("params",params).toString())
    }
    @Test fun quickQuestionGoesToItsChosenSessionWhileMainWindowStaysElsewhere()=runBlocking<Unit>(Dispatchers.Swing) {
        val other=HermesSession("b","主窗口保留")
        c.sessions=c.sessions+other;c.currentSession=other;c.page=Page.FILES
        c.messages[session.scopedId]=listOf(ChatMessage(role=MessageRole.ASSISTANT,content="准备好了"))
        c.companion.select(session)
        c.setDraft(session.scopedId,"来自桌面悬浮球的问题")
        c.companion.send()
        await {prompts().size==1}
        assertEquals(other,c.currentSession);assertEquals(Page.FILES,c.page)
        assertTrue(c.runs.containsKey(session.scopedId));assertFalse(c.runs.containsKey(other.scopedId))
        assertEquals("来自桌面悬浮球的问题",prompts().single().getJSONObject("params").getString("text"))
        c.companion.close()
        assertTrue(c.runs.containsKey(session.scopedId))
        event("runtime-a","message.complete","独立小窗的回复")
        await {!c.runs.containsKey(session.scopedId)}
        assertEquals(other,c.currentSession)
        assertTrue(c.messages[session.scopedId].orEmpty().any {it.content.contains("独立小窗的回复")})
    }
    private fun prompts()=calls.filter {it.optString("method")=="prompt.submit"}
    private fun nodes():List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        return scene.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
    }
    private fun node(tag:String)=nodes().first {it.config.getOrNull(SemanticsProperties.TestTag)==tag}
    private fun type(text:String) {assertTrue(node("chat-input").config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(text)))}
    private fun click(point:Offset) {
        scene.sendPointerEvent(PointerEventType.Move,point)
        scene.sendPointerEvent(PointerEventType.Press,point,button=PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release,point,button=PointerButton.Primary)
    }
    private fun press(ctrl:Boolean=false,shift:Boolean=false) {
        scene.sendKeyEvent(KeyEvent(Key.Enter,KeyEventType.KeyDown,isCtrlPressed=ctrl&&!DesktopHost.isMac,isMetaPressed=ctrl&&DesktopHost.isMac,isShiftPressed=shift))
        scene.sendKeyEvent(KeyEvent(Key.Enter,KeyEventType.KeyUp,isCtrlPressed=ctrl&&!DesktopHost.isMac,isMetaPressed=ctrl&&DesktopHost.isMac,isShiftPressed=shift))
    }
    private suspend fun frames(){repeat(8){scene.render(System.nanoTime()).close();delay(20)}}
    private suspend fun await(condition:()->Boolean){withTimeout(10_000){while(!condition()){scene.render(System.nanoTime()).close();delay(20)}}}
}
