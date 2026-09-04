package com.silkage.mygardenworld.feature.admin

import com.mygardenworld.v1.CreateUserRequest
import com.mygardenworld.v1.CreateUserResponse
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
}
