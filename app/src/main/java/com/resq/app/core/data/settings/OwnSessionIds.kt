package com.resq.app.core.data.settings

import kotlin.random.Random

/** How long a finished SOS ID is still recognised as ours (it may still be echoed back to us). */
const val OWN_ID_MEMORY_SECONDS: Long = 72 * 3600L

/** One SOS session: a random ID chosen at Start, a counter that goes up per update, and the Start time. */
data class SosSession(val senderId: Int, val startedAtSeconds: Long, val counter: Int)

/**
 * Plain-Kotlin helpers for the "recent own IDs" list, so the rules can be unit tested
 * without Android. Stored as `id:seconds` pairs joined by commas.
 */
object OwnSessionIds {

    fun newSenderId(random: Random = Random.Default): Int = random.nextInt(from = 1, until = Int.MAX_VALUE)

    fun parse(stored: String?): List<Pair<Int, Long>> =
        stored.orEmpty().split(',').mapNotNull { part ->
            val bits = part.split(':')
            val id = bits.getOrNull(0)?.toIntOrNull()
            val at = bits.getOrNull(1)?.toLongOrNull()
            if (bits.size == 2 && id != null && at != null) id to at else null
        }

    fun format(entries: List<Pair<Int, Long>>): String = entries.joinToString(",") { "${it.first}:${it.second}" }

    /** Adds [id] (replacing an older copy of it) and drops anything older than the memory window. */
    fun remember(stored: String?, id: Int, nowSeconds: Long): String {
        val kept = parse(stored).filter { (other, at) -> other != id && nowSeconds - at <= OWN_ID_MEMORY_SECONDS }
        return format(kept + (id to nowSeconds))
    }

    fun isOwn(stored: String?, id: Int, nowSeconds: Long): Boolean =
        parse(stored).any { (other, at) -> other == id && nowSeconds - at <= OWN_ID_MEMORY_SECONDS }
}
