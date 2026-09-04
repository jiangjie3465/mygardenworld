package com.silkage.mygardenworld.feature.redeem

import com.google.protobuf.Timestamp
import com.mygardenworld.v1.Channel
import com.mygardenworld.v1.GetExchangeInfoRequest
import com.mygardenworld.v1.GetExchangeInfoResponse
import com.mygardenworld.v1.ListRedeemCodesRequest
import com.mygardenworld.v1.ListRedeemCodesResponse
import com.mygardenworld.v1.RedeemCode
import com.mygardenworld.v1.RedeemCodeSubmission
import com.mygardenworld.v1.RedeemSubmitResult
import com.mygardenworld.v1.RedeemValidation
import com.mygardenworld.v1.SubmitRedeemCodesRequest
import com.mygardenworld.v1.SubmitRedeemCodesResponse
import com.silkage.mygardenworld.core.network.ConnectClient

/** Redeem exchange center: shared code list plus submission. */
class RedeemRepository(private val rpc: ConnectClient) {
    suspend fun info(): GetExchangeInfoResponse =
        rpc.call("RedeemExchangeService", "GetExchangeInfo", GetExchangeInfoRequest.getDefaultInstance(), GetExchangeInfoResponse.parser())

    /** Follows cursors like the Web page and returns codes newest first. */
    suspend fun listAll(includeExpired: Boolean = true): List<RedeemCode> {
        val byFingerprint = LinkedHashMap<String, RedeemCode>()
        var cursor = ""
        repeat(20) {
            val response: ListRedeemCodesResponse = rpc.call(
                "RedeemExchangeService", "ListRedeemCodes",
                ListRedeemCodesRequest.newBuilder().setCursor(cursor).setPageSize(500).setIncludeExpired(includeExpired).build(),
                ListRedeemCodesResponse.parser(),
            )
            response.entriesList.forEach { byFingerprint[it.fingerprint] = it }
            if (response.nextCursor.isBlank() || response.nextCursor == cursor || response.entriesCount < 500) return@repeat
            cursor = response.nextCursor
        }
        return byFingerprint.values.sortedByDescending { it.firstSeenAt.seconds }
    }

    suspend fun submit(code: String, channels: List<Channel>, expiresAt: Timestamp?): List<RedeemSubmitResult> {
        val request = SubmitRedeemCodesRequest.newBuilder()
        channels.forEach { channel ->
            val entry = RedeemCodeSubmission.newBuilder()
                .setCode(code)
                .setChannel(channel)
                .setPermanent(expiresAt == null)
                .setReportedValidation(RedeemValidation.REDEEM_VALIDATION_PENDING)
            if (expiresAt != null) entry.setExpiresAt(expiresAt)
            request.addEntries(entry)
        }
        return rpc.call("RedeemExchangeService", "SubmitRedeemCodes", request.build(), SubmitRedeemCodesResponse.parser()).resultsList
    }
}
