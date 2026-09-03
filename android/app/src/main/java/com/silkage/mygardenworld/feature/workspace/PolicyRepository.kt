package com.silkage.mygardenworld.feature.workspace

import com.mygardenworld.v1.GetPolicyRequest
import com.mygardenworld.v1.GetPolicyResponse
import com.mygardenworld.v1.Policy
import com.mygardenworld.v1.SetPolicyRequest
import com.mygardenworld.v1.SetPolicyResponse
import com.silkage.mygardenworld.core.network.ConnectClient

class PolicyRepository(private val rpc: ConnectClient) {
    suspend fun get(accountId: Long): Policy =
        rpc.call("PolicyService", "GetPolicy", GetPolicyRequest.newBuilder().setAccountId(accountId).build(), GetPolicyResponse.parser()).policy

    /** Replaces the whole policy document; returns the effective policy. */
    suspend fun set(accountId: Long, policy: Policy): Policy =
        rpc.call("PolicyService", "SetPolicy", SetPolicyRequest.newBuilder().setAccountId(accountId).setPolicy(policy).build(), SetPolicyResponse.parser()).policy
}
