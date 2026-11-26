package com.bitavida.app.platform.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseFollowUp
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

class BloodPressureReminderScheduler(context: Context) {
    private val delegate = MeasurementReminderScheduler(context, CONFIG)

    fun reconcile(
        followUp: GlucoseFollowUp,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) = delegate.reconcile(followUp, today, zoneId)

    fun restore() = delegate.restore()

    suspend fun showReminder(token: String) = delegate.showReminder(token)

    companion object {
        internal const val ACTION_REMINDER =
            "com.bitavida.app.action.BLOOD_PRESSURE_REMINDER"
        internal const val EXTRA_TOKEN = "blood_pressure_reminder_token"
        private val CONFIG = MeasurementReminderConfig(
            namespace = "blood_pressure",
            channelId = "blood_pressure_measurements",
            channelName = "Presión arterial programada",
            channelDescription = "Avisos locales para mediciones de presión configuradas",
            reminderAction = ACTION_REMINDER,
            tokenExtra = EXTRA_TOKEN,
            receiverClass = BloodPressureReminderReceiver::class.java,
            icon = R.drawable.ic_measurement_pressure,
            privateTitle = "Medición de presión programada",
            privateText = "Abre Bitavida para registrar o confirmar esta medición.",
        )
    }
}

class BloodPressureReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BloodPressureReminderScheduler.ACTION_REMINDER) return
        val token = intent.getStringExtra(
            BloodPressureReminderScheduler.EXTRA_TOKEN,
        ) ?: return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            try { BloodPressureReminderScheduler(context).showReminder(token) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* No notification data in logs. */ }
            finally { pending.finish() }
        }
    }
}
