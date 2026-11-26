package com.bitavida.app.platform.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bitavida.app.R
import com.bitavida.app.data.careprofile.EncryptedCareProfileRepository
import com.bitavida.app.data.observations.EncryptedObservationRepository
import com.bitavida.app.domain.model.ObservationSnapshot
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.projectObservationOccurrences
import com.bitavida.app.domain.usecase.projectObservationReminderOccurrences
import com.bitavida.app.domain.usecase.observationReminderIsCurrent
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All reconciliation paths reload committed data inside one process-wide lock. */
class ObservationReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val delegate = MeasurementReminderScheduler(appContext, CONFIG)

    suspend fun reconcileLatest(actor: GlucoseActor) {
        reconciliationMutex.withLock {
            val repository = EncryptedObservationRepository(appContext)
            val snapshot = repository.load(actor)
            if (repository.hasSensitiveConsent(actor)) reconcile(snapshot)
            else delegate.reconcilePlanned(emptyList())
        }
    }

    suspend fun restore() {
        val actor = currentActor()
        if (actor == null) {
            reconciliationMutex.withLock { delegate.reconcilePlanned(emptyList()) }
        } else {
            reconcileLatest(actor)
        }
    }

    /** Recheck the encrypted current state before displaying even an already queued alarm. */
    suspend fun deliver(token: String) {
        val actor = currentActor() ?: return
        reconciliationMutex.withLock {
            val repository = EncryptedObservationRepository(appContext)
            if (!repository.hasSensitiveConsent(actor)) {
                delegate.reconcilePlanned(emptyList())
                return@withLock
            }
            val snapshot = repository.load(actor)
            val now = LocalDateTime.now()
            val event = (-1L..1L).asSequence().flatMap { day ->
                projectObservationOccurrences(now.toLocalDate().plusDays(day),
                    snapshot.followUps, snapshot.records, now).asSequence()
            }.firstOrNull { it.id == token && it.record == null && it.reminderAt?.let { time -> time <= now } == true }
            if (event != null && observationReminderIsCurrent(snapshot, event, now)) delegate.showReminder(token)
            // Every delivered alarm renews the horizon, including when the app stays closed.
            reconcile(snapshot)
        }
    }

    private suspend fun currentActor(): GlucoseActor? {
        val profile = EncryptedCareProfileRepository(appContext).get() ?: return null
        return GlucoseActor(profile.id, profile.caregiver.id, profile.personUnderCare.id,
            profile.consent.isCurrent).takeIf { it.authorizationCurrent }
    }

    private fun reconcile(
        snapshot: ObservationSnapshot,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        val now = LocalDateTime.now(zoneId)
        val planned = projectObservationReminderOccurrences(snapshot, now).map { event ->
            event.id to requireNotNull(event.reminderAt).atZone(zoneId).toInstant().toEpochMilli()
        }
        delegate.reconcilePlanned(planned)
    }

    companion object {
        internal const val ACTION_REMINDER = "com.bitavida.app.action.OBSERVATION_REMINDER"
        internal const val EXTRA_TOKEN = "observation_reminder_token"
        private val reconciliationMutex = Mutex()
        internal val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val CONFIG = MeasurementReminderConfig(
            namespace = "observation",
            channelId = "observation_reviews",
            channelName = "Revisiones programadas",
            channelDescription = "Avisos locales para revisiones configuradas",
            reminderAction = ACTION_REMINDER,
            tokenExtra = EXTRA_TOKEN,
            receiverClass = ObservationReminderReceiver::class.java,
            icon = R.drawable.ic_v2_stethoscope,
            privateTitle = "Revisión programada",
            privateText = "Abre Bitavida para registrar el resultado de la revisión.",
            publicTitle = "Recordatorio de Bitavida",
        )
    }
}

class ObservationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ObservationReminderScheduler.ACTION_REMINDER) return
        val token = intent.getStringExtra(ObservationReminderScheduler.EXTRA_TOKEN) ?: return
        val pending = goAsync()
        ObservationReminderScheduler.receiverScope.launch {
            try { ObservationReminderScheduler(context).deliver(token) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Never disclose encrypted storage details to notifications/logs. */ }
            finally { pending.finish() }
        }
    }
}

class ObservationReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        ObservationReminderScheduler.receiverScope.launch {
            try { ObservationReminderScheduler(context).restore() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Next app entry or system event retries without replacing data. */ }
            finally { pending.finish() }
        }
    }

    private companion object {
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")
    }
}
