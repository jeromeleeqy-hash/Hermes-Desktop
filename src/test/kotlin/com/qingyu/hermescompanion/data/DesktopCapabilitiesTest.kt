package com.qingyu.hermescompanion.data

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class DesktopCapabilitiesTest {
    @Test fun unknownAndUnsupportedModelsRemainDistinct() {
        assertNull(advertisedReasoningOptions(JSONObject()))
        assertEquals(emptyList<String>(),advertisedReasoningOptions(JSONObject("""{"supports_reasoning":false}""")))
    }
    @Test fun declaredLevelsAreFilteredAndOffIsNotInvented() {
        assertEquals(listOf("low","high"),advertisedReasoningOptions(JSONObject("""{"reasoning_efforts":["low","high","none","high","bogus"],"thinking_off":false}""")))
    }
    @Test fun modelMetadataIsScopedByProviderAndModel() {
        val result=parseModelCatalog(JSONObject("""{"providers":[{"slug":"a","models":[{"id":"same","reasoning_efforts":["low"]}]},{"slug":"b","models":[{"id":"same","reasoning_efforts":["high"]}]}]}"""))
        assertEquals(listOf("low"),result.providers[0].reasoningOptions["same"])
        assertEquals(listOf("high"),result.providers[1].reasoningOptions["same"])
    }
    @Test fun dashboardCapabilityMapAdvertisesStandardReasoningLevels() {
        val result=parseModelCatalog(JSONObject("""{
            "providers":[{"slug":"openrouter","models":["deepseek/deepseek-v4-pro"],
            "capabilities":{"deepseek/deepseek-v4-pro":{"reasoning":true,"can_disable_reasoning":true}}}]
        }"""))
        assertEquals(listOf("none","minimal","low","medium","high","xhigh","max","ultra"),
            result.providers.single().reasoningOptions["deepseek/deepseek-v4-pro"])
    }
    @Test fun mandatoryDashboardReasoningOmitsOffButKeepsLevels() {
        val result=parseModelCatalog(JSONObject("""{
            "providers":[{"slug":"openrouter","models":["model"],
            "capabilities":{"model":{"reasoning":true,"can_disable_reasoning":false}}}]
        }"""))
        assertEquals(listOf("minimal","low","medium","high","xhigh","max","ultra"),
            result.providers.single().reasoningOptions["model"])
    }
    @Test fun authorizationLinksRejectExecutableSchemesAndCredentials() {
        listOf("file:///etc/passwd","javascript:alert(1)","https://user:secret@example.com/","http://example.com/login","https://example.com/\n").forEach {assertNull(safeActionUrl(it))}
        assertEquals("https://example.com/auth?state=1",safeActionUrl("https://example.com/auth?state=1"))
        assertEquals("http://127.0.0.1:9119/login",safeActionUrl("http://127.0.0.1:9119/login"))
    }
}
