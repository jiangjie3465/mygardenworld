package com.silkage.mygardenworld.feature.accounts

import com.mygardenworld.v1.Account
import com.mygardenworld.v1.Channel
import com.mygardenworld.v1.ConnectAccountRequest
import com.mygardenworld.v1.ConnectAccountResponse
import com.mygardenworld.v1.CreateAccountRequest
import com.mygardenworld.v1.CreateAccountResponse
import com.mygardenworld.v1.DeleteAccountRequest
import com.mygardenworld.v1.DeleteAccountResponse
import com.mygardenworld.v1.DeleteUnionRaceTaskRequest
import com.mygardenworld.v1.DeleteUnionRaceTaskResponse
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
import com.mygardenworld.v1.ReauthenticateAccountRequest
import com.mygardenworld.v1.ReauthenticateAccountResponse
import com.mygardenworld.v1.StartAlipayLoginRequest
import com.mygardenworld.v1.StartAlipayLoginResponse
import com.mygardenworld.v1.TakeUnionRaceTaskRequest
import com.mygardenworld.v1.TakeUnionRaceTaskResponse
import com.mygardenworld.v1.User
import com.silkage.mygardenworld.core.network.ConnectClient

/** Connect commands for account lifecycle and automation. */
class AccountsRepository(private val rpc: ConnectClient) {
    suspend fun me(): User = rpc.call("AuthService", "GetMe", GetMeRequest.getDefaultInstance(), GetMeResponse.parser()).user

    suspend fun list(): List<Account> =
        rpc.call("AccountService", "ListAccounts", ListAccountsRequest.getDefaultInstance(), ListAccountsResponse.parser()).accountsList

    /** [initialPolicyAccountId] copies that account's saved policy before the first runner starts; zero uses defaults. */
    suspend fun createIos(username: String, password: String, initialPolicyAccountId: Long = 0): CreateAccountResponse = rpc.call(
        "AccountService", "CreateAccount",
        CreateAccountRequest.newBuilder().setUsername(username.trim()).setPassword(password).setChannel(Channel.CHANNEL_IOS).setInitialPolicyAccountId(initialPolicyAccountId).build(),
        CreateAccountResponse.parser(),
        readTimeoutSeconds = ConnectClient.LONG_READ_TIMEOUT_SECONDS,
    )

    /** A nonzero [accountId] re-authorizes that existing Alipay account instead of creating one. */
    suspend fun startAlipayLogin(accountId: Long = 0, initialPolicyAccountId: Long = 0): StartAlipayLoginResponse = rpc.call(
        "AccountService", "StartAlipayLogin",
        StartAlipayLoginRequest.newBuilder().setAccountId(accountId).setInitialPolicyAccountId(if (accountId == 0L) initialPolicyAccountId else 0).build(),
        StartAlipayLoginResponse.parser(),
        readTimeoutSeconds = ConnectClient.LONG_READ_TIMEOUT_SECONDS,
    )

    /** Updates stored iOS credentials and logs in again, keeping policy, history and run/pause intent. */
    suspend fun reauthenticate(id: Long, password: String): ReauthenticateAccountResponse = rpc.call(
        "AccountService", "ReauthenticateAccount",
        ReauthenticateAccountRequest.newBuilder().setId(id).setPassword(password).build(),
        ReauthenticateAccountResponse.parser(),
        readTimeoutSeconds = ConnectClient.LONG_READ_TIMEOUT_SECONDS,
    )

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

    suspend fun takeUnionRaceTask(accountId: Long, taskMsId: Long) {
        rpc.call("AutomationService", "TakeUnionRaceTask", TakeUnionRaceTaskRequest.newBuilder().setAccountId(accountId).setTaskMsId(taskMsId).build(), TakeUnionRaceTaskResponse.parser())
    }

    suspend fun deleteUnionRaceTask(accountId: Long, taskMsId: Long) {
        rpc.call("AutomationService", "DeleteUnionRaceTask", DeleteUnionRaceTaskRequest.newBuilder().setAccountId(accountId).setTaskMsId(taskMsId).build(), DeleteUnionRaceTaskResponse.parser())
    }

    suspend fun disableAutomation(id: Long) {
        rpc.call("AutomationService", "DisableAutomation", DisableAutomationRequest.newBuilder().setAccountId(id).build(), DisableAutomationResponse.parser())
    }
}
