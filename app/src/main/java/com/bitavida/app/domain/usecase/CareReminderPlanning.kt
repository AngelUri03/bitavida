package com.bitavida.app.domain.usecase

/** Decodes only the technical registry left by the retired Recordatorios alarm adapter. */
fun careReminderAlarmTokensForRetirement(scheduledEntries: Set<String>): Set<String> =
    scheduledEntries.asSequence().mapNotNull { entry ->
        entry.lastIndexOf('|').takeIf { it > 0 }?.let { entry.substring(0, it) }
    }.toSet()
