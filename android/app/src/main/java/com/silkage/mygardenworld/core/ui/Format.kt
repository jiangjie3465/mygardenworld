package com.silkage.mygardenworld.core.ui

import com.google.protobuf.Timestamp
import com.mygardenworld.v1.Account
import com.mygardenworld.v1.AccountHealth
import com.mygardenworld.v1.AccountStatus
import com.mygardenworld.v1.AlipayLoginStatus
import com.mygardenworld.v1.Channel
import com.mygardenworld.v1.Event
import com.mygardenworld.v1.LandView
import com.mygardenworld.v1.PlanStatus
import com.mygardenworld.v1.PlannedOperation
import com.mygardenworld.v1.WorkspaceLogCategory
import com.silkage.mygardenworld.core.game.Catalog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formatting and label helpers ported from web/src/components/dashboard/dashboard-utils.tsx. */
object Format {
    private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.CHINA)
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val dayClockFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    fun count(value: Number): String = synchronized(numberFormat) { numberFormat.format(value.toLong()) }

    fun timestamp(ts: Timestamp?): String {
        if (ts == null || (ts.seconds == 0L && ts.nanos == 0)) return "-"
        val ms = ts.seconds * 1000 + ts.nanos / 1_000_000
        if (ms <= 0) return "-"
        return synchronized(timestampFormat) { timestampFormat.format(Date(ms)) }
    }

    fun clock(ms: Long): String = if (ms <= 0) "-" else synchronized(clockFormat) { clockFormat.format(Date(ms)) }

    fun dayClock(ms: Long): String = if (ms <= 0) "-" else synchronized(dayClockFormat) { dayClockFormat.format(Date(ms)) }

    fun dayId(dayId: Int): String {
        if (dayId < 20000101 || dayId > 21001231) return if (dayId != 0) dayId.toString() else "-"
        return "%d-%02d-%02d".format(dayId / 10000, (dayId % 10000) / 100, dayId % 100)
    }

    fun remaining(ms: Long): String {
        val seconds = ((ms - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
        if (seconds < 60) return "${seconds}秒"
        val minutes = (seconds + 59) / 60
        if (minutes < 60) return "${minutes}分钟"
        return "${(minutes + 59) / 60}小时"
    }

    private val areaSuffix = Regex("""\s*·?\s*第\s*\d+\s*区(?:\s*#\d+)?\s*$""")
    private val serverPrefix = Regex("""^s\d{2,}[.．·\s_-]+""", RegexOption.IGNORE_CASE)
    private val areaInName = Regex("""第\s*(\d+)\s*区""")
    private val serverInName = Regex("""^s(\d{2,})[.．·\s_-]+""", RegexOption.IGNORE_CASE)

    fun accountNickname(account: Account): String {
        val withoutArea = account.name.replace(areaSuffix, "").trim()
        val withoutServer = withoutArea.replace(serverPrefix, "").trim()
        return withoutServer.ifEmpty { withoutArea.ifEmpty { account.name.ifEmpty { "账号" } } }
    }

    fun accountArea(account: Account, status: AccountStatus?): String {
        val gsIdx = status?.gsIdx?.takeIf { it > 0 } ?: account.gsIdx
        if (gsIdx > 0) return "第${gsIdx}区"
        areaInName.find(account.name)?.let { return "第${it.groupValues[1]}区" }
        serverInName.find(account.name)?.let { return "第${it.groupValues[1]}区" }
        return "未知区"
    }

    fun channel(channel: Channel): String = when (channel) {
        Channel.CHANNEL_IOS -> "iOS"
        Channel.CHANNEL_ALIPAY -> "Alipay"
        else -> "未知渠道"
    }

    fun alipayStatus(status: AlipayLoginStatus): String = when (status) {
        AlipayLoginStatus.ALIPAY_LOGIN_STATUS_WAITING_FOR_SCAN -> "等待 Alipay 扫码确认"
        AlipayLoginStatus.ALIPAY_LOGIN_STATUS_PROCESSING -> "正在验证游戏登录…"
        AlipayLoginStatus.ALIPAY_LOGIN_STATUS_COMPLETE -> "绑定完成"
        AlipayLoginStatus.ALIPAY_LOGIN_STATUS_EXPIRED -> "二维码已过期"
        AlipayLoginStatus.ALIPAY_LOGIN_STATUS_FAILED -> "登录验证失败"
        else -> "准备扫码"
    }

    fun accountConnected(account: Account?, status: AccountStatus?): Boolean = status?.connected ?: (account?.connected ?: false)

    fun statusIssues(status: AccountStatus?): List<String> {
        if (status == null) return emptyList()
        val d = status.diagnostics
        val issues = LinkedHashSet<String>()
        listOf(status.lastError, d.lastOperationError, d.sessionInvalidatedReason).forEach { if (it.isNotBlank()) issues += it.trim() }
        d.blockedReasonsList.forEach { if (it.isNotBlank()) issues += it.trim() }
        if (status.health == AccountHealth.ACCOUNT_HEALTH_BLOCKED && issues.isEmpty()) issues += "账号处于异常状态，但后端未返回具体原因。"
        return issues.toList()
    }

    fun accountAbnormal(status: AccountStatus?): Boolean {
        if (status == null) return false
        if (statusIssues(status).isNotEmpty()) return true
        return status.health == AccountHealth.ACCOUNT_HEALTH_BLOCKED ||
            status.health == AccountHealth.ACCOUNT_HEALTH_SESSION_EXPIRED ||
            status.lastError.isNotBlank()
    }

    /** Returns label and tone for the health badge. */
    fun healthBadge(account: Account?, status: AccountStatus?): Pair<String, BadgeTone> = when {
        accountAbnormal(status) -> "异常" to BadgeTone.DANGER
        !accountConnected(account, status) -> "离线" to BadgeTone.NEUTRAL
        else -> "在线" to BadgeTone.SUCCESS
    }

    fun planStatus(status: PlanStatus): String = when (status) {
        PlanStatus.PLAN_STATUS_READY -> "可执行"
        PlanStatus.PLAN_STATUS_MANAGED -> "调度"
        PlanStatus.PLAN_STATUS_SYNC_ONLY -> "同步"
        PlanStatus.PLAN_STATUS_ADAPTER_MISSING -> "缺适配"
        PlanStatus.PLAN_STATUS_BLOCKED -> "阻塞"
        PlanStatus.PLAN_STATUS_SKIPPED -> "跳过"
        else -> "等待"
    }

    fun operationCooling(op: PlannedOperation): Boolean = op.cooldownUntilMs > System.currentTimeMillis()

    fun operationBadge(op: PlannedOperation): Pair<String, BadgeTone> = when {
        operationCooling(op) -> "冷却" to BadgeTone.SECONDARY
        op.status == PlanStatus.PLAN_STATUS_BLOCKED || op.blockedReasonsCount > 0 -> "阻塞" to BadgeTone.DANGER
        op.syncOnly -> "同步" to BadgeTone.NEUTRAL
        !op.executable -> planStatus(op.status) to BadgeTone.NEUTRAL
        op.status == PlanStatus.PLAN_STATUS_MANAGED -> "调度" to BadgeTone.SECONDARY
        else -> "可执行" to BadgeTone.PRIMARY
    }

    fun operationTitle(op: PlannedOperation): String =
        op.label.ifBlank { actionLabel(op.action).ifBlank { op.domain.ifBlank { op.rpc.ifBlank { "操作" } } } }

    fun operationTarget(op: PlannedOperation, catalog: Catalog): String {
        val landIds = operationLandIds(op)
        if (landIds.isNotEmpty()) return landIds.joinToString("、") { landDisplayName(it) }
        if (op.rpc == "flowerArt.makeFlowerArt") {
            val art = if (op.itemId != 0) catalog.itemName(op.itemId) else "花艺"
            val count = if (op.count != 0) "x${op.count}" else ""
            val prefix = if (op.domain == "order.customer" && op.targetId != 0) "NPC ${op.targetId}" else ""
            return listOf(prefix, art, count).filter { it.isNotBlank() }.joinToString(" ")
        }
        if (op.rpc == "orderCustomer.finishOrder" || op.rpc == "orderCustomer.rejectOrder") return if (op.targetId != 0) "NPC ${op.targetId}" else ""
        if (op.targetUid != 0L) return "UID ${op.targetUid}" + if (op.targetId != 0) " · 槽位 ${op.targetId}" else ""
        if (op.targetUidsCount > 0) return "${op.targetUidsCount} 个候选 UID"
        return listOf(
            if (op.targetId != 0) targetIdLabel(op) else "",
            if (op.itemId != 0) catalog.itemName(op.itemId) else "",
            if (op.flowerId != 0) catalog.itemName(op.flowerId) else "",
            if (op.count != 0) "x${op.count}" else "",
        ).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun operationCost(op: PlannedOperation, catalog: Catalog): String {
        if (op.costGatesCount > 0) {
            val gates = op.costGatesList.filter { it.required > 0 }.map { gate ->
                val label = gate.label.ifBlank { if (gate.itemId != 0) catalog.itemName(gate.itemId) else "成本" }
                val availability = if (gate.available > 0 || gate.status == PlanStatus.PLAN_STATUS_BLOCKED) "/${gate.available}" else ""
                "$label ${gate.required}$availability"
            }
            if (gates.isNotEmpty()) return "成本 ${gates.joinToString("、")}"
        }
        val costs = buildList {
            if (op.goldCost != 0) add("金币 ${op.goldCost}")
            if (op.diamondCost != 0) add("元宝 ${op.diamondCost}")
            op.itemCostMap.filter { it.value > 0 }.forEach { (id, count) -> add("${catalog.itemName(id)}x$count") }
        }
        return if (costs.isEmpty()) "" else "成本 ${costs.joinToString("、")}"
    }

    fun operationNote(op: PlannedOperation): String {
        if (operationCooling(op)) return "${op.cooldownReason.ifBlank { "操作冷却中" }}，${remaining(op.cooldownUntilMs)}后重试"
        val raw = if (op.blockedReasonsCount > 0) op.blockedReasonsList.joinToString("、") else op.reason
        return reasonLabel(raw)
    }

    private fun operationLandIds(op: PlannedOperation): List<Int> {
        if (op.landIdsCount > 0) return op.landIdsList
        if ((op.domain.startsWith("farm.") || op.rpc.startsWith("usrLand.")) && op.targetId > 0) return listOf(op.targetId)
        return emptyList()
    }

    private fun targetIdLabel(op: PlannedOperation): String = when {
        op.domain == "order.customer" -> "NPC ${op.targetId}"
        op.domain.startsWith("order.flower_art") -> "花架 ${op.targetId}"
        op.domain.startsWith("union.") -> "目标 ${op.targetId}"
        else -> "#${op.targetId}"
    }

    fun actionLabel(action: String): String = when (action) {
        "harvest" -> "收获"
        "plant" -> "种植"
        "water" -> "浇水"
        "finish", "submit" -> "提交"
        "reject" -> "暂时无货"
        "claim", "recv" -> "领取"
        "craft" -> "制作"
        "sell" -> "上架"
        "sync" -> "同步"
        "buy" -> "购买"
        "unlock" -> "解锁"
        "cultivate" -> "培育"
        "feed" -> "喂食"
        "stroke" -> "互动"
        "find_pet" -> "寻回"
        "handle_event" -> "处理"
        else -> action
    }

    fun reasonLabel(reason: String): String = when {
        reason.isBlank() -> ""
        reason == "ready land" || reason.contains("initial bloom ready") || reason.contains("elapsed") -> "可收获"
        reason == "land is empty" -> "空地"
        reason.contains("awaiting first water") -> "待浇水"
        reason.contains("regrowing") -> "成长中"
        reason.contains("not actionable") -> "等待"
        reason.contains("no observed") -> "未同步"
        else -> reason
    }

    fun recommendation(value: String): String = when (value) {
        "harvest" -> "可采收"
        "plant" -> "可种植"
        "water" -> "可浇水"
        "wait" -> "等待"
        "unlock" -> "待开"
        "locked" -> "锁定"
        "unknown", "" -> "未知"
        else -> value
    }

    fun landStatus(status: String): String = when (status) {
        "opened" -> "已开"
        "unopened" -> "未开"
        "locked" -> "锁定"
        "" -> "未知"
        else -> status
    }

    fun landDisplayNumber(landId: Int): Int = if (landId in 1001..1999) landId - 1000 else landId

    fun landDisplayName(landId: Int): String = "#${landDisplayNumber(landId)}"

    fun landTiming(land: LandView, status: String): String {
        when (land.recommendation) {
            "harvest" -> return "可收获"
            "water" -> return "待浇水"
            "plant" -> return "待种植"
        }
        if (status != "opened") return landStatus(status)
        val next = clock(land.nextTimeMs)
        if (next != "-") return "成熟 $next"
        return if (land.flowerId > 0) "成长中" else "待同步"
    }

    fun eventTitle(event: Event): String {
        if (event.label.isNotBlank()) return event.label
        return when {
            event.kind == "order_satin_finish" -> "绸缎订单"
            event.kind == "order_decorate_finish" -> "建材订单"
            event.kind == "waterwheel" -> "水车水滴"
            event.kind == "free_water" -> "限时水滴"
            event.domain.contains("resident.satin") -> "绸缎订单"
            event.domain.contains("resident.decorate") -> "建材订单"
            else -> listOf(event.domain, event.action).filter { it.isNotBlank() }.joinToString(".").ifBlank { event.kind.ifBlank { "-" } }
        }
    }

    fun eventMessage(event: Event): String = event.message.ifBlank { event.payloadJson }

    fun logCategory(category: WorkspaceLogCategory): String = when (category) {
        WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_ACCOUNT -> "账号"
        WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_BASIC -> "基础"
        WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_GARDEN -> "花园"
        WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_ORDERS -> "订单"
        WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_UNION -> "公会"
        WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_ACTIVITIES -> "活动"
        WorkspaceLogCategory.WORKSPACE_LOG_CATEGORY_WAREHOUSE -> "仓库"
        else -> "系统"
    }
}
