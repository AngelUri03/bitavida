package com.bitavida.app.domain.validation

/** A declared reason needs visible text; whitespace or decoration alone is incomplete. */
internal fun hasMedicationIntakeReason(value: String?): Boolean =
    value?.any { it.isLetterOrDigit() } == true
