package com.qingyu.hermescompanion.today

import org.junit.Assert.*
import org.junit.Test

class TodayChangesTest {
    private fun card(id: String, status: String = "open") = TodayCard(id, "事项 $id", "事项的进展", TodayKind.FOLLOWUP, status = status)
    private fun baseline(vararg cards: TodayCard) = TodayChanges("scope").observe(cards.toList(), 1)

    @Test fun firstVisitDoesNotAnnounceExistingCardsOrPreviouslyCompletedWork() {
        assertTrue(baseline(card("a"), card("b", "done")).events.isEmpty())
    }
    @Test fun detectsOnlyNewActiveCardsAndConfirmedCompletionInOneSync() {
        val changes = baseline(card("a"), card("b")).observe(listOf(card("a", "done"), card("b", "archived"), card("c"), card("d", "done")), 2)
        assertEquals(listOf(TodayChangeKind.COMPLETED, TodayChangeKind.ADDED), changes.events.map { it.kind })
        assertEquals(setOf("c"), changes.newCardIds)
        assertEquals(listOf("a"), changes.recentCompleted.map { it.cardId })
    }
    @Test fun missingArchivedRenamedAndReorderedCardsAreNeverReportedAsCompleted() {
        val changes = baseline(card("a"), card("b"), card("c")).observe(listOf(card("c").copy(title = "改了名字"), card("b", "archived")))
        assertTrue(changes.events.isEmpty())
        assertTrue(changes.observe(listOf(card("a"), card("b", "archived"), card("c"))).events.isEmpty())
    }
    @Test fun repeatedReadsAndRestartDoNotDuplicateChangesOrForgetAcknowledgement() {
        val cards = listOf(card("a", "done"), card("b"))
        val first = baseline(card("a")).observe(cards, 2)
        val acknowledged = first.acknowledge(first.unread.map { it.sequence }.toSet())
        val restored = TodayChanges.decode(acknowledged.encode(), "scope")!!
        assertEquals(acknowledged, restored.observe(cards, 3))
        assertTrue(restored.unread.isEmpty())
        assertEquals(1, restored.recentCompleted.size)
        assertEquals("done", restored.known.first { it.id == "a" }.status)
    }
    @Test fun acknowledgingAnEarlierSheetDoesNotClearChangesArrivingWhileItWasOpen() {
        val first = baseline(card("a")).observe(listOf(card("a"), card("b")), 2)
        val next = first.observe(listOf(card("a", "done"), card("b"), card("c")), 3)
        assertEquals(setOf("a", "c"), next.acknowledge(first.unread.map { it.sequence }.toSet()).unread.map { it.cardId }.toSet())
    }
    @Test fun recentlyAddedCardBecomesOneCompletionAndReopenedCardLosesOutdatedCompletion() {
        val added = baseline().observe(listOf(card("a")), 2)
        val done = added.observe(listOf(card("a", "done")), 3)
        assertEquals(1, done.events.size)
        assertEquals(TodayChangeKind.COMPLETED, done.events.single().kind)
        assertTrue(done.observe(listOf(card("a")), 4).events.isEmpty())
    }
    @Test fun completedRecordSurvivesServerCleanupButDeletedNewCardIsNotStillAdvertised() {
        val changes = baseline(card("a")).observe(listOf(card("a", "done"), card("b")), 2).observe(emptyList(), 3)
        assertEquals(listOf("a"), changes.events.map { it.cardId })
        assertTrue(changes.newCardIds.isEmpty())
    }
    @Test fun scopesAndCorruptPayloadsCannotLeakAnotherAccountsReadingState() {
        val scope = TodayChanges.scope("server\naccount", "work", "/root")
        val value = TodayChanges(scope).observe(listOf(card("a"))).encode()
        assertNotNull(TodayChanges.decode(value, scope))
        assertNull(TodayChanges.decode(value, TodayChanges.scope("server\nother", "work", "/root")))
        assertNull(TodayChanges.decode(value, TodayChanges.scope("server\naccount", "other", "/root")))
        assertNull(TodayChanges.decode(value, TodayChanges.scope("server\naccount", "work", "/other")))
        assertNull(TodayChanges.decode("broken", scope))
    }
    @Test fun boundedHistoryRetainsCurrentCardsAndRecentChanges() {
        var changes = baseline()
        repeat(1100) { changes = changes.observe(listOf(card("id$it")), it.toLong()) }
        assertEquals(1024, changes.known.size)
        assertTrue(changes.events.size <= 80)
        assertEquals("id1099", changes.known.last().id)
    }
    @Test fun readingOrderHoldsBackgroundAdditionsUntilRevealAndNeverResortsExistingCards() {
        val initial = TodayReadingOrder().reconcile(listOf(card("a"), card("b")))
        val fresh = listOf(card("c"), card("b"), card("a"))
        val held = initial.reconcile(fresh)
        assertEquals(listOf("a", "b"), held.cards(fresh).map { it.id })
        assertEquals(listOf("a", "b", "c"), held.reconcile(fresh, reveal = true).ids)
        assertEquals(listOf("c", "b", "a"), TodayReadingOrder().reconcile(fresh).ids)
    }
}
