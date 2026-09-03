package com.silkage.mygardenworld.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class BadgeTone { NEUTRAL, PRIMARY, SECONDARY, WARNING, DANGER, SUCCESS }

@Composable
fun Badge(text: String, tone: BadgeTone = BadgeTone.NEUTRAL, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        BadgeTone.NEUTRAL -> Color.Transparent to scheme.onSurfaceVariant
        BadgeTone.PRIMARY -> scheme.primary to scheme.onPrimary
        BadgeTone.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        BadgeTone.WARNING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        BadgeTone.DANGER -> scheme.error.copy(alpha = 0.14f) to scheme.error
        BadgeTone.SUCCESS -> CloudColors.Green.copy(alpha = 0.16f) to if (isDark()) CloudColors.Green else Color(0xFF0F6E4E)
    }
    val border = if (tone == BadgeTone.NEUTRAL) scheme.outline else Color.Transparent
    Text(
        text,
        modifier = modifier
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Collapsible card mirroring the Web CollapsibleCard. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    defaultOpen: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(defaultOpen) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (open) "收起" else "展开",
                modifier = Modifier.size(18.dp).rotate(if (open) 0f else -90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        AnimatedVisibility(visible = open) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

@Composable
fun StatTile(icon: ImageVector, label: String, value: String, detail: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = CloudColors.Sky) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun EmptyState(title: String, detail: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("✦", color = CloudColors.Amber)
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
    }
}

@Composable
fun LoadingBox(text: String = "加载中…", modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/** Shared setting row: label + optional hint on the left, control on the right. */
@Composable
fun SettingRow(label: String, hint: String? = null, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (!hint.isNullOrBlank()) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, hint: String? = null, onChange: (Boolean) -> Unit) {
    SettingRow(label, hint) { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) }
}

@Composable
fun NumberRow(label: String, value: Long, enabled: Boolean = true, hint: String? = null, onChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    SettingRow(label, hint) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() || it == '-' }.take(12)
                text = filtered
                filtered.toLongOrNull()?.let(onChange)
            },
            modifier = Modifier.width(112.dp),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}
