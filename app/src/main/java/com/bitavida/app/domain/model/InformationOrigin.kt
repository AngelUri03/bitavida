package com.bitavida.app.domain.model

/**
 * Makes the provenance of user-visible health information explicit.
 *
 * These origins must never be merged implicitly because each one carries a
 * different level of authority and a different presentation requirement.
 */
enum class InformationOrigin {
    MEDICAL_PRESCRIPTION_TRANSCRIPTION,
    CAREGIVER_RECORD,
    VERIFIED_GENERAL_EDUCATION,
}
