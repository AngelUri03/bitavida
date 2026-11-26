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
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import com.bitavida.app.MainActivity
import com.bitavida.app.R
import com.bitavida.app.data.careprofile.EncryptedCareProfileRepository
import com.bitavida.app.data.medical.EncryptedMedicalCareRepository
import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationReminderPreference
import com.bitavida.app.domain.model.MedicationReminderRuntimeState
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.usecase.PlannedMedicationReminder
import com.bitavida.app.domain.usecase.planMedicationReminders
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class MedicationReminderReconciliationResult(
    val state: MedicationReminderRuntimeState,
    val scheduledCount: Int,
)

class MedicationReminderScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val alarmManager =
        applicationContext.getSystemService(AlarmManager::class.java)
    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)
    private val registry = applicationContext.getSharedPreferences(
        REGISTRY_NAME,
        Context.MODE_PRIVATE,
    )

    fun reconcile(
        plans: List<MedicationPlan>,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): MedicationReminderReconciliationResult {
        createNotificationChannels()
        cancelRegisteredAlarms()
        val eligibleReminderPlans = plans.filter { plan ->
            val configuration = plan.currentRevision.configuration
            plan.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA &&
                (
                    configuration?.status == MedicationTreatmentStatus.ACTIVE ||
                        (
                            configuration?.status ==
                                MedicationTreatmentStatus.REMOVED &&
                                configuration.endsOn?.isAfter(
                                    now.toLocalDate(),
                                ) == true
                            )
                    ) &&
                configuration.reminderPreference !=
                MedicationReminderPreference.NONE &&
                configuration.endsOn?.isBefore(now.toLocalDate()) != true
        }
        if (eligibleReminderPlans.isEmpty()) {
            return MedicationReminderReconciliationResult(
                MedicationReminderRuntimeState.DISABLED,
                0,
            )
        }
        if (!notificationsAllowed()) {
            return MedicationReminderReconciliationResult(
                MedicationReminderRuntimeState.PERMISSION_REQUIRED,
                0,
            )
        }
        if (eligibleReminderPlans.none(MedicationPlan::hasExplicitReminderSource)) {
            return MedicationReminderReconciliationResult(
                MedicationReminderRuntimeState.NO_SCHEDULED_NOTICE,
                0,
            )
        }

        val exactAllowed = exactAlarmsAllowed()
        val reminders = planMedicationReminders(
            plans = eligibleReminderPlans,
            rangeStart = now,
            rangeEnd = now.plusDays(HORIZON_DAYS),
            limit = MAX_SCHEDULED_REMINDERS,
        )
        val tokens = linkedSetOf<String>()
        reminders.forEach { reminder ->
            val token = tokenFor(reminder.id)
            val triggerAtMillis =
                reminder.scheduledAt.atZone(zoneId).toInstant().toEpochMilli()
            if (triggerAtMillis > System.currentTimeMillis()) {
                schedule(
                    triggerAtMillis = triggerAtMillis,
                    pendingIntent = reminderPendingIntent(token),
                    exactAllowed = exactAllowed,
                )
                tokens += token
            }
        }
        val refreshAtMillis =
            now.plusDays(REFRESH_AFTER_DAYS)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        schedule(
            triggerAtMillis = refreshAtMillis,
            pendingIntent = refreshPendingIntent(),
            exactAllowed = exactAllowed,
        )
        tokens += REFRESH_TOKEN
        registry.edit { putStringSet(KEY_TOKENS, tokens) }
        return MedicationReminderReconciliationResult(
            state = if (exactAllowed) {
                MedicationReminderRuntimeState.ACTIVE
            } else {
                MedicationReminderRuntimeState.SYSTEM_LIMITED
            },
            scheduledCount = tokens.count { it != REFRESH_TOKEN },
        )
    }

    fun notificationsAllowed(): Boolean =
        notificationManager.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    applicationContext.checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                )

    fun exactAlarmsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

    internal fun findReminder(
        plans: List<MedicationPlan>,
        token: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlannedMedicationReminder? =
        planMedicationReminders(
            plans = plans,
            rangeStart = now.minusDays(1),
            rangeEnd = now.plusDays(HORIZON_DAYS),
            limit = MAX_SCHEDULED_REMINDERS,
        ).firstOrNull { tokenFor(it.id) == token }

    internal suspend fun showNotification(
        reminder: PlannedMedicationReminder,
        token: String,
    ) {
        val title = "Recordatorio de medicamento"
        val message = listOf(reminder.medicationName, "${reminder.amount.value} ${reminder.amount.unit}").joinToString(" · ")
        if (!notificationsAllowed()) {
            recordNotificationAttempt(applicationContext, "MEDICATIONS", title, message, token,
                com.bitavida.app.domain.repository.NotificationPublicationResult.BLOCKED)
            return
        }
        try {
            createNotificationChannels()
        } catch (_: Exception) {
            recordNotificationAttempt(applicationContext, "MEDICATIONS", title, message, token,
                com.bitavida.app.domain.repository.NotificationPublicationResult.FAILED)
            return
        }
        val channelId = when (reminder.preference) {
            MedicationReminderPreference.PROMINENT -> CHANNEL_PROMINENT
            MedicationReminderPreference.NORMAL -> CHANNEL_NORMAL
            MedicationReminderPreference.NONE -> return
        }
        if (!notificationChannelAvailable(notificationManager, channelId)) {
            recordNotificationAttempt(applicationContext, "MEDICATIONS", title, message, token,
                com.bitavida.app.domain.repository.NotificationPublicationResult.BLOCKED)
            return
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            token.hashCode(),
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = Notification.Builder(
            applicationContext,
            channelId,
        )
            .setSmallIcon(R.drawable.ic_v2_pill)
            .setContentTitle("Recordatorio de medicamento")
            .setContentText("Consulta Bitavida para ver los detalles.")
            .build()
        val notification = Notification.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_v2_pill)
            .setContentTitle("Recordatorio de medicamento")
            .setContentText(message)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        val result = try {
            notificationManager.notify(token.hashCode(), notification)
            com.bitavida.app.domain.repository.NotificationPublicationResult.PUBLISHED
        } catch (_: Exception) {
            com.bitavida.app.domain.repository.NotificationPublicationResult.FAILED
        }
        recordNotificationAttempt(applicationContext, "MEDICATIONS", title, message, token, result)
    }

    private fun schedule(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
        exactAllowed: Boolean,
    ) {
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun cancelRegisteredAlarms() {
        registry.getStringSet(KEY_TOKENS, emptySet()).orEmpty().forEach { token ->
            val pendingIntent = if (token == REFRESH_TOKEN) {
                refreshPendingIntent()
            } else {
                reminderPendingIntent(token)
            }
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        registry.edit { remove(KEY_TOKENS) }
    }

    private fun reminderPendingIntent(token: String): PendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            token.hashCode(),
            Intent(
                applicationContext,
                MedicationReminderReceiver::class.java,
            ).apply {
                action = ACTION_REMINDER
                data = "bitavida://medication-reminder/$token".toUri()
                putExtra(EXTRA_TOKEN, token)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun refreshPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            REFRESH_TOKEN.hashCode(),
            Intent(
                applicationContext,
                MedicationReminderReceiver::class.java,
            ).apply {
                action = ACTION_REFRESH
                data = "bitavida://medication-reminder/refresh".toUri()
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannels() {
        val normal = NotificationChannel(
            CHANNEL_NORMAL,
            "Recordatorios de medicamentos",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avisos locales de medicamentos registrados en Bitavida"
            setBypassDnd(false)
        }
        val prominent = NotificationChannel(
            CHANNEL_PROMINENT,
            "Recordatorios destacados de medicamentos",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisos locales destacados, sujetos a las decisiones del sistema"
            setBypassDnd(false)
        }
        notificationManager.createNotificationChannels(
            listOf(normal, prominent),
        )
    }

    companion object {
        private const val REGISTRY_NAME = "medication_reminder_alarm_registry"
        private const val KEY_TOKENS = "opaque_alarm_tokens"
        private const val CHANNEL_NORMAL = "medication_reminders_normal"
        private const val CHANNEL_PROMINENT = "medication_reminders_prominent"
        private const val ACTION_REMINDER =
            "com.bitavida.app.action.MEDICATION_REMINDER"
        private const val ACTION_REFRESH =
            "com.bitavida.app.action.REFRESH_MEDICATION_REMINDERS"
        private const val EXTRA_TOKEN = "opaque_reminder_token"
        private const val REFRESH_TOKEN = "refresh"
        private const val HORIZON_DAYS = 7L
        private const val REFRESH_AFTER_DAYS = 6L
        private const val MAX_SCHEDULED_REMINDERS = 256

        internal fun tokenFor(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }

        internal fun isReminderAction(action: String?): Boolean =
            action == ACTION_REMINDER

        internal fun tokenFrom(intent: Intent): String? =
            intent.getStringExtra(EXTRA_TOKEN)
    }
}

class MedicationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val scheduler = MedicationReminderScheduler(context)
                if (MedicationReminderScheduler.isReminderAction(intent.action)) {
                    val token = MedicationReminderScheduler.tokenFrom(intent)
                    if (token != null) {
                        val plans = loadStoredMedicationPlans(context)
                        scheduler.findReminder(plans, token)?.let {
                            scheduler.showNotification(it, token)
                        }
                    }
                }
                reconcileStoredMedicationReminders(context)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Do not include protected notification data in logs.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class MedicationReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            )
        ) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reconcileStoredMedicationReminders(context)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Do not include protected notification data in logs.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

suspend fun reconcileStoredMedicationReminders(
    context: Context,
): MedicationReminderReconciliationResult? = runCatching {
    val plans = loadStoredMedicationPlans(context)
    MedicationReminderScheduler(context).reconcile(plans)
}.getOrNull()

private fun MedicationPlan.hasExplicitReminderSource(): Boolean {
    val configuration = currentRevision.configuration ?: return false
    if (configuration.frequency == MedicationFrequencyKind.AS_NEEDED) {
        return false
    }
    if (configuration.frequency == MedicationFrequencyKind.EVERY_N_HOURS) {
        return true
    }
    return configuration.administrations.any { administration ->
        when (administration.timing) {
            MedicationAdministrationTiming.EXACT_TIME ->
                administration.reminderEnabled != false
            MedicationAdministrationTiming.TIME_WINDOW ->
                administration.noticeTime != null
            MedicationAdministrationTiming.ACTIVITY_ONLY ->
                administration.activityNoticeTime != null
        }
    }
}

private suspend fun loadStoredMedicationPlans(
    context: Context,
): List<MedicationPlan> {
    val profile = EncryptedCareProfileRepository(context).get()
        ?: return emptyList()
    return EncryptedMedicalCareRepository(context)
        .load(profile.personUnderCare.id)
        .medicationPlans
}
