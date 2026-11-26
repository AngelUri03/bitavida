package com.bitavida.app.platform.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bitavida.app.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Prepared cancellation contracts. No notifications, alarm deliveries or databases are used. */
@RunWith(AndroidJUnit4::class)
class CareReminderNotificationContractTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = "a".repeat(64)
    private val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private val lookupFlags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE

    @Test fun retirementCancelsBothLegacyAlarmShapesAndTheOldCompletionAndOpeningCapabilities() = runBlocking {
        val request = (31 * token.hashCode()) + "care".hashCode()
        val alarm = receiver(CareReminderScheduler.ACTION_REMINDER, "care-reminder")
        val legacyAlarm = Intent(context, CareReminderReceiver::class.java).setAction(CareReminderScheduler.ACTION_REMINDER)
        val complete = receiver(CareReminderScheduler.ACTION_COMPLETE, "care-reminder-complete")
        val open = Intent(context, MainActivity::class.java).apply {
            action = CareReminderScheduler.ACTION_OPEN_REMINDER
            data = Uri.parse("bitavida://care-reminder-open/$token")
        }
        val created = listOf(
            PendingIntent.getBroadcast(context, request, alarm, flags),
            PendingIntent.getBroadcast(context, request, legacyAlarm, flags),
            PendingIntent.getBroadcast(context, 0, complete, flags),
            PendingIntent.getActivity(context, 0, open, flags),
        )
        try {
            context.getSharedPreferences(CareReminderScheduler.ALARM_REGISTRY, Context.MODE_PRIVATE).edit(commit = true) {
                putStringSet(CareReminderScheduler.KEY_SCHEDULED, setOf("$token|1795107600000"))
            }
            context.getSharedPreferences(CareReminderScheduler.DELIVERY_REGISTRY, Context.MODE_PRIVATE).edit(commit = true) {
                putStringSet(CareReminderScheduler.KEY_DELIVERED, setOf(token))
            }
            val scheduler = CareReminderScheduler(context)
            scheduler.cancelExistingNotices()
            scheduler.cancelExistingNotices()
            assertNull(PendingIntent.getBroadcast(context, request, alarm, lookupFlags))
            assertNull(PendingIntent.getBroadcast(context, request, legacyAlarm, lookupFlags))
            assertNull(PendingIntent.getBroadcast(context, 0, complete, lookupFlags))
            assertNull(PendingIntent.getActivity(context, 0, open, lookupFlags))
            assertTrue(context.getSharedPreferences(CareReminderScheduler.ALARM_REGISTRY, Context.MODE_PRIVATE).all.isEmpty())
            assertTrue(context.getSharedPreferences(CareReminderScheduler.DELIVERY_REGISTRY, Context.MODE_PRIVATE).all.isEmpty())
        } finally {
            created.forEach { it.cancel() }
        }
    }

    @Test fun cancellingRemindersPreservesAnotherModulesCapabilitiesAndTechnicalState() = runBlocking {
        val isolatedRegistry = context.getSharedPreferences("fictional_other_reminder_registry", Context.MODE_PRIVATE)
        val previous = isolatedRegistry.getStringSet("scheduled", emptySet()).orEmpty().toSet()
        val otherIntent = Intent(context, GlucoseReminderReceiver::class.java).apply {
            action = "com.bitavida.app.action.FICTIONAL_UNRELATED_NOTICE"
            data = Uri.parse("bitavida://fictional-unrelated/$token")
        }
        // The same integer request code is insufficient to identify a different module's capability.
        val request = (31 * token.hashCode()) + "care".hashCode()
        val other = PendingIntent.getBroadcast(context, request, otherIntent, flags)
        try {
            isolatedRegistry.edit(commit = true) { putStringSet("scheduled", setOf("fictional-other-token|100")) }
            context.getSharedPreferences(CareReminderScheduler.ALARM_REGISTRY, Context.MODE_PRIVATE).edit(commit = true) {
                putStringSet(CareReminderScheduler.KEY_SCHEDULED, setOf("$token|100"))
            }
            CareReminderScheduler(context).cancelExistingNotices()
            assertNotNull(PendingIntent.getBroadcast(context, request, otherIntent, lookupFlags))
            assertEquals(setOf("fictional-other-token|100"), isolatedRegistry.getStringSet("scheduled", emptySet()))
        } finally {
            other.cancel()
            isolatedRegistry.edit(commit = true) { putStringSet("scheduled", previous) }
        }
    }

    private fun receiver(actionName: String, authority: String) = Intent(context, CareReminderReceiver::class.java).apply {
        action = actionName
        data = Uri.parse("bitavida://$authority/$token")
    }
}
