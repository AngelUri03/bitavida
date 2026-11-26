package com.bitavida.app.domain.repository

import kotlinx.coroutines.flow.Flow

/** A publication attempt by this application, never evidence that a person saw an alert. */
data class NotificationHistoryItem(
    val id: String,
    val module: String,
    val title: String,
    val message: String,
    val attemptedAtEpochMillis: Long,
    val result: NotificationPublicationResult,
    val relatedToken: String?,
    val readAtEpochMillis: Long?,
    val openedAtEpochMillis: Long?,
)

enum class NotificationPublicationResult { PUBLISHED, BLOCKED, FAILED }

interface NotificationHistoryRepository {
    fun observe(): Flow<List<NotificationHistoryItem>>
    suspend fun record(item: NotificationHistoryItem)
    suspend fun markRead(id: String, at: Long)
    suspend fun markOpened(id: String, at: Long)
    suspend fun markAllRead(at: Long)
}
