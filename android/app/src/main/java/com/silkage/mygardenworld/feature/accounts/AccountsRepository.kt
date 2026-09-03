package com.silkage.mygardenworld.feature.accounts

import com.mygardenworld.v1.Account
import com.mygardenworld.v1.Channel
import com.mygardenworld.v1.ConnectAccountRequest
import com.mygardenworld.v1.ConnectAccountResponse
import com.mygardenworld.v1.CreateAccountRequest
import com.mygardenworld.v1.CreateAccountResponse
import com.mygardenworld.v1.DeleteAccountRequest
import com.mygardenworld.v1.DeleteAccountResponse
import com.mygardenworld.v1.DisableAutomationRequest
import com.mygardenworld.v1.DisableAutomationResponse
import com.mygardenworld.v1.DisconnectAccountRequest
import com.mygardenworld.v1.DisconnectAccountResponse
import com.mygardenworld.v1.EnableAutomationRequest
import com.mygardenworld.v1.EnableAutomationResponse
import com.mygardenworld.v1.GetMeRequest
import com.mygardenworld.v1.GetMeResponse
import com.mygardenworld.v1.ListAccountsRequest
import com.mygardenworld.v1.ListAccountsResponse
import com.mygardenworld.v1.StartAlipayLoginRequest
import com.mygardenworld.v1.StartAlipayLoginResponse
import com.mygardenworld.v1.User
import com.silkage.mygardenworld.core.network.ConnectClient

/** Connect commands for account lifecycle and automation. */
class AccountsRepository(private val rpc: ConnectClient) {
    suspend fun me(): User = rpc.call("AuthService", "GetMe", GetMeRequest.getDefaultInstance(), GetMeResponse.parser()).user

    suspend fun list(): List<Account> =
        rpc.call("AccountService", "ListAccounts", ListAccountsRequest.getDefaultInstance(), ListAccountsResponse.parser()).accountsList

    suspend fun createIos(username: String, password: String): CreateAccountResponse = rpc.call(
        "AccountService", "CreateAccount",
        CreateAccountRequest.newBuilder().setUsername(username.trim()).setPassword(password).setChannel(Channel.CHANNEL_IOS).build(),
        CreateAccountResponse.parser(),
        readTimeoutSeconds = ConnectClient.LONG_READ_TIMEOUT_SECONDS,
    )

    suspend fun startAlipayLogin(): StartAlipayLoginResponse =
        rpc.call("AccountService", "StartAlipayLogin", StartAlipayLoginRequest.getDefaultInstance(), StartAlipayLoginResponse.parser(), readTimeoutSeconds = ConnectClient.LONG_READ_TIMEOUT_SECONDS)

    suspend fun delete(id: Long) {
        rpc.call("AccountService", "DeleteAccount", DeleteAccountRequest.newBuilder().setId(id).build(), DeleteAccountResponse.parser())
    }

    suspend fun connect(id: Long): Account =
        rpc.call("AccountService", "ConnectAccount", ConnectAccountRequest.newBuilder().setId(id).build(), ConnectAccountResponse.parser(), readTimeoutSeconds = ConnectClient.LONG_READ_TIMEOUT_SECONDS).account

    suspend fun disconnect(id: Long): Account =
        rpc.call("AccountService", "DisconnectAccount", DisconnectAccountRequest.newBuilder().setId(id).build(), DisconnectAccountResponse.parser()).account

    suspend fun enableAutomation(id: Long) {
        rpc.call("AutomationService", "EnableAutomation", EnableAutomationRequest.newBuilder().setAccountId(id).build(), EnableAutomationResponse.parser())
    }

    suspend fun disableAutomation(id: Long) {
        rpc.call("AutomationService", "DisableAutomation", DisableAutomationRequest.newBuilder().setAccountId(id).build(), DisableAutomationResponse.parser())
    }
}
