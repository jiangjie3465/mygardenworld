package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/** Web "重新登录／更新凭据": keeps policy, history and run/pause intent. */
@Composable
fun ReauthDialog(viewModel: WorkspaceViewModel, screen: WorkspaceScreenState, online: Boolean, onDismiss: () -> Unit) {
    val account = screen.account
    val alipay = viewModel.isAlipay
    var password by rememberSaveable { mutableStateOf("") }
    val qr = screen.reauthQr
    val terminal = qr != null && (qr.status == AlipayLoginStatus.ALIPAY_LOGIN_STATUS_EXPIRED || qr.status == AlipayLoginStatus.ALIPAY_LOGIN_STATUS_FAILED)
    val close = { viewModel.closeReauth(); onDismiss() }
    LaunchedEffect(screen.reauthDone) { if (screen.reauthDone) close() }

    AlertDialog(
        onDismissRequest = { if (!screen.reauthBusy) close() },
        title = { Text("重新登录") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    (account?.let(Format::accountNickname) ?: "账号") + "：更新登录凭据，保留配置、历史和运行／暂停设置。" + if (alipay) "请使用原支付宝账号扫码。" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (screen.reauthError.isNotBlank()) ErrorBanner(screen.reauthError)
                if (alipay) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (qr != null && qr.content.isNotBlank()) {
                            QrCodeImage(qr.content, Modifier.size(168.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp))
                            Text(Format.alipayStatus(qr.status), style = MaterialTheme.typography.bodyMedium)
                            if (qr.error.isNotBlank()) Text(qr.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (terminal) OutlinedButton(onClick = viewModel::startAlipayReauth, enabled = online && !screen.reauthBusy) { Text("刷新二维码") }
                        } else {
                            Button(onClick = viewModel::startAlipayReauth, enabled = online && !screen.reauthBusy) { Text(if (screen.reauthBusy) "获取中…" else "获取二维码") }
                        }
                    }
                } else {
                    OutlinedTextField(account?.username.orEmpty(), {}, Modifier.fillMaxWidth(), label = { Text("账号") }, singleLine = true, enabled = false)
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true, enabled = !screen.reauthBusy, visualTransformation = PasswordVisualTransformation())
                }
            }
        },
        confirmButton = {
            if (!alipay) {
                Button(onClick = { viewModel.reauthenticate(password) }, enabled = online && !screen.reauthBusy && password.isNotBlank()) { Text(if (screen.reauthBusy) "处理中…" else "验证并重新登录") }
            }
        },
        dismissButton = { TextButton(onClick = close, enabled = !screen.reauthBusy) { Text("取消") } },
    )
}
