package com.silkage.mygardenworld.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.silkage.mygardenworld.BuildConfig
import com.silkage.mygardenworld.core.auth.AuthSession
import com.silkage.mygardenworld.core.network.ConnectCode
import com.silkage.mygardenworld.core.network.ConnectException
import com.silkage.mygardenworld.core.network.normalizeApiBaseUrl
import com.silkage.mygardenworld.core.ui.ErrorBanner
import com.silkage.mygardenworld.feature.accounts.AccountsRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(session: AuthSession, accounts: AccountsRepository, initialMessage: String?) {
    val scope = rememberCoroutineScope()
    var baseUrl by rememberSaveable { mutableStateOf(session.baseUrl ?: "https://") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(initialMessage.orEmpty()) }
    var info by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Text("小花园", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("连接独立部署的 gardend", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            OutlinedTextField(
                baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(),
                label = { Text(if (BuildConfig.ALLOW_INSECURE_ENDPOINTS) "服务地址（debug 允许 http）" else "HTTPS 服务地址") },
                placeholder = { Text("https://garden.example.com") },
                singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("用户名") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth().padding(top = 10.dp),
                label = { Text("密码") }, singleLine = true, enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            if (error.isNotBlank()) ErrorBanner(error, Modifier.padding(top = 12.dp))
            if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        error = ""; info = "正在测试连接…"; busy = true
                        scope.launch {
                            info = testConnection(session, accounts, baseUrl)
                            busy = false
                        }
                    },
                    enabled = !busy && baseUrl.length > 8,
                    modifier = Modifier.weight(1f),
                ) { Text("测试连接") }
                Button(
                    onClick = {
                        error = ""; info = ""; busy = true
                        scope.launch {
                            try {
                                session.login(baseUrl, username, password)
                            } catch (e: ConnectException) {
                                error = e.userMessage
                            } catch (e: IllegalArgumentException) {
                                error = e.message ?: "服务地址无效"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(if (busy) "处理中…" else "登录") }
            }
        }
    }
}

/**
 * Probes GetMe without a token. gardend answers `unauthenticated`, which proves
 * the address, TLS chain, and Connect routing all work.
 */
private suspend fun testConnection(session: AuthSession, accounts: AccountsRepository, raw: String): String {
    val base = try {
        normalizeApiBaseUrl(raw)
    } catch (e: IllegalArgumentException) {
        return e.message ?: "服务地址无效"
    }
    val previous = session.rpc.baseUrl
    session.rpc.baseUrl = base
    return try {
        accounts.me()
        "连接正常"
    } catch (e: ConnectException) {
        when {
            e.code == ConnectCode.UNAUTHENTICATED -> "连接正常，服务端可达"
            e.isNetworkFailure -> "无法连接：${e.userMessage}"
            e.code == ConnectCode.UNIMPLEMENTED -> "地址可达，但不是 gardend 服务"
            else -> "连接异常：${e.userMessage}"
        }
    } finally {
        session.rpc.baseUrl = previous ?: base
    }
}
