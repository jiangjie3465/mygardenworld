package com.silkage.mygardenworld.feature.workspace

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.Policy
import com.silkage.mygardenworld.core.protocol.ProtoJson
import com.silkage.mygardenworld.core.protocol.ProtoJsonException

/** Web PolicyJSONDialog: export the editor policy, or validate an import before applying it to the editor. */
@Composable
fun PolicyJsonDialog(export: Boolean, policy: Policy, codec: ProtoJson?, enabled: Boolean, onImport: (Policy) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    val exported = remember(policy, codec) { if (export && codec != null) PolicyJson.export(codec, policy) else "" }
    var text by remember { mutableStateOf(exported) }
    var message by remember { mutableStateOf(if (codec == null) "配置格式描述未加载，无法处理 JSON" else "") }
    var messageOk by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Policy?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (export) "导出账号配置" else "导入账号配置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("包含所有模块的完整配置，不包含账号密码或登录凭据。导入会替换全部模块设置，账号运行／暂停状态保持不变。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (export) Text("导出当前编辑器中的配置，包含尚未保存的修改。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; preview = null; message = "" },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    label = { Text("配置 JSON") },
                    readOnly = export,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = if (messageOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                preview?.let { p ->
                    Text(
                        "完整配置校验通过，应用到编辑器后点击“保存”生效。\n自动挤号：${if (p.basic.displacedSessionReloginEnabled) "开启" else "关闭"}；竞赛自动升级：${if (p.union.race.upgradeTask) "开启" else "关闭"}；单次元宝上限：${p.union.race.maxSpendDiamond}",
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (export) {
                TextButton(onClick = {
                    clipboard?.setPrimaryClip(ClipData.newPlainText("配置 JSON", text))
                    messageOk = clipboard != null
                    message = if (clipboard != null) "已复制完整配置 JSON" else "无法自动复制，请在文本框中全选并复制"
                }, enabled = text.isNotBlank()) { Text("复制 JSON") }
            } else {
                TextButton(
                    enabled = enabled && codec != null && text.isNotBlank(),
                    onClick = {
                        val validated = preview
                        if (validated != null) {
                            onImport(validated)
                            return@TextButton
                        }
                        try {
                            preview = PolicyJson.import(codec!!, text, policy)
                            message = ""
                        } catch (e: ProtoJsonException) {
                            messageOk = false
                            message = e.message ?: "JSON 配置无效"
                        }
                    },
                ) { Text(if (preview != null) "应用到编辑器" else "校验配置") }
            }
        },
        dismissButton = {
            Column {
                if (!export) {
                    TextButton(onClick = {
                        val pasted = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (pasted.isBlank()) { messageOk = false; message = "剪贴板中没有可用内容" } else { text = pasted; preview = null; message = "" }
                    }) { Text("从剪贴板粘贴") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
