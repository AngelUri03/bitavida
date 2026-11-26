package com.bitavida.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Explicit caregiver-declared care management, never an institution-confirmed booking. */
enum class CareEventKind { REVIEW_TO_SCHEDULE, APPOINTMENT, STUDY }
/** Specific category declared by the caregiver; older records retain GENERAL. */
enum class CareEventCategory { GENERAL, SPECIALTY, LABORATORY, IMAGING, ADMINISTRATIVE }
/** LEGACY preserves records saved before the six-step form existed. */
enum class CareEventPurpose { LEGACY, MEDICAL_CONSULTATION, STUDY, PROCEDURE, ADMINISTRATIVE, OTHER }
/** Explicit consultation subtype; older ambiguous records remain UNSPECIFIED. */
enum class CareConsultationType { UNSPECIFIED, FAMILY_MEDICINE, GENERAL_MEDICINE, SPECIALIST, OTHER }
enum class CareEventTimeMode { LEGACY, EXACT, TIME_UNCONFIRMED, ALL_DAY, TO_SCHEDULE }
enum class CareEventDuration { UNSPECIFIED, THIRTY_MINUTES, ONE_HOUR, VARIABLE }
enum class CareEventModality { IN_PERSON, PHONE, VIDEO }
enum class CareEventPreparation { FASTING, PREVIOUS_STUDIES, PRESCRIPTION_OR_CARD, ARRIVE_EARLY, ACCOMPANIED, OTHER }
enum class CareEventStatus {
    TO_SCHEDULE, CONFIRMED, COMPLETED, CANCELLED,
    REQUESTED, STUDY_TO_SCHEDULE, SCHEDULED, PERFORMED, RESULT_PENDING, RESULT_AVAILABLE, REVIEWED,
}

data class CareEventDraft(
    val kind: CareEventKind,
    val title: String,
    val transcribedIndication: String = "",
    val suggestedDate: LocalDate? = null,
    val scheduledAt: LocalDateTime? = null,
    val location: String = "",
    val instructions: String = "",
    val caregiverNotes: String = "",
    val status: CareEventStatus,
    val changeReason: String = "",
    val scheduledDate: LocalDate? = null,
    val reminderEnabled: Boolean = false,
    val category: CareEventCategory = CareEventCategory.GENERAL,
    val specialty: String = "",
    val institution: String = "",
    val office: String = "",
    val reminderMinutesBefore: Int = 60,
    val purpose: CareEventPurpose = CareEventPurpose.LEGACY,
    val timeMode: CareEventTimeMode = CareEventTimeMode.LEGACY,
    val duration: CareEventDuration = CareEventDuration.UNSPECIFIED,
    val modality: CareEventModality = CareEventModality.IN_PERSON,
    val professional: String = "",
    val reference: String = "",
    val contactInformation: String = "",
    val otherPreparation: String = "",
    val preparations: Set<CareEventPreparation> = emptySet(),
    /** Empty with reminderEnabled retains the single legacy offset. */
    val reminderOffsetsMinutes: Set<Int> = emptySet(),
    val showInToday: Boolean = true,
    /** Stable catalog identity; empty for a manually entered institution. */
    val verifiedUnitId: String = "",
    /** Only the caregiver can explicitly classify a consultation or include it in follow-up. */
    val consultationType: CareConsultationType = CareConsultationType.UNSPECIFIED,
    val isFollowUp: Boolean = false,
)

data class CareEvent(
    val id: String,
    val personUnderCareId: String,
    val draft: CareEventDraft,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime,
    val revision: Int,
)
