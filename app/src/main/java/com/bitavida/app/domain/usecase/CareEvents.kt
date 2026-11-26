package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.CareEventCommand
import com.bitavida.app.domain.validation.fitsTextLimit
import java.time.LocalDateTime

enum class CareEventProblem { INVALID_ID, TITLE_REQUIRED, TEXT_TOO_LONG, INVALID_STATE, CONFIRMED_TIME_REQUIRED, PAST_TIME, HISTORICAL_READ_ONLY, CONFLICT, NOT_FOUND }
class CareEventValidationException(val problem: CareEventProblem) : Exception(problem.name)

/** Pure validation. Events never advance status merely because their date has passed. */
fun applyCareEventCommand(records: List<CareEvent>, personId: String, command: CareEventCommand, now: LocalDateTime): CareEvent {
    fun fail(problem: CareEventProblem): Nothing = throw CareEventValidationException(problem)
    if (personId.isBlank() || command.id.isBlank() || command.operationId.isBlank()) fail(CareEventProblem.INVALID_ID)
    val existing = records.singleOrNull { it.id == command.id && it.personUnderCareId == personId }
    val draft = when (command) {
        is CareEventCommand.Create -> {
            if (existing != null) fail(CareEventProblem.CONFLICT)
            command.draft
        }
        is CareEventCommand.Revise -> {
            if (existing == null) fail(CareEventProblem.NOT_FOUND)
            if (existing.revision != command.expectedRevision) fail(CareEventProblem.CONFLICT)
            command.draft
        }
    }
    if (draft.title.isBlank()) fail(CareEventProblem.TITLE_REQUIRED)
    // Explicitly clarify an ambiguous historical consultation without changing its facts.
    // The normal repository transaction still appends a revision and checks authorization.
    val historical = existing?.draft?.let {
        (it.scheduledAt?.let { time -> !time.isAfter(now) } == true) ||
            (it.scheduledAt == null && it.scheduledDate?.isBefore(now.toLocalDate()) == true)
    } == true
    val isClarification = existing != null && command is CareEventCommand.Revise &&
        existing.draft.kind == CareEventKind.APPOINTMENT &&
        existing.draft.consultationType == CareConsultationType.UNSPECIFIED &&
        existing.draft.purpose in setOf(CareEventPurpose.LEGACY, CareEventPurpose.MEDICAL_CONSULTATION) &&
        draft.purpose == CareEventPurpose.MEDICAL_CONSULTATION &&
        draft.consultationType != CareConsultationType.UNSPECIFIED &&
        existing.draft.copy(purpose = draft.purpose, consultationType = draft.consultationType,
            isFollowUp = draft.isFollowUp, specialty = draft.specialty, changeReason = draft.changeReason) == draft
    if (historical && isClarification) {
        if (!fitsTextLimit(draft.specialty, 100, existing!!.draft.specialty) ||
            !fitsTextLimit(draft.changeReason, 300, existing.draft.changeReason)) fail(CareEventProblem.TEXT_TOO_LONG)
        return CareEvent(existing.id, personId, draft, existing.createdAt,
            maxOf(now, existing.modifiedAt), existing.revision + 1)
    }
    // Historical records remain readable and revisable; new events require an explicit time,
    // and revisions cannot silently erase a time that was already recorded.
    if ((command is CareEventCommand.Create || existing?.draft?.scheduledAt != null) && draft.scheduledAt == null)
        fail(CareEventProblem.CONFIRMED_TIME_REQUIRED)
    val existingTime = existing?.draft?.scheduledAt
    if (existingTime != null && !existingTime.isAfter(now)) fail(CareEventProblem.HISTORICAL_READ_ONLY)
    if (existingTime == null && existing?.draft?.scheduledDate?.isBefore(now.toLocalDate()) == true)
        fail(CareEventProblem.HISTORICAL_READ_ONLY)
    if (draft.scheduledAt != null && !draft.scheduledAt.isAfter(now)) fail(CareEventProblem.PAST_TIME)
    val attendanceStatuses = setOf(CareEventStatus.COMPLETED, CareEventStatus.PERFORMED,
        CareEventStatus.RESULT_PENDING, CareEventStatus.RESULT_AVAILABLE, CareEventStatus.REVIEWED)
    if (draft.status in attendanceStatuses && draft.status != existing?.draft?.status)
        fail(CareEventProblem.INVALID_STATE)
    if (!fitsTextLimit(draft.title, 100, existing?.draft?.title.orEmpty()) ||
        !fitsTextLimit(draft.transcribedIndication, 1000, existing?.draft?.transcribedIndication.orEmpty()) ||
        !fitsTextLimit(draft.location, 200, existing?.draft?.location.orEmpty()) ||
        !fitsTextLimit(draft.instructions, 1000, existing?.draft?.instructions.orEmpty()) ||
        !fitsTextLimit(draft.caregiverNotes, 1000, existing?.draft?.caregiverNotes.orEmpty()) ||
        !fitsTextLimit(draft.changeReason, 300, existing?.draft?.changeReason.orEmpty()) ||
        !fitsTextLimit(draft.specialty, 100, existing?.draft?.specialty.orEmpty()) ||
        !fitsTextLimit(draft.institution, 200, existing?.draft?.institution.orEmpty()) ||
        !fitsTextLimit(draft.office, 100, existing?.draft?.office.orEmpty()) ||
        !fitsTextLimit(draft.professional, 100, existing?.draft?.professional.orEmpty()) ||
        !fitsTextLimit(draft.reference, 200, existing?.draft?.reference.orEmpty()) ||
        !fitsTextLimit(draft.contactInformation, 200, existing?.draft?.contactInformation.orEmpty()) ||
        !fitsTextLimit(draft.otherPreparation, 500, existing?.draft?.otherPreparation.orEmpty()) ||
        !fitsTextLimit(draft.verifiedUnitId, 100, existing?.draft?.verifiedUnitId.orEmpty())) fail(CareEventProblem.TEXT_TOO_LONG)
    // Classification is declared, never inferred from title, unit or legacy category.
    val isConsultation = draft.kind != CareEventKind.STUDY && draft.purpose == CareEventPurpose.MEDICAL_CONSULTATION
    if ((draft.consultationType != CareConsultationType.UNSPECIFIED || draft.isFollowUp) && !isConsultation)
        fail(CareEventProblem.INVALID_STATE)
    if (draft.isFollowUp && draft.consultationType == CareConsultationType.UNSPECIFIED)
        fail(CareEventProblem.INVALID_STATE)
    val allowed = when (draft.kind) {
        CareEventKind.REVIEW_TO_SCHEDULE -> setOf(CareEventStatus.TO_SCHEDULE, CareEventStatus.CANCELLED)
        CareEventKind.APPOINTMENT -> setOf(CareEventStatus.CONFIRMED, CareEventStatus.COMPLETED, CareEventStatus.CANCELLED)
        CareEventKind.STUDY -> setOf(CareEventStatus.REQUESTED, CareEventStatus.STUDY_TO_SCHEDULE, CareEventStatus.SCHEDULED,
            CareEventStatus.PERFORMED, CareEventStatus.RESULT_PENDING, CareEventStatus.RESULT_AVAILABLE, CareEventStatus.REVIEWED, CareEventStatus.CANCELLED)
    }
    if (draft.status !in allowed) fail(CareEventProblem.INVALID_STATE)
    if (existing?.draft?.status in setOf(CareEventStatus.CANCELLED, CareEventStatus.COMPLETED) &&
        draft.status != existing?.draft?.status) fail(CareEventProblem.INVALID_STATE)
    if (existing?.draft?.status in setOf(CareEventStatus.PERFORMED, CareEventStatus.RESULT_PENDING,
            CareEventStatus.RESULT_AVAILABLE, CareEventStatus.REVIEWED) &&
        draft.status in setOf(CareEventStatus.REQUESTED, CareEventStatus.STUDY_TO_SCHEDULE, CareEventStatus.SCHEDULED))
        fail(CareEventProblem.INVALID_STATE)
    if (draft.kind == CareEventKind.REVIEW_TO_SCHEDULE && (draft.scheduledAt != null || draft.scheduledDate != null)) fail(CareEventProblem.INVALID_STATE)
    if (draft.kind == CareEventKind.APPOINTMENT && draft.scheduledAt == null && draft.scheduledDate == null)
        fail(CareEventProblem.CONFIRMED_TIME_REQUIRED)
    if (draft.status == CareEventStatus.SCHEDULED && draft.scheduledDate == null && draft.scheduledAt == null)
        fail(CareEventProblem.INVALID_STATE)
    if (draft.reminderMinutesBefore !in 1..10080 || (draft.reminderEnabled && draft.scheduledAt == null) ||
        draft.reminderOffsetsMinutes.any { it !in setOf(30, 120, 1440) } ||
        (!draft.reminderEnabled && draft.reminderOffsetsMinutes.isNotEmpty()))
        fail(CareEventProblem.INVALID_STATE)
    val date = draft.scheduledAt?.toLocalDate() ?: draft.scheduledDate
    when (draft.timeMode) {
        CareEventTimeMode.LEGACY -> Unit
        CareEventTimeMode.EXACT -> if (draft.scheduledAt == null) fail(CareEventProblem.INVALID_STATE)
        CareEventTimeMode.TIME_UNCONFIRMED, CareEventTimeMode.ALL_DAY ->
            if (date == null || draft.scheduledAt != null) fail(CareEventProblem.INVALID_STATE)
        CareEventTimeMode.TO_SCHEDULE ->
            if (draft.kind != CareEventKind.REVIEW_TO_SCHEDULE || date != null) fail(CareEventProblem.INVALID_STATE)
    }
    if (draft.scheduledDate != null && draft.scheduledAt != null && draft.scheduledDate != draft.scheduledAt.toLocalDate())
        fail(CareEventProblem.INVALID_STATE)
    if (existing != null && existing.draft.kind != draft.kind &&
        existing.draft.kind != CareEventKind.REVIEW_TO_SCHEDULE && draft.kind != CareEventKind.REVIEW_TO_SCHEDULE)
        fail(CareEventProblem.INVALID_STATE)
    return CareEvent(command.id, personId, draft.copy(title = draft.title.trim(), scheduledDate = draft.scheduledDate ?: draft.scheduledAt?.toLocalDate()),
        existing?.createdAt ?: now, maxOf(now, existing?.modifiedAt ?: now), (existing?.revision ?: 0) + 1)
}
