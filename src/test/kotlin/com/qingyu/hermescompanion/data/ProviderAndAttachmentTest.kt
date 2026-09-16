package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.nio.file.*
import java.util.Base64
import java.util.concurrent.TimeUnit

class ProviderAndAttachmentTest {
    private lateinit var root:Path
    private lateinit var server:MockWebServer
    private lateinit var client:HermesApiClient
    @Before fun setup() {
        root=Files.createTempDirectory("hermes-provider-tests")
        server=MockWebServer().apply {start()}
        client=HermesApiClient(ConnectionConfig(server.url("/prefix").toString(),"test"),SecureCookieJar(SecureConfigStore(root){ByteArray(32){19}}))
        client.setProfile("work")
    }
    @After fun close(){client.close();server.shutdown();root.toFile().deleteRecursively()}
    private fun json(value:String,status:Int=200)=MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(value)
    private fun take()=server.takeRequest(2,TimeUnit.SECONDS)!!
    private fun provider()=CustomProviderConfiguration("office","Office API","https://api.example.test/v1","model-a",listOf("model-a","model-b"),hasKey=true)

    @Test fun credentialMetadataNeverRetainsValuesAndDoesNotIncludeMessagingKeys() {
        server.enqueue(json("""{"DEEPSEEK_API_KEY":{"is_set":true,"redacted_value":"fake-secret","category":"provider","provider":"deepseek","provider_label":"DeepSeek","is_password":true},"TELEGRAM_BOT_TOKEN":{"is_set":true,"is_password":true,"channel_managed":true},"CUSTOM_ENDPOINT_URL":{"category":"provider","is_password":false}}"""))
        val result=client.providerCredentials()
        assertEquals(1,result.size);assertEquals("DeepSeek",result.single().name);assertTrue(result.single().configured)
        assertFalse(result.toString().contains("fake-secret"));assertEquals("work",take().requestUrl!!.queryParameter("profile"))
    }
    @Test fun nativeEditOmitsBlankSecretAndStaysInSelectedProfile() {
        server.enqueue(json("{\"ok\":true}"));client.saveCustomProvider(provider(),"",false,true)
        val request=take();val body=JSONObject(request.body.readUtf8())
        assertEquals("POST",request.method);assertEquals("/prefix/api/providers/custom-endpoints",request.requestUrl!!.encodedPath)
        assertEquals("work",request.requestUrl!!.queryParameter("profile"));assertEquals("work",body.getString("profile"))
        assertFalse(body.has("api_key"));assertFalse(body.getBoolean("make_default"));assertEquals(2,body.getJSONArray("models").length())
    }
    @Test fun nativeSaveIncludesOnlyExplicitlyEnteredNewSecret() {
        server.enqueue(json("{\"ok\":true}"));client.saveCustomProvider(provider(),"fake-key-for-test",true,true)
        val body=JSONObject(take().body.readUtf8());assertEquals("fake-key-for-test",body.getString("api_key"));assertTrue(body.getBoolean("make_default"))
    }
    @Test fun negativeAcknowledgementsDoNotReportSuccessfulWrites() {
        repeat(3){server.enqueue(json("{\"ok\":false,\"message\":\"保存失败\"}"))}
        assertThrows(ApiException::class.java){client.saveProviderCredential("DEEPSEEK_API_KEY","fake-key")}
        assertThrows(ApiException::class.java){client.saveCustomProvider(provider(),"",false,true)}
        assertThrows(ApiException::class.java){client.uploadAssistantFile("/work/test.pdf","data:application/pdf;base64,JVBERg==","work")}
    }
    @Test fun unsupportedNativeCatalogFallsBackButAuthenticationFailureDoesNot() {
        server.enqueue(json("{}",404));server.enqueue(json("""{"config":{"providers":{"office":{"api":"https://api.example.test/v1","default_model":"a","models":{"b":{"context_length":32000}},"key_env":"OFFICE_KEY"}}}}"""))
        val result=client.customProviders();assertFalse(result.nativeApi);assertEquals(listOf("a","b"),result.items.single().models)
        take();take()
        server.enqueue(json("{\"detail\":\"login required\"}",401))
        assertEquals(401,assertThrows(ApiException::class.java){client.customProviders()}.statusCode)
        assertEquals(3,server.requestCount)
    }
    @Test fun legacyEditPreservesOtherProvidersCredentialsAndModelMetadata() {
        server.enqueue(json("""{"config":{"memory":{"enabled":true},"providers":{"other":{"api":"http://local/v1"},"office":{"api":"https://old.example/v1","key_env":"EXISTING_KEY","extra_headers":{"x-note":"keep"},"transport":"anthropic_messages","models":{"model-a":{"context_length":64000}}}}}}"""))
        server.enqueue(json("{}"))
        client.saveCustomProvider(provider(),"",false,false);take()
        val config=JSONObject(take().body.readUtf8()).getJSONObject("config")
        val p=config.getJSONObject("providers").getJSONObject("office")
        assertTrue(config.getJSONObject("memory").getBoolean("enabled"));assertTrue(config.getJSONObject("providers").has("other"))
        assertEquals("EXISTING_KEY",p.getString("key_env"));assertEquals("keep",p.getJSONObject("extra_headers").getString("x-note"))
        assertEquals("anthropic_messages",p.getString("transport"));assertEquals(64000,p.getJSONObject("models").getJSONObject("model-a").getInt("context_length"))
    }
    @Test fun builtinCredentialUpdateIsScopedAndSecretIsRedactedOnError() {
        server.enqueue(json("{\"detail\":\"invalid fake-key-only\"}",400))
        val error=assertThrows(ApiException::class.java){client.saveProviderCredential("DEEPSEEK_API_KEY","fake-key-only")}
        assertFalse(error.message.orEmpty().contains("fake-key-only"))
        val request=take();assertEquals("work",request.requestUrl!!.queryParameter("profile"));assertEquals("DEEPSEEK_API_KEY",JSONObject(request.body.readUtf8()).getString("key"))
    }
    @Test fun invalidProviderAddressIsRejectedBeforeNetwork() {
        listOf("file:///etc/a","https://name:password@example.test/v1","https://api.example/v1?token=abc").forEach {url->
            assertThrows(IllegalArgumentException::class.java){client.saveCustomProvider(provider().copy(baseUrl=url),"",false,true)}
        }
        assertEquals(0,server.requestCount)
    }
    @Test fun binaryAttachmentUsesOriginalBytesAndExplicitSessionDirectory() {
        val data="data:application/pdf;base64,"+Base64.getEncoder().encodeToString("%PDF-test bytes".toByteArray())
        val attachment=PendingAttachment(id="stable-test-id",name="报告.pdf",mimeType="application/pdf",uploadDataUrl=data)
        val session=HermesSession("a","A",profile="work",workspacePath="/projects/work")
        server.enqueue(json("{}"));val ready=client.prepareChatFiles(session,listOf(attachment)).single()
        val request=take();val body=JSONObject(request.body.readUtf8())
        assertTrue(body.getString("path").startsWith("/projects/work/Hermes-Attachment-"));assertTrue(body.getString("path").endsWith("报告.pdf"))
        assertEquals(data,body.getString("data_url"));assertFalse(body.getBoolean("overwrite"));assertEquals("work",body.getString("profile"))
        assertNull(ready.uploadDataUrl);assertEquals(body.getString("path"),ready.remotePath)
        assertEquals(data,attachment.uploadDataUrl)
    }
    @Test fun retryReconcilesMatchingUploadWithoutOverwritingAnything() {
        val data="data:application/octet-stream;base64,YWJj"
        val attachment=PendingAttachment(id="a",name="file.bin",mimeType="application/octet-stream",uploadDataUrl=data)
        val session=HermesSession("a","A",profile="work",workspacePath="/work")
        server.enqueue(json("{}",409));server.enqueue(json("""{"data_url":"$data","name":"file.bin","mime_type":"application/octet-stream"}"""))
        val result=client.prepareChatFiles(session,listOf(attachment));assertNotNull(result.single().remotePath)
        val upload=JSONObject(take().body.readUtf8());val read=take()
        assertFalse(upload.getBoolean("overwrite"));assertEquals(upload.getString("path"),read.requestUrl!!.queryParameter("path"))
        assertEquals("work",read.requestUrl!!.queryParameter("profile"))
    }
    @Test fun differentExistingBytesFailAndKeepTheLocalAttachment() {
        val a=PendingAttachment(name="file.bin",mimeType="application/octet-stream",uploadDataUrl="data:application/octet-stream;base64,YWJj")
        server.enqueue(json("{}",409));server.enqueue(json("{\"data_url\":\"data:application/octet-stream;base64,eHl6\"}"))
        assertThrows(ApiException::class.java){client.prepareChatFiles(HermesSession("a","A",workspacePath="/work"),listOf(a))}
        assertNotNull(a.uploadDataUrl);assertNull(a.remotePath)
    }
    @Test fun slowHtmlAssetHasBoundedRequestTime() {
        server.enqueue(json("{\"data_url\":\"data:text/css;base64,YQ==\"}").setBodyDelay(3,TimeUnit.SECONDS))
        val start=System.nanoTime()
        assertThrows(java.io.IOException::class.java){client.readWorkspaceDocumentForProfile("/work/slow.css","work",150)}
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1500)
    }
}
