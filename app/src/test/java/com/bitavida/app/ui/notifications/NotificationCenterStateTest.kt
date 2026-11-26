package com.bitavida.app.ui.notifications

import com.bitavida.app.domain.repository.NotificationHistoryItem
import com.bitavida.app.domain.repository.NotificationPublicationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCenterStateTest {
    private fun item(id: String, result: NotificationPublicationResult, read: Long? = null) =
        NotificationHistoryItem(id, "MEASUREMENTS", "Aviso", "Mensaje ficticio", 0L, result, null, read, null)

    @Test fun unreadBadgeCountsOnlyPublishedUnreadAttempts() {
        val state = NotificationCenterState(loading = false, items = listOf(
            item("one", NotificationPublicationResult.PUBLISHED),
            item("two", NotificationPublicationResult.PUBLISHED, 1L),
            item("three", NotificationPublicationResult.BLOCKED),
            item("four", NotificationPublicationResult.FAILED),
        ))
        assertEquals(1, state.unread)
    }
}
