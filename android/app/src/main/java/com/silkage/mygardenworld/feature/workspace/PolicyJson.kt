package com.silkage.mygardenworld.feature.workspace

import com.google.protobuf.InvalidProtocolBufferException
import com.mygardenworld.v1.Policy
import com.silkage.mygardenworld.core.protocol.ProtoJson
import com.silkage.mygardenworld.core.protocol.ProtoJsonException

/** Whole-policy JSON share format, interchangeable with the Web export. */
object PolicyJson {
    private const val POLICY_TYPE = ".mygardenworld.v1.Policy"
    private const val MAX_CHARS = 1024 * 1024

    fun export(codec: ProtoJson, policy: Policy): String = codec.toJson(POLICY_TYPE, policy.toByteArray())

    /** Throws [ProtoJsonException] with a user-facing message when the text is not a complete current policy. */
    fun import(codec: ProtoJson, text: String, current: Policy): Policy {
        if (text.length > MAX_CHARS) throw ProtoJsonException("配置 JSON 不能超过 1 MB")
        val policy = try {
            Policy.parseFrom(codec.fromJson(POLICY_TYPE, text))
        } catch (_: InvalidProtocolBufferException) {
            throw ProtoJsonException("JSON 配置无效")
        }
        if (policy.schemaVersion != current.schemaVersion) throw ProtoJsonException("配置版本与当前版本不一致，请使用当前版本导出的 JSON")
        val missing = listOf(
            "basic" to policy.hasBasic(),
            "plant" to policy.hasPlant(),
            "order" to policy.hasOrder(),
            "union" to policy.hasUnion(),
            "activity" to policy.hasActivity(),
        ).firstOrNull { !it.second }
        if (missing != null) throw ProtoJsonException("缺少 ${missing.first} 配置，请导入完整配置 JSON")
        // Account start/pause is a lifecycle command, not an import side effect.
        return policy.toBuilder().setAutomationEnabled(current.automationEnabled).build()
    }
}
