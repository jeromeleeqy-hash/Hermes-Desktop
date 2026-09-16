package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.ConnectionConfig
import com.qingyu.hermescompanion.storage.SecureCookieJar
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.mock

class SessionPaginationTest {
    private lateinit var server:MockWebServer
    private lateinit var client:HermesApiClient
    @Before fun setup(){server=MockWebServer();server.start();client=HermesApiClient(ConnectionConfig(server.url("/").toString(),"test"),mock(SecureCookieJar::class.java))}
    @After fun close(){client.close();server.shutdown()}
    private fun page(start:Int,count:Int,total:Int?=null):String=JSONObject().put("sessions",JSONArray().apply {repeat(count){i->put(JSONObject().put("id","s${start+i}").put("title","Session ${start+i}"))}}).apply {total?.let {put("total",it)}}.toString()

    @Test fun clampsTheRequestToTheGatewayLimit(){
        server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse {
            return if(request.requestUrl!!.queryParameter("limit")!!.toInt()>100)MockResponse().setResponseCode(422).setBody("{\"detail\":[{\"msg\":\"Input should be less than or equal to 100\"}]}")else MockResponse().setBody(page(0,1,1))
        }}
        assertEquals(1,client.listSessions(200).sessions.size)
        assertEquals("100",server.takeRequest().requestUrl!!.queryParameter("limit"))
    }
    @Test fun readsBeyondOneHundredWhenTheGatewayOmitsTotals(){
        server.enqueue(MockResponse().setBody(page(0,100)));server.enqueue(MockResponse().setBody(page(100,25)))
        assertEquals(125,client.listAllSessions().size)
        assertEquals("0",server.takeRequest().requestUrl!!.queryParameter("offset"))
        assertEquals("100",server.takeRequest().requestUrl!!.queryParameter("offset"))
    }
    @Test fun honorsAnExplicitTotalEvenWhenTheServerReturnsShortPages(){
        server.enqueue(MockResponse().setBody(page(0,2,3)));server.enqueue(MockResponse().setBody(page(2,1,3)))
        assertEquals(3,client.listAllSessions().size)
        server.takeRequest();assertEquals("2",server.takeRequest().requestUrl!!.queryParameter("offset"))
    }
    @Test fun overlappingPagesAreDeduplicatedWithoutSkippingOffsets(){
        server.enqueue(MockResponse().setBody(page(0,100,150)));server.enqueue(MockResponse().setBody(page(99,51,150)))
        assertEquals(150,client.listAllSessions().size)
    }
    @Test fun repeatedPagesFailRatherThanLoopForeverOrPretendTheListIsComplete(){
        repeat(2){server.enqueue(MockResponse().setBody(page(0,100)))}
        assertThrows(ApiException::class.java){client.listAllSessions()}
        assertEquals(2,server.requestCount)
    }
    @Test fun malformedResponsesAreNotEmptyWorkspaces(){
        server.enqueue(MockResponse().setBody("{\"status\":\"unexpected\"}"))
        assertThrows(ApiException::class.java){client.listSessions()}
    }
    @Test fun archivedConversationsAlsoReadBeyondTheFirstPage(){
        server.enqueue(MockResponse().setBody(page(0,100)));server.enqueue(MockResponse().setBody(page(100,1)))
        assertEquals(101,client.listArchivedSessions().size)
        repeat(2){assertEquals("only",server.takeRequest().requestUrl!!.queryParameter("archived"))}
    }
    @Test fun emptyPageEndsTheListEvenWhenTheTotalIncludesOtherSessions(){
        server.enqueue(MockResponse().setBody(page(0,0,10)))
        assertTrue(client.listAllSessions().isEmpty())
        assertEquals(1,server.requestCount)
    }
    @Test fun staleTotalsDoNotDiscardSuccessfullyReadConversations(){
        server.enqueue(MockResponse().setBody(page(0,37,143)))
        server.enqueue(MockResponse().setBody(page(37,0,143)))
        assertEquals(37,client.listAllSessions().size)
        server.takeRequest();assertEquals("37",server.takeRequest().requestUrl!!.queryParameter("offset"))
    }
    @Test fun aFullPageContinuesEvenWhenTheTotalIsUnderreported(){
        server.enqueue(MockResponse().setBody(page(0,100,1)))
        server.enqueue(MockResponse().setBody(page(100,25,1)))
        assertEquals(125,client.listAllSessions().size)
    }
    @Test fun anExactFullPageCanBeFollowedByAnEmptyPage(){
        server.enqueue(MockResponse().setBody(page(0,100,100)))
        server.enqueue(MockResponse().setBody(page(100,0,100)))
        assertEquals(100,client.listAllSessions().size)
        assertEquals(2,server.requestCount)
    }
    @Test fun successfulPagesArePublishedBeforeALaterHttpFailureForBothLists(){
        for(archived in listOf(false,true)) {
            val snapshots=mutableListOf<List<String>>()
            server.enqueue(MockResponse().setBody(page(0,12,40)))
            server.enqueue(MockResponse().setResponseCode(503).setBody("{\"detail\":\"maintenance\"}"))
            val e=assertThrows(ApiException::class.java){
                val collect:(List<com.qingyu.hermescompanion.model.HermesSession>)->Unit={sessions->snapshots+=sessions.map {it.id}}
                if(archived)client.listArchivedSessions(collect) else client.listAllSessions(onProgress=collect)
            }
            assertEquals(503,e.statusCode)
            assertEquals(listOf((0 until 12).map {"s$it"}),snapshots)
        }
    }
    @Test fun aRejectedFirstRequestDoesNotPublishAnEmptyList(){
        var called=false
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"detail\":\"Unauthorized\"}"))
        val e=assertThrows(ApiException::class.java){client.listAllSessions {called=true}}
        assertEquals(401,e.statusCode);assertFalse(called)
    }
    @Test fun progressSnapshotsAreStableAndDeduplicated(){
        val snapshots=mutableListOf<List<com.qingyu.hermescompanion.model.HermesSession>>()
        server.enqueue(MockResponse().setBody(page(0,100,150)))
        server.enqueue(MockResponse().setBody(page(99,51,150)))
        assertEquals(150,client.listAllSessions {snapshots+=it}.size)
        assertEquals(listOf(100,150),snapshots.map {it.size})
        assertEquals(snapshots.last().size,snapshots.last().map {it.id}.distinct().size)
    }
    @Test fun validationArraysAreHumanReadable(){
        server.enqueue(MockResponse().setResponseCode(422).setBody("{\"detail\":[{\"msg\":\"Input should be less than or equal to 100\",\"loc\":[\"query\",\"limit\"],\"input\":\"200\"}]}"))
        val e=assertThrows(ApiException::class.java){client.listSessions()}
        assertEquals("Input should be less than or equal to 100",e.message)
        assertFalse(e.message.contains("\"loc\""))
    }
}
