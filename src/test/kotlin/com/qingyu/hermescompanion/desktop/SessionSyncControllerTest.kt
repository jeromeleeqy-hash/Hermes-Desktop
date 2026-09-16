package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises real HTTP -> controller -> Compose state, including an in-flight second page. */
class SessionSyncControllerTest {
    private lateinit var root:Path
    private lateinit var server:MockWebServer
    private lateinit var c:DesktopController
    private val releaseSecondPage=CountDownLatch(1)
    @Volatile private var serveRetry=false
    @Volatile private var authStatus=200
    @Volatile private var secondPageFails=true

    @Before fun setup()=runBlocking<Unit> {
        root=Files.createTempDirectory("hermes-session-sync")
        val store=SecureConfigStore(root){ByteArray(32){17}}
        server=MockWebServer()
        server.dispatcher=object:Dispatcher(){
            override fun dispatch(request:RecordedRequest):MockResponse {
                val url=request.requestUrl!!
                return when(url.encodedPath) {
                    "/api/status"->json("""{"auth_required":true,"auth_providers":["basic"]}""")
                    "/api/auth/providers"->json("""{"providers":[{"name":"basic","supports_password":true}]}""")
                    "/auth/password-login"->{authStatus=200;json("""{"ok":true}""").addHeader("Set-Cookie","hermes_session=test-only; Path=/; HttpOnly")}
                    "/api/auth/me"->json("""{"user_id":"test"}""",authStatus)
                    "/api/profiles"->json("""{"profiles":[{"name":"default"},{"name":"other"}]}""")
                    "/api/projects"->json("""{"projects":[]}""")
                    "/api/sessions"->when {
                        url.queryParameter("profile")=="other"->page(listOf("other-session"),1)
                        serveRetry->page(listOf("fresh-session"),1)
                        url.queryParameter("offset")=="0"->page(listOf("first","second"),19)
                        else->{
                            if(!releaseSecondPage.await(10,TimeUnit.SECONDS))return json("""{"detail":"test wait expired"}""",500)
                            if(secondPageFails)json("""{"detail":"maintenance"}""",503)else page(emptyList(),19)
                        }
                    }
                    else->json("{}")
                }
            }
        }
        server.start()
        withContext(Dispatchers.Swing) {
            c=DesktopController(false,store,autoConnect=false)
            c.connect(server.url("/").toString(),"test","test-password")
        }
        awaitState { c.sessions.map {it.id}==listOf("first","second") }
    }

    @After fun close()=runBlocking<Unit> {
        releaseSecondPage.countDown()
        if(::c.isInitialized)withContext(Dispatchers.Swing){c.close()}
        if(::server.isInitialized)server.shutdown()
        if(::root.isInitialized)Files.walk(root).use {it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)}
    }

    @Test fun showsTheFirstPageWhileLoadingAndKeepsItOnFailureThenRetries()=runBlocking<Unit> {
        withContext(Dispatchers.Swing) {
            assertTrue(c.connected)
            assertTrue(c.sessionsLoading)
            assertNull(c.sessionsSyncedAt)
            c.setDraft("default::first","保留我的草稿")
        }
        releaseSecondPage.countDown()
        awaitState {!c.sessionsLoading}
        withContext(Dispatchers.Swing) {
            assertEquals(listOf("first","second"),c.sessions.map {it.id})
            assertEquals("maintenance",c.sessionsLoadError)
            assertNull(c.sessionsSyncedAt)
            serveRetry=true
            c.refresh()
        }
        awaitState {!c.sessionsLoading}
        withContext(Dispatchers.Swing) {
            assertEquals(listOf("fresh-session"),c.sessions.map {it.id})
            assertNull(c.sessionsLoadError)
            assertNotNull(c.sessionsSyncedAt)
            assertEquals("保留我的草稿",c.drafts["default::first"])
        }
    }

    @Test fun emptyFinalPageWithStaleTotalCompletesWithoutShowingASyncError()=runBlocking<Unit> {
        secondPageFails=false
        releaseSecondPage.countDown()
        awaitState {!c.sessionsLoading}
        withContext(Dispatchers.Swing) {
            assertEquals(2,c.sessions.size)
            assertNull(c.sessionsLoadError)
            assertNotNull(c.sessionsSyncedAt)
        }
    }

    @Test fun offlineAndExpiredLoginKeepWorkspaceDraftsAndAttachments()=runBlocking<Unit> {
        releaseSecondPage.countDown();awaitState {!c.sessionsLoading}
        val key="default::first"
        val file=com.qingyu.hermescompanion.model.PendingAttachment(name="notes.txt",mimeType="text/plain",textContent="Keep me")
        withContext(Dispatchers.Swing){c.setDraft(key,"Do not resend");c.attachments[key]=listOf(file);authStatus=503;c.checkConnection()}
        awaitState {c.connectionHealth=="offline"&&!c.connectionChecking}
        withContext(Dispatchers.Swing){assertTrue(c.connected);assertEquals("Do not resend",c.drafts[key]);assertEquals(listOf(file),c.attachments[key]);authStatus=401;c.checkConnection()}
        awaitState {c.connectionHealth=="auth"&&!c.connectionChecking}
        withContext(Dispatchers.Swing){c.reauthenticate("test-password")}
        awaitState {c.connectionHealth=="online"&&!c.connectionChecking}
        withContext(Dispatchers.Swing){assertEquals("Do not resend",c.drafts[key]);assertEquals(listOf(file),c.attachments[key]);assertTrue(c.runs.isEmpty())}
    }

    private suspend fun awaitState(predicate:()->Boolean) {
        withTimeout(10_000) {while(!withContext(Dispatchers.Swing){predicate()})delay(10)}
    }
    private fun json(body:String,status:Int=200)=MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(body)
    private fun page(ids:List<String>,total:Int)=json(JSONObject().put("sessions",JSONArray().apply {
        ids.forEach {put(JSONObject().put("id",it).put("title",it))}
    }).put("total",total).toString())
}
