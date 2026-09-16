package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.data.HermesApiClient
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureCookieJar
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.mock

class ReplyStyleTest {
    @Test fun dropdownKeepsServerDefinitionsAndUnknownExistingSelections() {
        val raw="""{"personalities":{"reviewer":"Review"},"agent":{"personalities":{"concise":"Custom concise","coach":"Coach"}}}"""
        val options=replyStyleOptions(raw,"原有的自定义内容")
        assertEquals(options.size,options.map {it.value}.distinct().size)
        assertEquals("服务器自定义风格",options.first {it.value=="concise"}.detail)
        assertTrue(options.any {it.value=="reviewer"});assertTrue(options.any {it.value=="coach"})
        assertEquals("原有的自定义内容",options.last().value)
        assertEquals("简洁直接",options.first {it.value=="concise"}.label)
    }

    @Test fun selectedStyleUsesCanonicalFieldAndPreservesUnrelatedServerConfig() {
        val server=MockWebServer();server.start()
        val config=JSONObject("""{"model":{"provider":"test","default":"model"},"agent":{"personality":"teacher","system_prompt":"Keep this prompt","personalities":{"coach":"Keep this definition"}},"display":{"personality":"concise","skin":"custom"},"timezone":"Asia/Shanghai"}""")
        server.enqueue(MockResponse().setBody(JSONObject().put("config",config).toString()))
        server.enqueue(MockResponse().setBody(JSONObject().put("config",config).toString()))
        server.enqueue(MockResponse().setBody("{}"))
        val client=HermesApiClient(ConnectionConfig(server.url("/").toString(),"test"),mock(SecureCookieJar::class.java))
        try {
            assertEquals("concise",client.serverSettings().conversation.personality)
            val saved=client.saveConversationStyle(ConversationStyleSettings("technical","Asia/Shanghai",false))
            assertEquals("technical",saved.conversation.personality)
            server.takeRequest();server.takeRequest()
            val request=server.takeRequest();assertEquals("PUT",request.method)
            val sent=JSONObject(request.body.readUtf8()).getJSONObject("config")
            assertEquals("technical",sent.getJSONObject("display").getString("personality"))
            assertEquals("technical",sent.getJSONObject("agent").getString("personality"))
            assertEquals("Keep this prompt",sent.getJSONObject("agent").getString("system_prompt"))
            assertEquals("Keep this definition",sent.getJSONObject("agent").getJSONObject("personalities").getString("coach"))
            assertEquals("custom",sent.getJSONObject("display").getString("skin"))
        } finally {client.close();server.shutdown()}
    }

    @Test fun explicitDefaultWinsOverAnOldAgentSelection() {
        val server=MockWebServer();server.start()
        val client=HermesApiClient(ConnectionConfig(server.url("/").toString(),"test"),mock(SecureCookieJar::class.java))
        try {
            for(value in listOf("none","")) {
                val config=JSONObject("""{"model":{"provider":"test","default":"model"},"agent":{"personality":"teacher"},"display":{}}""")
                config.getJSONObject("display").put("personality",value)
                server.enqueue(MockResponse().setBody(JSONObject().put("config",config).toString()))
                assertEquals(value,client.serverSettings().conversation.personality)
            }
        } finally {client.close();server.shutdown()}
    }
}
