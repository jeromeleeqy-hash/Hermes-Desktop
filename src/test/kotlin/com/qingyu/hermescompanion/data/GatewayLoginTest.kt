package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.ConnectionConfig
import com.qingyu.hermescompanion.storage.SecureConfigStore
import com.qingyu.hermescompanion.storage.SecureCookieJar
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.TimeUnit

/** Real HTTP and encrypted cookie storage; no live gateway or real credentials. */
class GatewayLoginTest {
    private lateinit var root: Path
    private lateinit var server: MockWebServer
    private lateinit var client: HermesApiClient
    private lateinit var store: SecureConfigStore

    @Before fun setUp() {
        root=Files.createTempDirectory("hermes-login-test")
        store=SecureConfigStore(root){ByteArray(32){17}}
        server=MockWebServer().apply { start() }
        client=newClient()
    }
    private fun newClient()=HermesApiClient(
        ConnectionConfig(server.url("/prefix").toString(),"admin"),SecureCookieJar(store))
    @After fun close() {
        client.close();server.shutdown()
        Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
    private fun json(body: String, status: Int=200)=MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(body)
    private fun bootstrap() {
        server.enqueue(json("""{"auth_required":true,"auth_providers":["basic"]}"""))
        server.enqueue(json("""{"providers":[{"name":"oauth","supports_password":false},{"name":"basic","supports_password":true}]}"""))
    }

    @Test fun passwordIsSentExactlyAndSessionSurvivesClientRestart() {
        val password="  A+&=\"\\中文 café\uD83D\uDD10  "
        bootstrap()
        server.enqueue(json("""{"ok":true,"next":"/"}""")
            .addHeader("Set-Cookie","hermes_session=fake-session-for-test; Path=/prefix; HttpOnly")
            .addHeader("Set-Cookie","hermes_session_provider=basic; Path=/prefix; HttpOnly"))
        server.enqueue(json("""{"user_id":"admin","display_name":"Jerome"}"""))
        assertEquals("Jerome",client.login("admin",password))
        assertEquals("/prefix/api/status",server.takeRequest(2,TimeUnit.SECONDS)!!.path)
        assertEquals("/prefix/api/auth/providers",server.takeRequest(2,TimeUnit.SECONDS)!!.path)
        val login=server.takeRequest(2,TimeUnit.SECONDS)!!
        assertEquals("POST",login.method)
        assertEquals("/prefix/auth/password-login",login.path)
        assertTrue(login.getHeader("Content-Type")!!.startsWith("application/json"))
        val body=JSONObject(login.body.readUtf8())
        assertEquals(setOf("provider","username","password","next"),body.keySet())
        assertEquals(password,body.getString("password"))
        assertEquals("admin",body.getString("username"))
        assertEquals("basic",body.getString("provider"))
        val me=server.takeRequest(2,TimeUnit.SECONDS)!!
        assertEquals("/prefix/api/auth/me",me.path)
        assertTrue(me.getHeader("Cookie")!!.contains("hermes_session=fake-session-for-test"))
        assertFalse(Files.readString(root.resolve("state.enc"),Charsets.ISO_8859_1).contains(password))
        client.close();client=newClient()
        server.enqueue(json("""{"auth_required":true,"auth_providers":["basic"]}"""))
        server.enqueue(json("""{"user_id":"admin"}"""))
        assertEquals("admin",client.checkSavedSession())
        server.takeRequest(2,TimeUnit.SECONDS)
        assertTrue(server.takeRequest(2,TimeUnit.SECONDS)!!.getHeader("Cookie")!!.contains("fake-session-for-test"))
    }

    @Test fun emptyInputIsRejectedBeforeAnyNetworkRequest() {
        for ((user,password) in listOf("admin" to "", " " to "secret", "admin" to "  ")) {
            val failure=assertThrows(GatewayLoginException::class.java){client.login(user,password)}
            assertEquals(GatewayLoginStage.INPUT,failure.stage)
        }
        assertEquals(0,server.requestCount)
    }

    @Test fun rejectedCredentialsAreNotRetriedOrMisreportedAsCookieFailure() {
        bootstrap()
        server.enqueue(json("""{"detail":"Invalid credentials"}""",401))
        val failure=assertThrows(GatewayLoginException::class.java){client.login("admin","test-password")}
        assertEquals(GatewayLoginStage.PASSWORD,failure.stage)
        assertEquals(401,failure.statusCode)
        assertEquals(GatewayLoginReason.CREDENTIALS,failure.reason)
        assertEquals(3,server.requestCount)
        assertFalse(failure.diagnostic().contains("test-password"))
    }

    @Test fun successfulPasswordPostWithRejectedSessionHasDifferentFailure() {
        bootstrap()
        server.enqueue(json("""{"ok":true}"""))
        server.enqueue(json("""{"detail":"Unauthorized"}""",401))
        val failure=assertThrows(GatewayLoginException::class.java){client.login("admin","password")}
        assertEquals(GatewayLoginStage.VERIFY_SESSION,failure.stage)
        assertEquals(GatewayLoginReason.SESSION,failure.reason)
        assertEquals(4,server.requestCount)
    }

    @Test fun savedSessionFailureDoesNotClaimPasswordWasRejected() {
        server.enqueue(json("""{"auth_required":true,"auth_providers":["basic"]}"""))
        server.enqueue(json("""{"detail":"Unauthorized"}""",401))
        val failure=assertThrows(GatewayLoginException::class.java){client.checkSavedSession()}
        assertEquals(GatewayLoginStage.RESTORE_SESSION,failure.stage)
        assertEquals(GatewayLoginReason.SESSION,failure.reason)
    }

    @Test fun rateLimitDoesNotTriggerAnotherCredentialAttempt() {
        bootstrap();server.enqueue(json("""{"detail":"Try later"}""",429))
        val failure=assertThrows(GatewayLoginException::class.java){client.login("admin","password")}
        assertEquals(GatewayLoginReason.RATE_LIMIT,failure.reason)
        assertEquals(3,server.requestCount)
    }

    @Test fun malformedProviderResponseDoesNotSubmitPassword() {
        server.enqueue(json("""{"auth_required":true,"auth_providers":["basic"]}"""))
        server.enqueue(json("<html>Proxy response</html>"))
        val failure=assertThrows(GatewayLoginException::class.java){client.login("admin","password")}
        assertEquals(GatewayLoginStage.PROVIDERS,failure.stage)
        assertEquals(GatewayLoginReason.RESPONSE,failure.reason)
        assertEquals(2,server.requestCount)
    }

    @Test fun serverErrorBodyCannotLeakSecretsIntoUserDiagnostic() {
        bootstrap();server.enqueue(json("""{"detail":"echoed-secret-password token=fake-cookie"}""",500))
        val failure=assertThrows(GatewayLoginException::class.java){client.login("admin","echoed-secret-password")}
        assertFalse(failure.message.orEmpty().contains("echoed-secret"))
        assertFalse(failure.diagnostic().contains("echoed-secret"))
        assertFalse(failure.diagnostic().contains("fake-cookie"))
        assertEquals(500,failure.statusCode)
    }

    @Test fun storageFailureDoesNotBecomeInvalidCredentials() {
        val failure=assertThrows(GatewayLoginException::class.java){
            gatewayLoginStep(GatewayLoginStage.LOCAL_SESSION){throw java.io.IOException("private path")}
        }
        assertEquals(GatewayLoginReason.STORAGE,failure.reason)
        assertFalse(failure.diagnostic().contains("private path"))
    }
}
