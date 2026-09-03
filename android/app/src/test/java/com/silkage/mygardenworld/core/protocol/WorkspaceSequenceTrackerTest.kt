package com.silkage.mygardenworld.core.protocol

import com.mygardenworld.v1.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSequenceTrackerTest {
    @Test
    fun detectsGapsAndResetsPerConnection() {
        val tracker = WorkspaceSequenceTracker()
        assertTrue(tracker.accept(1))
        assertTrue(tracker.accept(2))
        assertFalse("gap 2 -> 4 must trigger resync", tracker.accept(4))
        assertTrue("after a gap the new sequence becomes the baseline", tracker.accept(5))
        tracker.resetConnection()
        assertTrue("a fresh connection starts at any sequence", tracker.accept(1))
    }

    @Test
    fun keepsHighestLogIdPerAccount() {
        val tracker = WorkspaceSequenceTracker()
        tracker.noteLogs(
            listOf(
                Event.newBuilder().setAccountId(1).setId(10).build(),
                Event.newBuilder().setAccountId(1).setId(7).build(),
                Event.newBuilder().setAccountId(2).setId(3).build(),
                Event.newBuilder().setAccountId(2).setId(0).build(),
            ),
        )
        assertEquals(10, tracker.afterLogId(1))
        assertEquals(3, tracker.afterLogId(2))
        assertEquals(0, tracker.afterLogId(99))
    }
}
