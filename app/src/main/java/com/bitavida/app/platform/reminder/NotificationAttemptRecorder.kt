package com.bitavida.app.platform.reminder

import android.content.Context
import com.bitavida.app.data.notifications.EncryptedNotificationHistoryRepository
import com.bitavida.app.domain.repository.NotificationHistoryItem
import com.bitavida.app.domain.repository.NotificationPublicationResult
import java.util.UUID
import kotlinx.coroutines.CancellationException

internal fun notificationChannelAvailable(manager: android.app.NotificationManager, channelId: String): Boolean {
    val channel = manager.getNotificationChannel(channelId) ?: return false
    if (channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) return false
    val groupId = channel.group
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && groupId != null &&
        manager.getNotificationChannelGroup(groupId)?.isBlocked == true) return false
    return true
}

/** Best effort: a storage failure must not suppress an otherwise deliverable reminder. */
internal suspend fun recordNotificationAttempt(
    context: Context,
    module: String,
    title: String,
    message: String,
    token: String?,
    result: NotificationPublicationResult,
) {
    try {
        val profile = com.bitavida.app.data.careprofile.EncryptedCareProfileRepository(context).get()
            ?.takeIf { it.consent.isCurrent } ?: return
        EncryptedNotificationHistoryRepository(context, profile.id).record(
            NotificationHistoryItem(UUID.randomUUID().toString(), module, title, message,
                System.currentTimeMillis(), result, token, null, null),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // The protected store may be unavailable. Do not log clinical data or fabricate receipts.
    }
}
