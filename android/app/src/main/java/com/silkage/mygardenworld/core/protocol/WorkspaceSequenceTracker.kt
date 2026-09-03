package com.silkage.mygardenworld.core.protocol

import com.mygardenworld.v1.Event

/**
 * Pure bookkeeping shared by the socket: strict server sequence checking and
 * per-account log cursors used for reconnect catch-up. Not thread-safe; the
 * socket serializes access.
 */
class WorkspaceSequenceTracker {
    private var lastSequence = 0L
    private val logCursors = HashMap<Long, Long>()

    /** Resets the sequence expectation for a brand-new connection. */
    fun resetConnection() {
        lastSequence = 0
    }

    /**
     * Records [sequence]. Returns false when a gap was detected, in which case
     * the caller must send a resync and drop the frame.
     */
    fun accept(sequence: Long): Boolean {
        val gap = lastSequence > 0 && sequence != lastSequence + 1
        lastSequence = sequence
        return !gap
    }

    fun noteLogs(events: Iterable<Event>) {
        for (event in events) {
            if (event.id <= 0) continue
            val current = logCursors[event.accountId] ?: 0
            if (event.id > current) logCursors[event.accountId] = event.id
        }
    }

    fun afterLogId(accountId: Long): Long = logCursors[accountId] ?: 0
}
