package com.bitavida.app.data.notifications

import android.content.Context
import com.bitavida.app.data.local.secure.NotificationDeliveryEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.domain.repository.NotificationHistoryItem
import com.bitavida.app.domain.repository.NotificationHistoryRepository
import com.bitavida.app.domain.repository.NotificationPublicationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class EncryptedNotificationHistoryRepository(context: Context, private val profileId: String) : NotificationHistoryRepository {
    private val appContext = context.applicationContext

    override fun observe(): Flow<List<NotificationHistoryItem>> = flow {
        val dao = SecureDatabaseProvider.open(appContext).notificationDeliveryDao()
        emitAll(dao.observe(profileId).map { rows ->
            rows.mapNotNull { row ->
                val result = NotificationPublicationResult.entries.firstOrNull { it.name == row.result }
                    ?: return@mapNotNull null
                NotificationHistoryItem(row.id, row.module, row.title, row.message,
                    row.attemptedAtEpochMillis, result, row.relatedToken, row.readAtEpochMillis, row.openedAtEpochMillis)
            }
        })
    }

    override suspend fun record(item: NotificationHistoryItem) {
        SecureDatabaseProvider.open(appContext).notificationDeliveryDao().insert(
            NotificationDeliveryEntity(item.id, profileId, item.module, item.title, item.message,
                item.attemptedAtEpochMillis, item.result.name, item.relatedToken,
                item.readAtEpochMillis, item.openedAtEpochMillis),
        )
    }

    override suspend fun markRead(id: String, at: Long) =
        SecureDatabaseProvider.open(appContext).notificationDeliveryDao().markRead(profileId, id, at)

    override suspend fun markOpened(id: String, at: Long) =
        SecureDatabaseProvider.open(appContext).notificationDeliveryDao().markOpened(profileId, id, at)

    override suspend fun markAllRead(at: Long) =
        SecureDatabaseProvider.open(appContext).notificationDeliveryDao().markAllRead(profileId, at)
}
