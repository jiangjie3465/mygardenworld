package com.silkage.mygardenworld.feature.notifications

import com.mygardenworld.v1.NotificationProvider
import com.mygardenworld.v1.SaveNotificationSettingsRequest
import com.mygardenworld.v1.SaveNotificationSettingsResponse
import com.mygardenworld.v1.TestNotificationRequest
import com.mygardenworld.v1.TestNotificationResponse
import com.silkage.mygardenworld.core.network.ConnectClient

/**
 * Connect commands for the signed-in user's own notification channel. Reads
 * arrive over the workspace socket (LoadUserNotifications).
 */
class NotificationsRepository(private val rpc: ConnectClient) {
    /**
     * A null [endpoint]/[signingSecret] keeps the saved value (same provider
     * only); an empty string clears it.
     */
    suspend fun save(enabled: Boolean, provider: NotificationProvider, cooldownMinutes: Int, endpoint: String?, signingSecret: String?) {
        val request = SaveNotificationSettingsRequest.newBuilder().setEnabled(enabled).setProvider(provider).setCooldownMinutes(cooldownMinutes)
        if (endpoint != null) request.setEndpoint(endpoint)
        if (signingSecret != null) request.setSigningSecret(signingSecret)
        rpc.call("NotificationService", "SaveNotificationSettings", request.build(), SaveNotificationSettingsResponse.parser())
    }

    suspend fun test(): Long = rpc.call("NotificationService", "TestNotification", TestNotificationRequest.getDefaultInstance(), TestNotificationResponse.parser()).deliveryId
}
