package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.repository.NotificationHistoryRepository

class ObserveNotificationHistory(private val repository: NotificationHistoryRepository) {
    operator fun invoke() = repository.observe()
}

class ManageNotificationHistory(private val repository: NotificationHistoryRepository) {
    suspend fun markRead(id: String, at: Long) = repository.markRead(id, at)
    suspend fun markOpened(id: String, at: Long) = repository.markOpened(id, at)
    suspend fun markAllRead(at: Long) = repository.markAllRead(at)
}
