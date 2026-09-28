package com.silkage.mygardenworld.feature.workspace

import com.mygardenworld.v1.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class LogsTabTest {
    private fun event(id: Long, kind: String, domain: String = "", message: String = "") =
        Event.newBuilder().setId(id).setKind(kind).setDomain(domain).setMessage(message).build()

    @Test
    fun `keeps only the newest completed race sync and drops planned syncs`() {
        val events = listOf(
            event(6, "operation_planned", "union.race.sync"),
            event(5, "race_task_sync"),
            event(4, "operation_ack", "union.race.sync"),
            event(3, "harvest", "plant"),
            event(2, "race_task_sync"),
            event(1, "operation_planned", "plant"),
        )
        assertEquals(listOf(5L, 3L, 1L), collapseRaceSyncLogEvents(events).map { it.id })
    }
}
