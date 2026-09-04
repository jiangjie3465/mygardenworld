package com.silkage.mygardenworld

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.silkage.mygardenworld.core.auth.AuthSession
import com.silkage.mygardenworld.core.auth.AuthState
import com.silkage.mygardenworld.core.auth.KeystoreTokenStore
import com.silkage.mygardenworld.core.auth.SessionsRepository
import com.silkage.mygardenworld.core.game.Catalog
import com.silkage.mygardenworld.core.network.ConnectClient
import com.silkage.mygardenworld.core.protocol.WorkspaceRepository
import com.silkage.mygardenworld.core.protocol.WorkspaceSocket
import com.silkage.mygardenworld.feature.accounts.AccountsRepository
import com.silkage.mygardenworld.feature.admin.AdminRepository
import com.silkage.mygardenworld.feature.redeem.RedeemRepository
import com.silkage.mygardenworld.feature.workspace.PolicyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide singletons. The workspace socket follows the process
 * lifecycle: connected while the app is in the foreground and signed in,
 * disconnected in the background, and reconnected with the log cursor on resume.
 */
class AppContainer(application: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val http = ConnectClient.defaultHttpClient()
    val auth = AuthSession(KeystoreTokenStore(application), http, deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim())
    val accounts = AccountsRepository(auth.rpc)
    val policies = PolicyRepository(auth.rpc)
    val redeem = RedeemRepository(auth.rpc)
    val admin = AdminRepository(auth.rpc)
    val sessions = SessionsRepository(auth.rpc) { auth.deviceId }
    val workspace = WorkspaceRepository()
    val socket = WorkspaceSocket(scope, auth, http) { auth.rpc.baseUrl }
    @Volatile var catalog: Catalog = Catalog.EMPTY
        private set

    private var foreground = false

    init {
        scope.launch(Dispatchers.IO) { catalog = Catalog.load(application) }
        scope.launch { socket.events.collect(workspace::onEvent) }
        scope.launch {
            auth.state.collect { state ->
                if (state is AuthState.SignedIn) syncSocket() else socket.stop()
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground = true
                syncSocket()
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground = false
                socket.stop()
            }
        })
    }

    fun selectAccount(accountId: Long) {
        workspace.select(accountId)
        socket.selectAccount(accountId)
    }

    private fun syncSocket() {
        if (foreground && auth.state.value is AuthState.SignedIn) socket.start(workspace.state.value.selectedAccountId)
    }
}

class MyGardenWorldApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
