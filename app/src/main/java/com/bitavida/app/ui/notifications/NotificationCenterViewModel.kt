package com.bitavida.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.repository.NotificationHistoryItem
import com.bitavida.app.domain.repository.NotificationHistoryRepository
import com.bitavida.app.domain.usecase.ManageNotificationHistory
import com.bitavida.app.domain.usecase.ObserveNotificationHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationCenterState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val items: List<NotificationHistoryItem> = emptyList(),
) {
    val unread: Int get() = items.count { it.readAtEpochMillis == null && it.result == com.bitavida.app.domain.repository.NotificationPublicationResult.PUBLISHED }
}

class NotificationCenterViewModel(repository: NotificationHistoryRepository) : ViewModel() {
    private val manage = ManageNotificationHistory(repository)
    private val writeFailed = MutableStateFlow(false)
    private val source = ObserveNotificationHistory(repository)()
        .map { NotificationCenterState(loading = false, items = it) }
        .catch { emit(NotificationCenterState(loading = false, failed = true)) }
    val state: StateFlow<NotificationCenterState> = combine(source, writeFailed) { read, failed ->
        read.copy(failed = read.failed || failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationCenterState())

    fun markRead(id: String) = update { manage.markRead(id, System.currentTimeMillis()) }
    fun markOpened(id: String) = update { manage.markOpened(id, System.currentTimeMillis()) }
    fun markAllRead() = update { manage.markAllRead(System.currentTimeMillis()) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); writeFailed.value = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { writeFailed.value = true }
        }
    }

    companion object {
        fun factory(repository: NotificationHistoryRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(NotificationCenterViewModel::class.java))
                return NotificationCenterViewModel(repository) as T
            }
        }
    }
}
