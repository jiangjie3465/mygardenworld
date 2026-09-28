package com.silkage.mygardenworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mygardenworld.v1.UserRole
import com.silkage.mygardenworld.core.auth.AuthState
import com.silkage.mygardenworld.core.ui.LoadingBox
import com.silkage.mygardenworld.core.ui.MyGardenWorldTheme
import com.silkage.mygardenworld.feature.accounts.AccountsScreen
import com.silkage.mygardenworld.feature.accounts.AccountsViewModel
import com.silkage.mygardenworld.feature.auth.LoginScreen
import com.silkage.mygardenworld.feature.admin.AdminScreen
import com.silkage.mygardenworld.feature.admin.AdminViewModel
import com.silkage.mygardenworld.feature.notifications.NotificationsScreen
import com.silkage.mygardenworld.feature.notifications.NotificationsViewModel
import com.silkage.mygardenworld.feature.redeem.RedeemScreen
import com.silkage.mygardenworld.feature.redeem.RedeemViewModel
import com.silkage.mygardenworld.feature.settings.SessionsScreen
import com.silkage.mygardenworld.feature.settings.SessionsViewModel
import com.silkage.mygardenworld.feature.settings.SettingsScreen
import com.silkage.mygardenworld.feature.workspace.WorkspaceScreen
import com.silkage.mygardenworld.feature.workspace.WorkspaceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MyGardenWorldApp).container
        setContent {
            MyGardenWorldTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(container)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val auth by container.auth.state.collectAsStateWithLifecycle()
    var restoreFailures by remember { mutableIntStateOf(0) }
    var retryNonce by remember { mutableIntStateOf(0) }
    // A transport failure keeps the stored refresh token and stays Restoring;
    // retry with backoff instead of leaving the user on a spinner forever.
    LaunchedEffect(retryNonce) {
        while (container.auth.state.value is AuthState.Restoring) {
            if (container.auth.restore()) break
            if (container.auth.state.value !is AuthState.Restoring) break
            restoreFailures++
            delay((2_000L shl (restoreFailures - 1).coerceAtMost(4)).coerceAtMost(30_000L))
        }
    }
    when (val state = auth) {
        is AuthState.Restoring -> RestoringScreen(
            failures = restoreFailures,
            onRetry = { retryNonce++ },
            onLogout = { scope.launch { container.auth.logout() } },
        )
        is AuthState.SignedOut -> LoginScreen(container.auth, container.accounts, state.reason)
        is AuthState.SignedIn -> SignedInNav(container)
    }
}

@Composable
private fun RestoringScreen(failures: Int, onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingBox("正在恢复登录…")
        if (failures > 0) {
            Text("暂时无法连接服务端，已重试 $failures 次，将自动继续重试。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                OutlinedButton(onClick = onLogout) { Text("退出登录") }
                Button(onClick = onRetry) { Text("立即重试") }
            }
        }
    }
}

@Composable
private fun SignedInNav(container: AppContainer) {
    val nav = rememberNavController()
    val serverLabel = container.auth.baseUrl?.removePrefix("https://")?.removePrefix("http://") ?: ""
    NavHost(nav, startDestination = "accounts") {
        composable("accounts") {
            val vm: AccountsViewModel = viewModel(factory = factory { AccountsViewModel(container) })
            AccountsScreen(vm, serverLabel, onOpenAccount = { nav.navigate("workspace/$it") }, onOpenSettings = { nav.navigate("settings") }, onOpenRedeem = { nav.navigate("redeem") })
        }
        composable("workspace/{accountId}", arguments = listOf(navArgument("accountId") { type = NavType.LongType })) { entry ->
            val accountId = entry.arguments?.getLong("accountId") ?: 0L
            val vm: WorkspaceViewModel = viewModel(key = "workspace-$accountId", factory = factory { WorkspaceViewModel(container, accountId) })
            WorkspaceScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                container,
                onBack = { nav.popBackStack() },
                onOpenSessions = { nav.navigate("sessions") },
                onOpenAdmin = { nav.navigate("admin") },
                onOpenRedeem = { nav.navigate("redeem") },
                onOpenNotifications = { nav.navigate("notifications") },
            )
        }
        composable("notifications") {
            val vm: NotificationsViewModel = viewModel(factory = factory { NotificationsViewModel(container) })
            NotificationsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("redeem") {
            val auth by container.auth.state.collectAsStateWithLifecycle()
            val isAdmin = (auth as? AuthState.SignedIn)?.user?.role == UserRole.USER_ROLE_ADMIN
            val vm: RedeemViewModel = viewModel(key = "redeem-$isAdmin", factory = factory { RedeemViewModel(container, isAdmin) })
            val workspace by container.workspace.state.collectAsStateWithLifecycle()
            RedeemScreen(vm, workspace.online, onBack = { nav.popBackStack() })
        }
        composable("sessions") {
            val vm: SessionsViewModel = viewModel(factory = factory { SessionsViewModel(container) })
            val workspace by container.workspace.state.collectAsStateWithLifecycle()
            SessionsScreen(vm, workspace.online, onBack = { nav.popBackStack() })
        }
        composable("admin") {
            val vm: AdminViewModel = viewModel(factory = factory { AdminViewModel(container) })
            val workspace by container.workspace.state.collectAsStateWithLifecycle()
            AdminScreen(vm, workspace.online, onBack = { nav.popBackStack() })
        }
    }
}

private inline fun <reified T : ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}
