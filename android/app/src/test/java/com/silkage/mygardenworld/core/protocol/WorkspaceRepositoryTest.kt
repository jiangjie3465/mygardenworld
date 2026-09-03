package com.silkage.mygardenworld.core.protocol

import com.mygardenworld.v1.AccountStatus
import com.mygardenworld.v1.AccountStatusBatch
import com.mygardenworld.v1.BasicView
import com.mygardenworld.v1.Event
import com.mygardenworld.v1.WorkspaceError
import com.mygardenworld.v1.WorkspaceLogPage
import com.mygardenworld.v1.WorkspaceLogPageKind
import com.mygardenworld.v1.WorkspacePatch
import com.mygardenworld.v1.WorkspaceReady
import com.mygardenworld.v1.WorkspaceSnapshot
import com.mygardenworld.v1.WorkspaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceRepositoryTest {
    private fun event(id: Long, accountId: Long = 7) = Event.newBuilder().setId(id).setAccountId(accountId).setMessage("e$id").build()
    private fun page(kind: WorkspaceLogPageKind, vararg ids: Long, accountId: Long = 7, more: Boolean = false, next: Long = 0) =
        WorkspaceLogPage.newBuilder().setAccountId(accountId).setKind(kind).addAllEvents(ids.map { event(it, accountId) }).setHasMoreBefore(more).setNextBeforeId(next).build()

    @Test
    fun readyAndStatusBatchesMergeStatuses() {
        val repo = WorkspaceRepository()
        repo.onEvent(WorkspaceEvent.Ready(WorkspaceReady.newBuilder().setServerVersion("1.0").addAccounts(AccountStatus.newBuilder().setAccountId(1).setConnected(false)).build()))
        repo.onEvent(WorkspaceEvent.Statuses(AccountStatusBatch.newBuilder().addAccounts(AccountStatus.newBuilder().setAccountId(1).setConnected(true)).addAccounts(AccountStatus.newBuilder().setAccountId(2)).build()))
        val state = repo.state.value
        assertEquals("1.0", state.serverVersion)
        assertTrue(state.statuses.getValue(1).connected)
        assertEquals(setOf(1L, 2L), state.statuses.keys)
    }

    @Test
    fun snapshotReplacesStateAndLogsThenPatchesMerge() {
        val repo = WorkspaceRepository()
        repo.select(7)
        repo.onEvent(WorkspaceEvent.Logs(page(WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_LIVE, 99)))
        assertTrue("logs before snapshot are kept only when for the selected account", repo.state.value.logs.events.size == 1)
        repo.onEvent(
            WorkspaceEvent.Snapshot(
                WorkspaceSnapshot.newBuilder()
                    .setState(WorkspaceState.newBuilder().setAccountId(7).setRevision(3).setBasic(BasicView.newBuilder().setGold(10)))
                    .setLogs(page(WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_RECENT, 5, 4, 3, more = true, next = 3))
                    .build(),
            ),
        )
        var state = repo.state.value
        assertEquals(10, state.state!!.basic.gold)
        assertEquals(listOf(5L, 4L, 3L), state.logs.events.map { it.id })
        assertTrue(state.logs.hasMoreBefore)

        repo.onEvent(WorkspaceEvent.Patch(WorkspacePatch.newBuilder().setAccountId(7).setRevision(4).setBasic(BasicView.newBuilder().setGold(11)).build()))
        repo.onEvent(WorkspaceEvent.Patch(WorkspacePatch.newBuilder().setAccountId(8).setRevision(9).setBasic(BasicView.newBuilder().setGold(99)).build()))
        state = repo.state.value
        assertEquals("patch for another account is ignored", 11, state.state!!.basic.gold)
        assertEquals(4, state.state!!.revision)
    }

    @Test
    fun reselectSnapshotWithCatchUpPageExtendsExistingWindow() {
        val repo = WorkspaceRepository()
        repo.select(7)
        repo.onEvent(
            WorkspaceEvent.Snapshot(
                WorkspaceSnapshot.newBuilder()
                    .setState(WorkspaceState.newBuilder().setAccountId(7).setRevision(1))
                    .setLogs(page(WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_RECENT, 5, 4, 3, more = true, next = 3))
                    .build(),
            ),
        )
        // Same account selected again with after_log_id = 5: the server answers
        // with the events after the cursor rather than a fresh recent page.
        repo.select(7)
        repo.onEvent(
            WorkspaceEvent.Snapshot(
                WorkspaceSnapshot.newBuilder()
                    .setState(WorkspaceState.newBuilder().setAccountId(7).setRevision(2))
                    .setLogs(page(WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_AFTER, 7, 6))
                    .build(),
            ),
        )
        val logs = repo.state.value.logs
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), logs.events.map { it.id })
        assertTrue("older-page cursor survives the catch-up", logs.hasMoreBefore)
        assertEquals(2, repo.state.value.state!!.revision)

        // A different account always starts from its own recent page.
        repo.select(8)
        repo.onEvent(
            WorkspaceEvent.Snapshot(
                WorkspaceSnapshot.newBuilder()
                    .setState(WorkspaceState.newBuilder().setAccountId(8))
                    .setLogs(page(WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_RECENT, 9, accountId = 8))
                    .build(),
            ),
        )
        assertEquals(listOf(9L), repo.state.value.logs.events.map { it.id })
    }

    @Test
    fun logPagesPrependLiveAppendOlderAndDeduplicate() {
        val repo = WorkspaceRepository(maxLogEvents = 6)
        repo.select(7)
        repo.onEvent(WorkspaceEvent.Logs(page(WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_RECENT, 5, 4, 3, more = true, next = 3)))
        repo.onEvent(WorkspaceEvent.Logs(page(WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_LIVE, 6, 5)))
        repo.markLoadingOlder()
        assertTrue(repo.state.value.logs.loadingOlder)
        repo.onEvent(WorkspaceEvent.Logs(page(WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_BEFORE, 3, 2, 1, more = false)))
        repo.onEvent(WorkspaceEvent.Logs(page(WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_AFTER, 8, 7, accountId = 9)))
        val logs = repo.state.value.logs
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L, 1L), logs.events.map { it.id })
        assertFalse(logs.loadingOlder)
        assertFalse(logs.hasMoreBefore)
        repo.onEvent(WorkspaceEvent.Logs(page(WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_AFTER, 8, 7)))
        assertEquals("window is capped to maxLogEvents newest-first", listOf(8L, 7L, 6L, 5L, 4L, 3L), repo.state.value.logs.events.map { it.id })
    }

    @Test
    fun selectingAnotherAccountClearsViewAndErrorsAndAuthExpiryCloses() {
        val repo = WorkspaceRepository()
        repo.select(7)
        repo.onEvent(WorkspaceEvent.Connection(WorkspaceConnectionState.OPEN))
        repo.onEvent(WorkspaceEvent.Snapshot(WorkspaceSnapshot.newBuilder().setState(WorkspaceState.newBuilder().setAccountId(7)).build()))
        repo.onEvent(WorkspaceEvent.Error(WorkspaceError.newBuilder().setCode("x").setMessage("boom").build()))
        assertEquals("boom", repo.state.value.lastError!!.message)
        repo.select(8)
        assertNull(repo.state.value.state)
        assertNull(repo.state.value.lastError)
        assertTrue(repo.state.value.online)
        repo.onEvent(WorkspaceEvent.AuthExpired)
        assertTrue(repo.state.value.authExpired)
        assertFalse(repo.state.value.online)
    }
}
