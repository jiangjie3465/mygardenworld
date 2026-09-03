package com.silkage.mygardenworld.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silkage.mygardenworld.AppContainer
import com.silkage.mygardenworld.BuildConfig
import com.silkage.mygardenworld.core.auth.AuthState
import com.silkage.mygardenworld.core.ui.SectionCard
import com.silkage.mygardenworld.core.ui.SettingRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val workspace by container.workspace.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("服务") {
                SettingRow("服务地址") { Text(container.auth.baseUrl ?: "-", style = MaterialTheme.typography.bodySmall) }
                SettingRow("服务端版本") { Text(workspace.serverVersion.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall) }
                SettingRow("当前用户") { Text((auth as? AuthState.SignedIn)?.user?.username ?: "-", style = MaterialTheme.typography.bodySmall) }
                SettingRow("设备标识") { Text(container.auth.deviceId.take(8) + "…", style = MaterialTheme.typography.bodySmall) }
                SettingRow("App 版本") { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})", style = MaterialTheme.typography.bodySmall) }
            }
            Button(
                onClick = { busy = true; scope.launch { container.auth.logout(); busy = false } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text("退出登录") }
        }
    }
}
