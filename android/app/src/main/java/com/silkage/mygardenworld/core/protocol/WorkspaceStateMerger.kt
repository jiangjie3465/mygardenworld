package com.silkage.mygardenworld.core.protocol

import com.mygardenworld.v1.WorkspaceDomain
import com.mygardenworld.v1.WorkspacePatch
import com.mygardenworld.v1.WorkspaceState

/**
 * Applies a WorkspacePatch to a WorkspaceState. Message presence means the
 * domain changed; absent fields stay unchanged; cleared_domains are emptied.
 */
object WorkspaceStateMerger {
    fun apply(current: WorkspaceState?, patch: WorkspacePatch): WorkspaceState {
        val builder = (current ?: WorkspaceState.getDefaultInstance()).toBuilder()
        builder.revision = patch.revision
        if (patch.accountId != 0L) builder.accountId = patch.accountId
        if (patch.hasAccountStatus()) builder.accountStatus = patch.accountStatus
        if (patch.hasPolicy()) builder.policy = patch.policy
        if (patch.hasBasic()) builder.basic = patch.basic
        if (patch.hasGarden()) builder.garden = patch.garden
        if (patch.hasOrders()) builder.orders = patch.orders
        if (patch.hasUnion()) builder.union = patch.union
        if (patch.hasActivities()) builder.activities = patch.activities
        if (patch.hasWarehouse()) builder.warehouse = patch.warehouse
        if (patch.hasStatistics()) builder.statistics = patch.statistics
        for (domain in patch.clearedDomainsList) {
            when (domain) {
                WorkspaceDomain.WORKSPACE_DOMAIN_BASIC -> builder.clearBasic()
                WorkspaceDomain.WORKSPACE_DOMAIN_GARDEN -> builder.clearGarden()
                WorkspaceDomain.WORKSPACE_DOMAIN_ORDERS -> builder.clearOrders()
                WorkspaceDomain.WORKSPACE_DOMAIN_UNION -> builder.clearUnion()
                WorkspaceDomain.WORKSPACE_DOMAIN_ACTIVITIES -> builder.clearActivities()
                WorkspaceDomain.WORKSPACE_DOMAIN_WAREHOUSE -> builder.clearWarehouse()
                WorkspaceDomain.WORKSPACE_DOMAIN_STATISTICS -> builder.clearStatistics()
                else -> Unit
            }
        }
        return builder.build()
    }
}
