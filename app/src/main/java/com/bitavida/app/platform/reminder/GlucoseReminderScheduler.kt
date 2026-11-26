package com.bitavida.app.platform.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseFollowUp
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

class GlucoseReminderScheduler(context: Context) {
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
            "com.bitavida.app.action.GLUCOSE_REMINDER"
        internal const val EXTRA_TOKEN = "glucose_reminder_token"
        private val CONFIG = MeasurementReminderConfig(
            namespace = "glucose",
            channelId = "glucose_measurements",
            channelName = "Mediciones programadas",
            channelDescription = "Avisos locales para mediciones configuradas",
            reminderAction = ACTION_REMINDER,
            tokenExtra = EXTRA_TOKEN,
            receiverClass = GlucoseReminderReceiver::class.java,
            icon = R.drawable.ic_v2_droplet,
            privateTitle = "Medición de glucosa programada",
            privateText = "Abre Bitavida para registrar o confirmar esta medición.",
        )
    }
}

class GlucoseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GlucoseReminderScheduler.ACTION_REMINDER) return
        val token = intent.getStringExtra(GlucoseReminderScheduler.EXTRA_TOKEN)
            ?: return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            try { GlucoseReminderScheduler(context).showReminder(token) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* No notification data in logs. */ }
            finally { pending.finish() }
        }
    }
}

class GlucoseReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in SUPPORTED_ACTIONS) {
            GlucoseReminderScheduler(context).restore()
            BloodPressureReminderScheduler(context).restore()
            WeightReminderScheduler(context).restore()
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
