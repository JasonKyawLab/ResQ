package com.resq.app.core.data.model

import com.resq.app.core.ble.MAX_MESSAGE_BYTES
import com.resq.app.core.ble.truncateUtf8

/**
 * Builds the text that is actually broadcast from the two things the user
 * writes: a saved "where am I" line (room, floor, building — set once, ahead
 * of time, so it still goes out when the user can barely type) and the
 * free-text SOS message. Both share the single [MAX_MESSAGE_BYTES] budget.
 */
object SosMessage {

    /** Cap for the saved location line, so it can't crowd out the message itself. */
    const val LOCATION_NOTE_MAX_BYTES = 48

    private const val SEPARATOR = " | "

    fun compose(locationNote: String, message: String): String {
        val note = locationNote.trim()
        val body = message.trim()
        val combined = when {
            note.isEmpty() -> body
            body.isEmpty() -> note
            else -> note + SEPARATOR + body
        }
        return combined.truncateUtf8(MAX_MESSAGE_BYTES)
    }

    /** Bytes left for the free-text message once the saved location line is accounted for. */
    fun messageBudgetBytes(locationNote: String): Int {
        val note = locationNote.trim()
        if (note.isEmpty()) return MAX_MESSAGE_BYTES
        val used = note.toByteArray(Charsets.UTF_8).size + SEPARATOR.length
        return (MAX_MESSAGE_BYTES - used).coerceAtLeast(0)
    }
}
