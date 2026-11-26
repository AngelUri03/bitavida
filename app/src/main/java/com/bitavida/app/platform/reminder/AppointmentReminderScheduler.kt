package com.bitavida.app.platform.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.bitavida.app.MainActivity
import com.bitavida.app.R
import com.bitavida.app.data.appointments.EncryptedCareEventRepository
import com.bitavida.app.data.careprofile.EncryptedCareProfileRepository
import com.bitavida.app.data.local.secure.NotificationDeliveryEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.domain.repository.CareEventActor
import com.bitavida.app.domain.repository.NotificationPublicationResult
import com.bitavida.app.domain.usecase.careEventReminderAlarmToken
import com.bitavida.app.domain.usecase.careEventReminderDeliveryKey
import com.bitavida.app.domain.usecase.careEventReminders
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reconciles from committed, encrypted records and rechecks authorization at delivery. */
class AppointmentReminderScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarms = MeasurementReminderScheduler(this.context, CONFIG)

    suspend fun reconcileLatest(actor: CareEventActor) = mutex.withLock {
        reconcile(actor, restoreElapsed = false)
    }

    /** Serialize event mutation and delivery so a queued alarm cannot use a superseded revision. */
    suspend fun <T> commitAndReconcile(actor: CareEventActor, eventId: String? = null, save: suspend () -> T): T = mutex.withLock {
        // Read the previous schedule before committing so already published notices can be withdrawn.
        val previous = eventId?.let { id ->
            EncryptedCareEventRepository(context).load(actor).singleOrNull { it.id == id }
        }
        val saved = save()
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            eventId?.let { manager.cancel(notificationId(it)) } // legacy single-notice identity
            previous?.let { event ->
                careEventReminders(event).forEach { manager.cancel(notificationId(careEventReminderAlarmToken(event, it))) }
            }
            reconcile(actor, restoreElapsed = false)
        }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* The committed record remains authoritative; retry on next entry. */ }
        saved
    }

    suspend fun restore() = mutex.withLock {
        val actor = currentActor()
        if (actor == null) alarms.reconcilePlanned(emptyList())
        else reconcile(actor, restoreElapsed = true)
    }

    private suspend fun reconcile(actor: CareEventActor, restoreElapsed: Boolean) {
        val repository = EncryptedCareEventRepository(context)
        if (!repository.hasSensitiveConsent(actor)) {
            alarms.reconcilePlanned(emptyList())
            return
        }
        val now = LocalDateTime.now()
        val dao = SecureDatabaseProvider.open(context).notificationDeliveryDao()
        val reminders = repository.load(actor).flatMap { event ->
            val scheduledAt = event.draft.scheduledAt
            if (scheduledAt == null || scheduledAt <= now) emptyList()
            else careEventReminders(event).filterNot { dao.hasClaim(actor.profileId, careEventReminderDeliveryKey(event, it.minutesBefore)) }
                .map { reminder -> event to reminder }
        }
        val planned = reminders.map { (event, reminder) ->
            careEventReminderAlarmToken(event, reminder) to reminder.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        alarms.reconcilePlanned(planned, reminders.map { (event, reminder) -> careEventReminderAlarmToken(event, reminder) }.toSet(), restoreElapsed)
    }

    suspend fun deliver(token: String) = mutex.withLock {
        val actor = currentActor() ?: return@withLock
        val repository = EncryptedCareEventRepository(context)
        if (!repository.hasSensitiveConsent(actor)) {
            alarms.reconcilePlanned(emptyList())
            return@withLock
        }
        val now = LocalDateTime.now()
        // Match only an opaque token belonging to the current authorized record and schedule.
        val match = repository.load(actor).asSequence().flatMap { event ->
            careEventReminders(event).asSequence().map { reminder -> event to reminder }
        }.singleOrNull { (event, reminder) -> careEventReminderAlarmToken(event, reminder) == token }
        if (match != null) {
            val (event, reminder) = match
            val scheduledAt = event.draft.scheduledAt
            val expectedEpoch = reminder.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (scheduledAt != null && now >= reminder.at && now < scheduledAt &&
                alarms.isRegistered(token, expectedEpoch)) {
                val dao = SecureDatabaseProvider.open(context).notificationDeliveryDao()
                val id = careEventReminderDeliveryKey(event, reminder.minutesBefore)
                val claim = NotificationDeliveryEntity(
                    id, actor.profileId, "APPOINTMENTS", TITLE, MESSAGE,
                    System.currentTimeMillis(), "ATTEMPTING", event.id, null, null,
                )
                if (dao.claimOnce(claim) != -1L) {
                    val outcome = publish(token)
                    dao.finishClaim(actor.profileId, id, outcome.name)
                }
            }
        }
        reconcile(actor, restoreElapsed = false)
    }

    private fun publish(token: String): NotificationPublicationResult {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        ) return NotificationPublicationResult.BLOCKED
        return try {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Citas y estudios", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos locales de citas y estudios programados"
            })
            if (!notificationChannelAvailable(manager, CHANNEL)) return NotificationPublicationResult.BLOCKED
            val notificationId = notificationId(token)
            val activity = PendingIntent.getActivity(context, notificationId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val publicVersion = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_v2_calendar)
                .setContentTitle("Recordatorio de Bitavida")
                .setContentText("Consulta Bitavida para ver los detalles.").build()
            val notice = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_v2_calendar)
                .setContentTitle(TITLE).setContentText(MESSAGE)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion).setContentIntent(activity)
                .setAutoCancel(true).setOnlyAlertOnce(true).build()
            manager.notify(notificationId, notice)
            NotificationPublicationResult.PUBLISHED
        } catch (_: Exception) {
            NotificationPublicationResult.FAILED
        }
    }

    private suspend fun currentActor(): CareEventActor? {
        val profile = EncryptedCareProfileRepository(context).get() ?: return null
        return CareEventActor(profile.id, profile.caregiver.id, profile.personUnderCare.id, profile.consent.isCurrent)
            .takeIf { it.authorizationCurrent }
    }

    private fun notificationId(token: String): Int = 31 * token.hashCode() + "appointments".hashCode()

    companion object {
        internal const val ACTION = "com.bitavida.app.action.APPOINTMENT_REMINDER"
        internal const val EXTRA = "appointment_reminder_token"
        internal const val CHANNEL = "appointments_and_studies"
        private const val TITLE = "Cita o estudio programado"
        private const val MESSAGE = "Abre Bitavida para consultar la información registrada."
        private val mutex = Mutex()
        internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val CONFIG = MeasurementReminderConfig(
            namespace = "appointment", channelId = CHANNEL, channelName = "Citas y estudios",
            channelDescription = "Avisos locales de citas y estudios programados", reminderAction = ACTION,
            tokenExtra = EXTRA, receiverClass = AppointmentReminderReceiver::class.java,
            icon = R.drawable.ic_v2_calendar, privateTitle = TITLE, privateText = MESSAGE,
            publicTitle = "Recordatorio de Bitavida", opaquePendingIntentIdentity = true,
        )
    }
}

class AppointmentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppointmentReminderScheduler.ACTION) return
        val token = intent.getStringExtra(AppointmentReminderScheduler.EXTRA) ?: return
        val pending = goAsync()
        AppointmentReminderScheduler.scope.launch {
            try { AppointmentReminderScheduler(context).deliver(token) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Protected storage may be temporarily unavailable. */ }
            finally { pending.finish() }
        }
    }
}

class AppointmentReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        AppointmentReminderScheduler.scope.launch {
            try { AppointmentReminderScheduler(context).restore() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* A later app entry or system event retries. */ }
            finally { pending.finish() }
        }
    }

    private companion object {
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")
    }
}
