package com.bitavida.app.platform.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import com.bitavida.app.MainActivity
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.occurrencesOn
import java.time.LocalDate
import java.time.ZoneId

/**
 * A legacy token can be reused when its event is reprogrammed. Preserve its old alarm only
 * when both token and instant still match the authoritative schedule.
 */
internal fun preserveScheduledReminderEntries(
    registered: Set<String>,
    planned: List<Pair<String, Long>>,
    preserveTokens: Set<String>,
    restoreElapsed: Boolean,
    nowMillis: Long,
): Set<String> {
    val expected = planned.map { (token, epoch) -> token + "|" + epoch }.toSet()
    return registered.filterTo(mutableSetOf()) { entry ->
        val divider = entry.lastIndexOf('|')
        divider > 0 &&
            entry.substring(0, divider) in preserveTokens &&
            entry in expected &&
            entry.substring(divider + 1).toLongOrNull()?.let { restoreElapsed || it <= nowMillis } == true
    }
}

internal data class MeasurementReminderConfig(
    val namespace: String,
    val channelId: String,
    val channelName: String,
    val channelDescription: String,
    val reminderAction: String,
    val tokenExtra: String,
    val receiverClass: Class<out BroadcastReceiver>,
    @DrawableRes val icon: Int,
    val privateTitle: String,
    val privateText: String,
    val publicTitle: String = "Recordatorio de medición",
    val preferExactAlarms: Boolean = true,
    val opaquePendingIntentIdentity: Boolean = false,
)

internal class MeasurementReminderScheduler(
    context: Context,
    private val config: MeasurementReminderConfig,
) {
    private val applicationContext = context.applicationContext
    private val alarmManager =
        applicationContext.getSystemService(AlarmManager::class.java)
    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)
    private val registry: SharedPreferences = applicationContext.getSharedPreferences(
        config.namespace + "_reminder_registry",
        Context.MODE_PRIVATE,
    )

    fun reconcile(
        followUp: GlucoseFollowUp,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) = reconcile(listOf(followUp), today, zoneId)

    fun reconcile(
        followUps: List<GlucoseFollowUp>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        val active = followUps.filter {
            it.currentRevision.active && it.currentRevision.remindersEnabled
        }
        val planned = (0L..HORIZON_DAYS).asSequence()
            .flatMap { day -> active.asSequence().flatMap { it.occurrencesOn(today.plusDays(day)).asSequence() } }
            .mapNotNull { occurrence ->
                occurrence.reminderAt?.let {
                    occurrence.id to it.atZone(zoneId).toInstant().toEpochMilli()
                }
            }.toList()
        reconcilePlanned(planned)
    }

    /** Optional preservation lets inexact one-shot notices await Android delivery after their due time. */
    fun reconcilePlanned(
        planned: List<Pair<String, Long>>,
        preserveElapsedTokens: Set<String> = emptySet(),
        restoreElapsed: Boolean = false,
    ) {
        val nowMillis = System.currentTimeMillis()
        val preserved = preserveScheduledReminderEntries(
            registry.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty(),
            planned, preserveElapsedTokens, restoreElapsed, nowMillis,
        )
        val preservedTokens = preserved.map { it.substringBeforeLast(SEPARATOR) }.toSet()
        cancelAll(preservedTokens)
        if (planned.isEmpty() && preserved.isEmpty()) return
        createChannel()
        if (restoreElapsed) {
            // Reboot clears OS alarms. Retain the original epoch in the registry for later reconciliation.
            preservedTokens.forEach { schedule(nowMillis + 1_000L, reminderIntent(it)) }
        }
        val scheduled = preserved.toMutableSet()
        planned.asSequence().filter { it.second > nowMillis }
            .sortedBy { it.second }.distinctBy { it.first }.take(MAX_REMINDERS)
            .forEach { (token, epochMillis) ->
                schedule(epochMillis, reminderIntent(token))
                scheduled += token + SEPARATOR + epochMillis
            }
        registry.edit { putStringSet(KEY_SCHEDULED, scheduled) }
    }

    /** Confirm queued broadcasts still refer to the currently registered instant. */
    fun isRegistered(token: String, epochMillis: Long): Boolean =
        registry.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().contains(token + SEPARATOR + epochMillis)

    fun restore() {
        val now = System.currentTimeMillis()
        registry.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
            .mapNotNull { encoded ->
                val divider = encoded.lastIndexOf(SEPARATOR)
                if (divider <= 0) return@mapNotNull null
                val token = encoded.substring(0, divider)
                val epoch = encoded.substring(divider + 1).toLongOrNull()
                    ?: return@mapNotNull null
                token to epoch
            }
            .filter { it.second > now }
            .forEach { (token, epoch) ->
                schedule(epoch, reminderIntent(token))
            }
    }

    suspend fun showReminder(token: String) {
        val module = if (config.namespace == "observation") "OBSERVATIONS" else "MEASUREMENTS"
        if (!notificationsAllowed()) {
            recordNotificationAttempt(applicationContext, module, config.privateTitle, config.privateText, token,
                com.bitavida.app.domain.repository.NotificationPublicationResult.BLOCKED)
            return
        }
        try {
            createChannel()
        } catch (_: Exception) {
            recordNotificationAttempt(applicationContext, module, config.privateTitle, config.privateText, token,
                com.bitavida.app.domain.repository.NotificationPublicationResult.FAILED)
            return
        }
        if (!notificationChannelAvailable(notificationManager, config.channelId)) {
            recordNotificationAttempt(applicationContext, module, config.privateTitle, config.privateText, token,
                com.bitavida.app.domain.repository.NotificationPublicationResult.BLOCKED)
            return
        }
        val notificationId = 31 * token.hashCode() + config.namespace.hashCode()
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = Notification.Builder(
            applicationContext,
            config.channelId,
        )
            .setSmallIcon(config.icon)
            .setContentTitle(config.publicTitle)
            .setContentText("Consulta Bitavida para ver los detalles.")
            .build()
        val notification = Notification.Builder(
            applicationContext,
            config.channelId,
        )
            .setSmallIcon(config.icon)
            .setContentTitle(config.privateTitle)
            .setContentText(config.privateText)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        val result = try {
            notificationManager.notify(notificationId, notification)
            com.bitavida.app.domain.repository.NotificationPublicationResult.PUBLISHED
        } catch (_: Exception) {
            com.bitavida.app.domain.repository.NotificationPublicationResult.FAILED
        }
        recordNotificationAttempt(applicationContext, module, config.privateTitle, config.privateText, token, result)
    }

    private fun cancelAll(preserveTokens: Set<String> = emptySet()) {
        registry.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
            .forEach { encoded ->
                val divider = encoded.lastIndexOf(SEPARATOR)
                if (divider > 0 && encoded.substring(0, divider) !in preserveTokens) {
                    alarmManager.cancel(
                        reminderIntent(encoded.substring(0, divider)),
                    )
                }
            }
        registry.edit { remove(KEY_SCHEDULED) }
    }

    private fun reminderIntent(token: String) = PendingIntent.getBroadcast(
        applicationContext,
        31 * token.hashCode() + config.namespace.hashCode(),
        Intent(applicationContext, config.receiverClass).apply {
            action = config.reminderAction
            if (config.opaquePendingIntentIdentity) {
                data = android.net.Uri.Builder().scheme("bitavida")
                    .authority(config.namespace + "-reminder").appendPath(token).build()
            }
            putExtra(config.tokenExtra, token)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(epochMillis: Long, pendingIntent: PendingIntent) {
        if (config.preferExactAlarms &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms())
        ) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                epochMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                epochMillis,
                pendingIntent,
            )
        }
    }

    fun notificationsAllowed() =
        notificationManager.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    applicationContext.checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                )

    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                config.channelId,
                config.channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = config.channelDescription
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private companion object {
        const val KEY_SCHEDULED = "scheduled"
        const val SEPARATOR = "|"
        const val HORIZON_DAYS = 45L
        const val MAX_REMINDERS = 96
    }
}
