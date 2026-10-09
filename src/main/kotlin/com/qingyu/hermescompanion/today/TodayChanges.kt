package com.qingyu.hermescompanion.today

import org.json.JSONArray
import org.json.JSONObject

enum class TodayChangeKind { ADDED, COMPLETED }

/** Local reading state only. It never writes or infers server task completion. */
data class TodayChange(
    val sequence: Long, val cardId: String, val kind: TodayChangeKind,
    val title: String, val summary: String, val observedAt: Long, val unread: Boolean = true,
)

data class TodayKnownCard(val id: String, val status: String)

data class TodayChanges(
    val scope: String = "", val initialized: Boolean = false,
    val known: List<TodayKnownCard> = emptyList(), val events: List<TodayChange> = emptyList(),
    val nextSequence: Long = 1,
) {
    val unread get() = events.filter { it.unread }
    val newCardIds get() = unread.filter { it.kind == TodayChangeKind.ADDED }.map { it.cardId }.toSet()
    val recentCompleted get() = events.filter { it.kind == TodayChangeKind.COMPLETED }.asReversed().take(12)

    fun observe(cards: List<TodayCard>, now: Long = System.currentTimeMillis()): TodayChanges {
        val before = known.associate { it.id to it.status }
        val current = cards.associateBy { it.id }
        var sequence = nextSequence
        val updated = events.mapNotNull { event ->
            val card = current[event.cardId]
            when {
                event.kind == TodayChangeKind.ADDED && (card == null || card.isClosed) -> null
                event.kind == TodayChangeKind.COMPLETED && card != null && !card.isClosed -> null
                card != null -> event.copy(title = card.title, summary = card.readableContext)
                else -> event
            }
        }.toMutableList()
        if (initialized) cards.forEach { card ->
            val old = before[card.id]
            val kind = when {
                old == null && !card.isClosed -> TodayChangeKind.ADDED
                old != null && old != "done" && old != "archived" && card.status == "done" -> TodayChangeKind.COMPLETED
                else -> null
            }
            if (kind != null) {
                updated.removeAll { it.cardId == card.id }
                updated += TodayChange(sequence++, card.id, kind, card.title, card.readableContext, now)
            }
        }
        // Keep tombstones so an omitted/reintroduced card is not repeatedly announced as new.
        val history = known.filterNot { it.id in current } + cards.map { TodayKnownCard(it.id, it.status) }
        return copy(initialized = true, known = history.takeLast(1024), events = updated.takeLast(80), nextSequence = sequence)
    }

    fun acknowledge(sequences: Set<Long>) = copy(events = events.map {
        if (it.sequence in sequences) it.copy(unread = false) else it
    })

    fun encode(): String = JSONObject().apply {
        put("version", 1); put("scope", scope); put("initialized", initialized); put("next", nextSequence)
        put("known", JSONArray(known.map { JSONObject().put("id", it.id).put("status", it.status) }))
        put("events", JSONArray(events.map {
            JSONObject().put("seq", it.sequence).put("card", it.cardId).put("kind", it.kind.name)
                .put("title", it.title).put("summary", it.summary).put("at", it.observedAt).put("unread", it.unread)
        }))
    }.toString()

    companion object {
        fun scope(connection: String, profile: String, root: String) = JSONArray(listOf(connection, profile, root.trimEnd('/'))).toString()

        fun decode(raw: String?, scope: String): TodayChanges? = runCatching {
            if (raw.isNullOrBlank()) return null
            require(raw.length <= 256 * 1024)
            val obj = JSONObject(raw)
            require(obj.getInt("version") == 1 && obj.getString("scope") == scope)
            val known = obj.getJSONArray("known")
            val events = obj.getJSONArray("events")
            require(known.length() <= 1024 && events.length() <= 80)
            TodayChanges(scope, obj.getBoolean("initialized"),
                (0 until known.length()).map { known.getJSONObject(it).let { v -> TodayKnownCard(v.getString("id"), v.getString("status")) } },
                (0 until events.length()).map { events.getJSONObject(it).let { v ->
                    TodayChange(v.getLong("seq"), v.getString("card"), TodayChangeKind.valueOf(v.getString("kind")),
                        v.getString("title"), v.getString("summary"), v.getLong("at"), v.getBoolean("unread"))
                } }, obj.getLong("next"))
        }.getOrNull()
    }
}

/** Preserve the visible order during a visit. New items wait for an explicit reveal or a new visit. */
internal data class TodayReadingOrder(val ids: List<String> = emptyList(), val initialized: Boolean = false) {
    fun reconcile(cards: List<TodayCard>, reveal: Boolean = false): TodayReadingOrder {
        val available = cards.map { it.id }
        if (!initialized) return TodayReadingOrder(available, true)
        val retained = ids // A temporarily omitted known card may return during this visit.
        return copy(ids = retained + if (reveal) available.filterNot { it in retained } else emptyList())
    }
    fun cards(cards: List<TodayCard>) = cards.associateBy { it.id }.let { byId -> ids.mapNotNull(byId::get) }
}
