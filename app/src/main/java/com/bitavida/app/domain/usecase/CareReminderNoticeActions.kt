package com.bitavida.app.domain.usecase

/**
 * Collects cancellation identities left by the retired Android-notification feature.
 * Inputs are copied; no record, repository or completion command is involved.
 */
fun careReminderTokensForRetirement(
    scheduledEntries: Set<String>,
    deliveredTokens: Set<String>,
    visibleNoticeTags: Set<String> = emptySet(),
): Set<String> {
    val visible = visibleNoticeTags.mapNotNull { tag ->
        tag.takeIf { it.startsWith("care:") }?.removePrefix("care:")
    }
    // Earlier versions used opaque hashes; technical identities from older versions are also cancellable.
    return (careReminderAlarmTokensForRetirement(scheduledEntries) + deliveredTokens + visible)
        .asSequence().filter { it.isNotBlank() }.toSet()
}
