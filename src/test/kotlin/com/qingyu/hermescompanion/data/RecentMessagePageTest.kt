package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureCookieJar
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.mock

class RecentMessagePageTest {
    private val server=MockWebServer()
    private lateinit var client:HermesApiClient
    @Before fun setup(){server.start();client=HermesApiClient(ConnectionConfig(server.url("/").toString(),"test"),mock(SecureCookieJar::class.java))}
    @After fun close(){client.close();server.shutdown()}
    @Test fun staleSidebarCountCannotHideLatestMobileTurnAndBothReadsUseTheSessionProfile(){
        server.enqueue(MockResponse().setBody("""{"session":{"id":"daily","message_count":202}}"""))
        server.enqueue(MockResponse().setBody(JSONObject().put("messages",JSONArray().apply {
            (143..202).forEach {put(JSONObject().put("id","m$it").put("role",if(it%2==1)"user" else "assistant").put("content",if(it==201)"记个账" else "消息 $it"))}
        }).toString()))
        val page=client.loadRecentMessagePage(HermesSession("daily","日常助理",messageCount=20,profile="mobile-profile"))
        assertEquals(142,page.offset);assertEquals(202,page.totalCount)
        assertEquals("记个账",page.messages[58].content);assertEquals("m202",page.messages.last().id)
        val metadata=server.takeRequest().requestUrl!!;val messages=server.takeRequest().requestUrl!!
        assertEquals("/api/sessions/daily",metadata.encodedPath)
        assertEquals("142",messages.queryParameter("offset"))
        assertEquals("mobile-profile",metadata.queryParameter("profile"));assertEquals("mobile-profile",messages.queryParameter("profile"))
    }
    @Test fun malformedMessageResponseIsNotAnEmptyConversation(){
        server.enqueue(MockResponse().setBody("""{"message_count":2}"""))
        server.enqueue(MockResponse().setBody("""{"status":"temporarily_unavailable"}"""))
        assertThrows(ApiException::class.java){client.loadRecentMessagePage(HermesSession("daily","日常助理"))}
    }
}
