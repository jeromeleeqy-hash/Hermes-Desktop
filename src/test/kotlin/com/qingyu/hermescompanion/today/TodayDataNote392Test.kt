package com.qingyu.hermescompanion.today

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TodayDataNote392Test {
    private fun fixture(): JSONObject = JSONObject(File("src/main/resources/today/hermes-today-examples.json").readText())
    @Test fun scopeSurvivesDecodingAndConversationContextWithoutInferringADate() {
        val raw = fixture()
        val card = raw.getJSONArray("cards").getJSONObject(0)
        card.getJSONObject("presentation").put("data_note", "统计日期待核对：报告标 10/5，表格标 10/4")
        val parsed = TodayBoard.decode(raw.toString(), "/work").cards.first()
        assertEquals("统计日期待核对：报告标 10/5，表格标 10/4", parsed.presentation.dataNote)
        assertEquals(parsed.presentation.dataNote, JSONObject(parsed.conversationContextDocument()).getString("data_note"))
        card.getJSONObject("presentation").remove("data_note")
        val legacy = TodayBoard.decode(raw.toString(), "/work").cards.first()
        assertEquals("", legacy.presentation.dataNote)
        assertFalse(JSONObject(legacy.contextDocument()).has("data_note"))
    }

    @Test fun malformedOptionalScopeNeverHidesOtherwiseValidMetricsOrChangesFacts() {
        for (invalid in listOf(123, JSONObject(), "字".repeat(121))) {
            val raw = fixture()
            val index = (0 until raw.getJSONArray("cards").length()).first {
                raw.getJSONArray("cards").getJSONObject(it).getJSONObject("presentation").optString("layout") == "metrics"
            }
            val original = TodayBoard.decode(raw.toString(), "/work").cards[index]
            raw.getJSONArray("cards").getJSONObject(index).getJSONObject("presentation").put("data_note", invalid)
            val parsed = TodayBoard.decode(raw.toString(), "/work").cards[index]
            assertEquals(original.presentation.metrics, parsed.presentation.metrics)
            assertEquals(original.summary, parsed.summary)
            assertEquals(original.status, parsed.status)
            assertEquals("", parsed.presentation.dataNote)
            assertNull(parsed.presentation.issue)
        }
    }
}
