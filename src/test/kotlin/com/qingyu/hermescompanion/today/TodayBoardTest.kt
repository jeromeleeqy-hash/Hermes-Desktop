package com.qingyu.hermescompanion.today

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class TodayBoardTest {
    private fun card(extra: String = "") = """{"id":"meeting","title":"Plan","summary":"A recorded summary","kind":"decision"$extra}"""
    private fun board(cards: String = card()) = """{"schema_version":1,"date":"2026-10-03","generated_at":"2026-10-03T09:00:00+08:00","headline":"Today","summary":"Brief","cards":[$cards]}"""
    @Test fun existingV1NeedsNoNewFields() {
        val result = TodayBoard.decode(board(), "/work")
        assertEquals("meeting", result.cards.single().id)
        assertTrue(result.cards.single().presentation.steps.isEmpty())
    }
    @Test fun blockedReferencesDoNotHideCardsOrAuthorizeOpening() {
        val result = TodayBoard.decode(board(card(""", "sources":["notes.md","/outside/private.md","../escape","https://example.com", "[notes](notes.md)"]""")), "/work")
        assertEquals(listOf("/work/notes.md"), result.cards.single().sources)
        assertEquals(4, result.cards.single().unavailableSources.size)
    }
    @Test fun pathsRespectSegmentsAndWindowsRoots() {
        assertNull(safeTodayPath("/work", "/workspace/file.md"))
        assertNull(safeTodayPath("/work", "a/../secret"))
        assertEquals("C:\\Work\\notes.md", safeTodayPath("C:\\Work", "notes.md"))
        assertNull(safeTodayPath("C:\\Work", "D:\\notes.md"))
    }
    @Test fun duplicateIdsAndBadCoreFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TodayBoard.decode(board(card() + "," + card()), "/work") }
        assertThrows(Exception::class.java) { TodayBoard.decode(board().replace("2026-10-03T09:00:00+08:00", "2026-10-03T09:00:00"), "/work") }
        assertThrows(Exception::class.java) { TodayBoard.decode(board(card(""", "status":"maybe"""")), "/work") }
    }
    @Test fun optionalPresentationFailureFallsBackToOriginalSummary() {
        val value = TodayBoard.decode(board(card(""", "presentation":{"steps":[{"title":"A","status":"inferred"}]}""")), "/work").cards.single()
        assertTrue(value.presentation.steps.isEmpty())
        assertNotNull(value.presentation.issue)
        assertTrue(value.presentation.invalidData.contains("inferred"))
        assertEquals("A recorded summary", value.summary)
    }
    @Test fun progressRequiresExplicitRecordedSteps() {
        val value = TodayBoard.decode(board(card(""", "presentation":{"steps":[{"title":"A","status":"done"},{"title":"B","status":"open"}],"metrics":[{"label":"Count","value":"12"}]}""")), "/work").cards.single()
        assertEquals(1, value.presentation.steps.count { it.done })
        assertEquals(2, value.presentation.steps.size)
        assertEquals("12", value.presentation.metrics.single().value)
        assertEquals("done", JSONObject(value.contextDocument()).getJSONArray("steps").getJSONObject(0).getString("status"))
    }
    @Test fun focusExcludesClosedItemsAndPreservesEditorialOrder() {
        val cards = listOf(TodayCard("a", "a", "", TodayKind.UPDATE), TodayCard("b", "b", "", TodayKind.DECISION),
            TodayCard("c", "c", "", TodayKind.SCHEDULE, status = "done"))
        assertEquals(listOf("a", "b"), focusCards(cards).map { it.id })
        assertEquals(listOf("a", "b", "c"), focusCards(cards, true).map { it.id })
    }
    @Test fun structuredQuestionKeepsFactsAndBackgroundInDraftContext() {
        val value = TodayBoard.decode(board(card(""", "presentation":{"intent":"clarify","question":"同一件事吗？","facts":["9/28 看场地","9/29 确定场地"],"background":"旧记录尚未核对"}""")), "/work").cards.single()
        assertEquals("clarify", value.presentation.intent)
        assertEquals(2, value.presentation.facts.size)
        val context = JSONObject(value.contextDocument())
        assertEquals("旧记录尚未核对", context.getString("background"))
        assertEquals("同一件事吗？", context.getString("question"))
    }
    @Test fun checkingWithoutNewFactsAdvancesCheckTimeWhileKeepingContentTimeAndCards() {
        val initial = TodayBoard.decode(board(), "/work")
        val updated = JSONObject(board()).put("source_state", JSONObject().put("checked_at", "2026-10-03T21:00:00+08:00"))
        val checked = TodayBoard.decode(updated.toString(), "/work")
        assertEquals(initial.generatedAt, checked.generatedAt)
        assertEquals(initial.cards, checked.cards)
        assertEquals("2026-10-03T21:00+08:00", checked.currentContentCheckedAt.toString())
    }
    @Test fun missingOrMalformedOptionalCheckTimeDoesNotHideAnOtherwiseValidOverview() {
        assertNull(TodayBoard.decode(board(), "/work").checkedAt)
        listOf<Any>("invalid", "2026-10-03T21:00:00", 123, JSONObject.NULL, "x".repeat(81)).forEach { value ->
            val decoded = TodayBoard.decode(JSONObject(board()).put("source_state", JSONObject().put("checked_at", value)).toString(), "/work")
            assertNull(decoded.checkedAt)
            assertEquals("meeting", decoded.cards.single().id)
        }
    }
    @Test fun earlierCheckDoesNotImplyTheCurrentContentWasCheckedAndOffsetsCompareByInstant() {
        fun checked(value: String) = TodayBoard.decode(JSONObject(board()).put("source_state", JSONObject().put("checked_at", value)).toString(), "/work")
        assertNotNull(checked("2026-10-03T08:52:00+08:00").checkedAt)
        assertNull(checked("2026-10-03T08:52:00+08:00").currentContentCheckedAt)
        assertNotNull(checked("2026-10-03T01:00:00Z").currentContentCheckedAt)
    }
}
