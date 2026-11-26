package com.bitavida.app.platform.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseFollowUp
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

class WeightReminderScheduler(context: Context) {
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
            "com.bitavida.app.action.WEIGHT_REMINDER"
        internal const val EXTRA_TOKEN = "weight_reminder_token"
        private val CONFIG = MeasurementReminderConfig(
            namespace = "weight",
            channelId = "weight_measurements",
            channelName = "Peso programado",
            channelDescription = "Avisos locales para mediciones de peso configuradas",
            reminderAction = ACTION_REMINDER,
            tokenExtra = EXTRA_TOKEN,
            receiverClass = WeightReminderReceiver::class.java,
            icon = R.drawable.ic_measurement_weight,
            privateTitle = "Medición de peso programada",
            privateText = "Abre Bitavida para registrar o confirmar esta medición.",
        )
    }
}

class WeightReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WeightReminderScheduler.ACTION_REMINDER) return
        val token = intent.getStringExtra(
            WeightReminderScheduler.EXTRA_TOKEN,
        ) ?: return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            try { WeightReminderScheduler(context).showReminder(token) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* No notification data in logs. */ }
            finally { pending.finish() }
        }
    }
}
