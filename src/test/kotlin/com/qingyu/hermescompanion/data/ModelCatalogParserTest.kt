package com.qingyu.hermescompanion.data

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class ModelCatalogParserTest {
    @Test fun readsNestedCatalogAndMixedModelIdsWithoutDisplayingRawJson() {
        val result=parseModelCatalog(JSONObject("""{"data":{"current":{"provider":"a","model":"one"},"providers":[{"slug":"a","name":"Alpha","models":[" one ",{"id":"two"},{"model":"three"},"one",{},null,14]}]}}"""))
        assertEquals("a",result.currentProvider);assertEquals("one",result.currentModel)
        assertEquals(listOf("one","two","three"),result.providers.single().models)
    }
    @Test fun excludesExplicitlyUnauthenticatedProviders() {
        val result=parseModelCatalog(JSONObject("""{"providers":[{"slug":"a","authenticated":false,"models":["one"]},{"id":"b","models":["two"]}]}"""))
        assertEquals(listOf("b"),result.providers.map {it.slug})
    }
    @Test fun recognizesAnExplicitlyEmptyCatalog() {
        assertTrue(parseModelCatalog(JSONObject("""{"providers":[]} """)).providers.isEmpty())
    }
    @Test fun rejectsMissingProvidersRatherThanPretendingNoModelsExist() {
        val e=assertThrows(ApiException::class.java){parseModelCatalog(JSONObject("""{"current_model":"existing"}"""))}
        assertTrue(e.message!!.contains("格式不完整"))
    }
    @Test fun preservesRestartGuardFromAnErrorEnvelope() {
        val e=assertThrows(ApiException::class.java){parseModelCatalog(JSONObject("""{"ok":false,"message":"Restart required: stale-module crash","providers":[]}"""))}
        assertTrue(e.message!!.contains("Restart required"))
    }
}
