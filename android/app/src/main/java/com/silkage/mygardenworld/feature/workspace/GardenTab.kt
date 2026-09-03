package com.silkage.mygardenworld.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mygardenworld.v1.LandView
import com.mygardenworld.v1.Policy
import com.silkage.mygardenworld.core.game.Catalog
import com.silkage.mygardenworld.core.protocol.WorkspaceUiState
import com.silkage.mygardenworld.core.ui.Badge
import com.silkage.mygardenworld.core.ui.BadgeTone
import com.silkage.mygardenworld.core.ui.CloudColors
import com.silkage.mygardenworld.core.ui.EmptyState
import com.silkage.mygardenworld.core.ui.Format
import com.silkage.mygardenworld.core.ui.SectionCard

@Composable
fun GardenTab(workspace: WorkspaceUiState, policy: Policy?, catalog: Catalog) {
    val state = workspace.state
    val garden = state?.takeIf { it.hasGarden() }?.garden
    val basic = state?.takeIf { it.hasBasic() }?.basic
    val lands = garden?.landsList.orEmpty()
    val byDisplay = lands.associateBy { Format.landDisplayNumber(it.landId) }
    val opened = lands.count { it.landStatus == "opened" }
    val unopened = lands.count { it.landStatus == "unopened" }
    val locked = lands.count { it.landStatus == "locked" }
    val minWater = policy?.plant?.planting?.minWaterDrops ?: 0
    val water = basic?.waterDrops ?: 0
    val counts = lands.filter { it.landStatus == "opened" }.groupingBy { it.recommendation.ifBlank { "unknown" } }.eachCount()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("土地", actions = {
                Badge("已开 $opened", BadgeTone.SECONDARY)
                if (unopened > 0) Badge("未开 $unopened")
                if (locked > 0) Badge("锁定 $locked")
            }) {
                if (lands.isEmpty()) EmptyState("暂无土地快照")
                else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("harvest", "plant", "water", "wait").forEach { key -> counts[key]?.takeIf { it > 0 }?.let { Badge("${Format.recommendation(key)} $it") } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Badge("水滴 ${Format.count(water)}/${Format.count(basic?.waterDropsTotal ?: 0)}")
                        if (minWater > 0) Badge("可用水滴 ${Format.count((water - minWater).coerceAtLeast(0))}")
                    }
                    listOf(1..32, 33..64).forEach { section ->
                        Text("地块 ${section.first}–${section.last}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        section.chunked(4).forEach { rowIds ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowIds.forEach { display ->
                                    val land = byDisplay[display]
                                    if (land != null) LandTile(land, catalog, Modifier.weight(1f))
                                    else Column(Modifier.weight(1f).heightIn(min = 72.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("#$display", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            val flowers = garden?.plantableFlowersList.orEmpty()
            SectionCard("可种植花朵", defaultOpen = false, actions = { Badge(flowers.size.toString(), BadgeTone.SECONDARY) }) {
                if (flowers.isEmpty()) EmptyState("暂无花朵库存快照")
                else flowers.sortedByDescending { it.stock }.forEach { flower ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(flower.flowerName.ifBlank { catalog.itemName(flower.flowerId) }, style = MaterialTheme.typography.bodySmall)
                            Text("${if (flower.lvl > 0) "${flower.lvl}级 · " else ""}金币 ${flower.gold} · 经验 ${flower.experience}" + if (flower.cdSeconds > 0) " · ${flower.cdSeconds / 60}分钟" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Badge("库存 ${Format.count(flower.stock)}", BadgeTone.SECONDARY)
                    }
                }
            }
        }
        item {
            val friends = garden?.friendTouchFriendsList.orEmpty()
            SectionCard("好友摸花", defaultOpen = false, actions = { Badge(if (garden?.friendTouchFriendsObserved == true) friends.size.toString() else "未同步", BadgeTone.SECONDARY) }) {
                if (friends.isEmpty()) EmptyState("暂无好友快照")
                else friends.forEach { friend ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(friend.name.ifBlank { friend.uid.toString() }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Badge(if (friend.quotaObserved) "剩余 ${friend.stealLeft}/${friend.stealMax}" else "未同步", if (friend.canSteal) BadgeTone.PRIMARY else BadgeTone.NEUTRAL)
                    }
                }
            }
        }
    }
}

@Composable
private fun LandTile(land: LandView, catalog: Catalog, modifier: Modifier) {
    val status = land.landStatus.ifBlank { if (land.observed) "opened" else "unknown" }
    val opened = status == "opened"
    val planted = land.flowerId > 0
    val scheme = MaterialTheme.colorScheme
    val (border, bg) = when {
        opened && land.recommendation == "harvest" -> scheme.primary.copy(alpha = 0.5f) to scheme.primary.copy(alpha = 0.08f)
        opened && land.recommendation == "water" -> CloudColors.Sky.copy(alpha = 0.7f) to CloudColors.Sky.copy(alpha = 0.1f)
        opened && land.recommendation == "plant" -> CloudColors.Amber.copy(alpha = 0.7f) to CloudColors.Amber.copy(alpha = 0.1f)
        else -> scheme.outlineVariant to scheme.surfaceContainer
    }
    Column(
        modifier
            .heightIn(min = 72.dp)
            .alpha(if (!opened || !land.observed) 0.7f else 1f)
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(Format.landDisplayName(land.landId), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            Badge(if (opened) Format.recommendation(land.recommendation) else Format.landStatus(status), if (opened && land.recommendation == "harvest") BadgeTone.SECONDARY else BadgeTone.NEUTRAL)
        }
        Text(if (opened) (if (planted) catalog.itemName(land.flowerId) else "空地") else Format.landStatus(status), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (opened) (if (land.lvl > 0) "${land.lvl}级" else "-") + (if (planted) " · 收${land.harvestCnt}" else "") else if (land.openLevel > 0) "${land.openLevel}级解锁" else "-",
            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(Format.landTiming(land, status), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
