package com.silkage.mygardenworld.feature.admin

import com.google.protobuf.Timestamp
import com.mygardenworld.v1.CreateUserRequest
import com.mygardenworld.v1.CreateUserResponse
import com.mygardenworld.v1.DeleteRedeemSourceRequest
import com.mygardenworld.v1.DeleteRedeemSourceResponse
import com.mygardenworld.v1.ListRedeemSourcesRequest
import com.mygardenworld.v1.ListRedeemSourcesResponse
import com.mygardenworld.v1.RedeemCode
import com.mygardenworld.v1.RedeemExpiryOverrideMode
import com.mygardenworld.v1.RedeemSource
import com.mygardenworld.v1.SyncRedeemSourceRequest
import com.mygardenworld.v1.SyncRedeemSourceResponse
import com.mygardenworld.v1.UpdateRedeemCodeExpiryRequest
import com.mygardenworld.v1.UpdateRedeemCodeExpiryResponse
import com.mygardenworld.v1.UpsertRedeemSourceRequest
import com.mygardenworld.v1.UpsertRedeemSourceResponse
import com.mygardenworld.v1.GetSystemStatsRequest
import com.mygardenworld.v1.GetSystemStatsResponse
import com.mygardenworld.v1.ListUsersRequest
import com.mygardenworld.v1.ListUsersResponse
import com.mygardenworld.v1.UpdateUserRequest
import com.mygardenworld.v1.UpdateUserResponse
import com.mygardenworld.v1.User
import com.mygardenworld.v1.UserStatus
import com.silkage.mygardenworld.core.network.ConnectClient

class AdminRepository(private val rpc: ConnectClient) {
    suspend fun stats(): GetSystemStatsResponse =
        rpc.call("AdminService", "GetSystemStats", GetSystemStatsRequest.getDefaultInstance(), GetSystemStatsResponse.parser())

    suspend fun users(): List<User> =
        rpc.call("AdminService", "ListUsers", ListUsersRequest.newBuilder().setPage(0).setPageSize(50).build(), ListUsersResponse.parser()).usersList

    suspend fun create(username: String, email: String, password: String, maxAccounts: Int?): User {
        val request = CreateUserRequest.newBuilder().setUsername(username.trim()).setEmail(email.trim()).setPassword(password)
        if (maxAccounts != null) request.setMaxAccounts(maxAccounts)
        return rpc.call("AdminService", "CreateUser", request.build(), CreateUserResponse.parser()).user
    }

    suspend fun setMaxAccounts(userId: Long, maxAccounts: Int): User =
        rpc.call("AdminService", "UpdateUser", UpdateUserRequest.newBuilder().setUserId(userId).setMaxAccounts(maxAccounts).build(), UpdateUserResponse.parser()).user

    suspend fun setStatus(userId: Long, status: UserStatus): User =
        rpc.call("AdminService", "UpdateUser", UpdateUserRequest.newBuilder().setUserId(userId).setStatus(status).build(), UpdateUserResponse.parser()).user

    suspend fun redeemSources(): List<RedeemSource> =
        rpc.call("AdminService", "ListRedeemSources", ListRedeemSourcesRequest.getDefaultInstance(), ListRedeemSourcesResponse.parser()).sourcesList

    /** Zero [UpsertRedeemSourceRequest.getId] creates a source. */
    suspend fun upsertRedeemSource(request: UpsertRedeemSourceRequest): RedeemSource =
        rpc.call("AdminService", "UpsertRedeemSource", request, UpsertRedeemSourceResponse.parser()).source

    suspend fun deleteRedeemSource(id: Long) {
        rpc.call("AdminService", "DeleteRedeemSource", DeleteRedeemSourceRequest.newBuilder().setId(id).build(), DeleteRedeemSourceResponse.parser())
    }

    suspend fun syncRedeemSource(id: Long): RedeemSource =
        rpc.call("AdminService", "SyncRedeemSource", SyncRedeemSourceRequest.newBuilder().setId(id).build(), SyncRedeemSourceResponse.parser(), readTimeoutSeconds = ConnectClient.LONG_READ_TIMEOUT_SECONDS).source

    /** [expiresAt] is required for FINITE; PERMANENT and SOURCE ignore it. */
    suspend fun updateRedeemExpiry(fingerprint: String, mode: RedeemExpiryOverrideMode, expiresAt: Timestamp? = null): RedeemCode {
        val request = UpdateRedeemCodeExpiryRequest.newBuilder().setFingerprint(fingerprint).setMode(mode)
        if (expiresAt != null) request.setExpiresAt(expiresAt)
        return rpc.call("AdminService", "UpdateRedeemCodeExpiry", request.build(), UpdateRedeemCodeExpiryResponse.parser()).code
    }
}
