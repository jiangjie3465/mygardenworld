package com.silkage.mygardenworld.core.protocol

import com.mygardenworld.v1.AccountStatus
import com.mygardenworld.v1.BasicView
import com.mygardenworld.v1.GardenView
import com.mygardenworld.v1.WorkspaceDomain
import com.mygardenworld.v1.WorkspacePatch
import com.mygardenworld.v1.WorkspaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceStateMergerTest {
    @Test
    fun presentFieldsReplaceAbsentFieldsStayAndClearedDomainsEmpty() {
        val initial = WorkspaceState.newBuilder()
            .setRevision(1)
            .setAccountId(7)
            .setAccountStatus(AccountStatus.newBuilder().setAccountId(7).setConnected(false))
            .setBasic(BasicView.getDefaultInstance())
            .setGarden(GardenView.getDefaultInstance())
            .build()

        val patch = WorkspacePatch.newBuilder()
            .setRevision(2)
            .setAccountStatus(AccountStatus.newBuilder().setAccountId(7).setConnected(true))
            .addClearedDomains(WorkspaceDomain.WORKSPACE_DOMAIN_GARDEN)
            .build()
        val merged = WorkspaceStateMerger.apply(initial, patch)

        assertEquals(2, merged.revision)
        assertEquals(7, merged.accountId)
        assertTrue(merged.accountStatus.connected)
        assertTrue("absent basic stays", merged.hasBasic())
        assertFalse("cleared garden is removed", merged.hasGarden())
    }

    @Test
    fun patchWithoutPriorStateStartsFromEmpty() {
        val patch = WorkspacePatch.newBuilder().setRevision(5).setAccountId(3).setBasic(BasicView.getDefaultInstance()).build()
        val merged = WorkspaceStateMerger.apply(null, patch)
        assertEquals(5, merged.revision)
        assertEquals(3, merged.accountId)
        assertTrue(merged.hasBasic())
    }
}
