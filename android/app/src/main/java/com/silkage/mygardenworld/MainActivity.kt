package com.silkage.mygardenworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
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
import com.silkage.mygardenworld.core.auth.AuthState
import com.silkage.mygardenworld.core.ui.LoadingBox
import com.silkage.mygardenworld.core.ui.MyGardenWorldTheme
import com.silkage.mygardenworld.feature.accounts.AccountsScreen
import com.silkage.mygardenworld.feature.accounts.AccountsViewModel
import com.silkage.mygardenworld.feature.auth.LoginScreen
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
    val auth by container.auth.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (container.auth.state.value is AuthState.Restoring) container.auth.restore() }
    when (val state = auth) {
        is AuthState.Restoring -> LoadingBox("正在恢复登录…")
        is AuthState.SignedOut -> LoginScreen(container.auth, container.accounts, state.reason)
        is AuthState.SignedIn -> SignedInNav(container)
    }
}

@Composable
private fun SignedInNav(container: AppContainer) {
    val nav = rememberNavController()
    val serverLabel = container.auth.baseUrl?.removePrefix("https://")?.removePrefix("http://") ?: ""
    NavHost(nav, startDestination = "accounts") {
        composable("accounts") {
            val vm: AccountsViewModel = viewModel(factory = factory { AccountsViewModel(container) })
            AccountsScreen(vm, serverLabel, onOpenAccount = { nav.navigate("workspace/$it") }, onOpenSettings = { nav.navigate("settings") })
        }
        composable("workspace/{accountId}", arguments = listOf(navArgument("accountId") { type = NavType.LongType })) { entry ->
            val accountId = entry.arguments?.getLong("accountId") ?: 0L
            val vm: WorkspaceViewModel = viewModel(key = "workspace-$accountId", factory = factory { WorkspaceViewModel(container, accountId) })
            WorkspaceScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("settings") { SettingsScreen(container, onBack = { nav.popBackStack() }) }
    }
}

private inline fun <reified T : ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}
