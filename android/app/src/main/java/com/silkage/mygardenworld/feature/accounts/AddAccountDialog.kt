package com.silkage.mygardenworld.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.AlipayLoginStatus
import com.silkage.mygardenworld.core.ui.ErrorBanner
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.QrCodeImage

@Composable
fun AddAccountDialog(
    state: AccountsUiState,
    onDismiss: () -> Unit,
    onCreateIos: (String, String) -> Unit,
    onStartAlipay: () -> Unit,
    onClearQr: () -> Unit,
) {
    var alipay by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val qr = state.qr
    val terminal = qr != null && (qr.status == AlipayLoginStatus.ALIPAY_LOGIN_STATUS_EXPIRED || qr.status == AlipayLoginStatus.ALIPAY_LOGIN_STATUS_FAILED)

    AlertDialog(
        onDismissRequest = { if (!state.creating) onDismiss() },
        title = { Text("新增账号") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !alipay, onClick = { alipay = false; onClearQr() }, label = { Text("iOS") }, enabled = !state.creating)
                    FilterChip(selected = alipay, onClick = { alipay = true }, label = { Text("Alipay") }, enabled = !state.creating)
                }
                if (state.error.isNotBlank()) ErrorBanner(state.error)
                if (alipay) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (qr != null && qr.content.isNotBlank()) {
                            QrCodeImage(qr.content, Modifier.size(168.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp))
                            Text(Format.alipayStatus(qr.status), style = MaterialTheme.typography.bodyMedium)
                            if (qr.error.isNotBlank()) Text(qr.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (terminal) OutlinedButton(onClick = onStartAlipay, enabled = !state.creating) { Text("刷新二维码") }
                        } else {
                            Text("使用 Alipay 扫码后将自动获取游戏账号。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onStartAlipay, enabled = !state.creating) { Text(if (state.creating) "获取中…" else "获取二维码") }
                        }
                    }
                } else {
                    OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("账号") }, singleLine = true, enabled = !state.creating)
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true, enabled = !state.creating, visualTransformation = PasswordVisualTransformation())
                }
            }
        },
        confirmButton = {
            if (!alipay) Button(onClick = { onCreateIos(username, password) }, enabled = !state.creating && username.isNotBlank() && password.isNotBlank()) { Text(if (state.creating) "处理中…" else "新增") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.creating) { Text("取消") } },
    )
}
