package com.bitavida.app.platform.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.bitavida.app.MainActivity
import com.bitavida.app.domain.usecase.careReminderTokensForRetirement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Upgrade-only cancellation for the retired Android notices of Recordatorios.
 * No path in this adapter schedules, publishes, opens or completes a reminder.
 */
class CareReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val alarmRegistry = appContext.getSharedPreferences(ALARM_REGISTRY, Context.MODE_PRIVATE)
    private val deliveredRegistry = appContext.getSharedPreferences(DELIVERY_REGISTRY, Context.MODE_PRIVATE)

    suspend fun cancelExistingNotices() = withContext(Dispatchers.IO) {
        retirementMutex.withLock {
            val active = notificationManager.activeNotifications.filter { it.notification.channelId == CHANNEL_ID }
            val tokens = careReminderTokensForRetirement(
                alarmRegistry.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty(),
                deliveredRegistry.getStringSet(KEY_DELIVERED, emptySet()).orEmpty(),
                active.asSequence().mapNotNull { it.tag }.toSet(),
            )
            var cancelled = true
            tokens.forEach { token ->
                try { cancelCapabilities(token) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { cancelled = false }
            }
            // Match the channel as well as tag/id: a hash collision must never cancel another module.
            active.forEach { notice ->
                try { notificationManager.cancel(notice.tag, notice.id) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { cancelled = false }
            }
            try { notificationManager.deleteNotificationChannel(CHANNEL_ID) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { cancelled = false }
            if (cancelled) {
                // Keep failed cancellation metadata for a later app-entry/upgrade retry.
                alarmRegistry.edit(commit = true) { clear() }
                deliveredRegistry.edit(commit = true) { clear() }
            }
        }
    }

    private fun cancelCapabilities(token: String) {
        val alarmRequest = (31 * token.hashCode()) + "care".hashCode()
        val alarm = Intent(appContext, CareReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = capabilityUri("care-reminder", token)
        }
        cancelBroadcast(alarmRequest, alarm)
        // The earliest informational notice used the same receiver/request without a data URI.
        cancelBroadcast(alarmRequest, Intent(appContext, CareReminderReceiver::class.java).setAction(ACTION_REMINDER))
        cancelBroadcast(
            0,
            Intent(appContext, CareReminderReceiver::class.java).apply {
                action = ACTION_COMPLETE
                data = capabilityUri("care-reminder-complete", token)
            },
        )
        PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).apply {
                action = ACTION_OPEN_REMINDER
                data = capabilityUri("care-reminder-open", token)
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.cancel()
    }

    private fun cancelBroadcast(request: Int, intent: Intent) {
        PendingIntent.getBroadcast(
            appContext, request, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { pending ->
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    companion object {
        internal const val ACTION_REMINDER = "com.bitavida.app.action.CARE_REMINDER"
        internal const val ACTION_COMPLETE = "com.bitavida.app.action.COMPLETE_CARE_REMINDER"
        internal const val ACTION_OPEN_REMINDER = "com.bitavida.app.action.OPEN_CARE_REMINDER"
        internal const val CHANNEL_ID = "care_errands"
        internal const val ALARM_REGISTRY = "care_reminder_registry"
        internal const val DELIVERY_REGISTRY = "care_notice_delivery"
        internal const val KEY_SCHEDULED = "scheduled"
        internal const val KEY_DELIVERED = "opaque_delivered_tokens"
        private val retirementMutex = Mutex()
        internal val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        internal val RECEIVER_TIMEOUT = 8.seconds

        private fun capabilityUri(authority: String, token: String): Uri =
            Uri.Builder().scheme("bitavida").authority(authority).appendPath(token).build()
    }
}

/** Kept non-exported only to cancel capabilities already held by Android from an older APK. */
class CareReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(CareReminderScheduler.ACTION_REMINDER, CareReminderScheduler.ACTION_COMPLETE)) return
        val pending = goAsync()
        CareReminderScheduler.receiverScope.launch {
            try {
                withTimeout(CareReminderScheduler.RECEIVER_TIMEOUT) {
                    CareReminderScheduler(context.applicationContext).cancelExistingNotices()
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Retry retirement on the next app entry; no record is changed. */ }
            finally { pending.finish() }
        }
    }
}

class CareReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        CareReminderScheduler.receiverScope.launch {
            try {
                withTimeout(CareReminderScheduler.RECEIVER_TIMEOUT) {
                    CareReminderScheduler(context.applicationContext).cancelExistingNotices()
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* No titles, identifiers or storage details are logged. */ }
            finally { pending.finish() }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
