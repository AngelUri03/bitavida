package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.ObservationRepository
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.domain.validation.fitsTextLimit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LoadObservationsUseCase(private val repository: ObservationRepository) {
    suspend operator fun invoke(actor: GlucoseActor): ObservationSnapshot {
        requireObservationActor(actor)
        return repository.load(actor)
    }
}

class SaveObservationUseCase(private val repository: ObservationRepository) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        record: ObservationRecord,
        now: LocalDateTime = LocalDateTime.now(),
    ): ObservationRecord {
        requireObservationActor(actor)
        require(record.personUnderCareId == actor.personUnderCareId && record.actorId == actor.caregiverId)
        require(validateObservation(record, now).isEmpty()) { "Revisa los campos de la observación." }
        require(repository.hasSensitiveConsent(actor)) { "Se requiere consentimiento para guardar." }
        val snapshot = repository.load(actor)
        snapshot.records.firstOrNull { it.id == record.id }?.let { return it }
        if (record.occurrenceId != null) {
            // Resolve the exact scheduled event, never by proximity to the real timestamp.
            val occurrence = projectObservationOccurrences(
                requireNotNull(record.scheduledAt).toLocalDate(), snapshot.followUps, snapshot.records, now,
            ).firstOrNull { it.id == record.occurrenceId }
            require(occurrence != null && occurrence.followUpId == record.followUpId &&
                occurrence.observationId == record.observationId && occurrence.scheduledAt == record.scheduledAt)
        }
        return repository.saveRecord(actor, record)
    }
}

class CorrectObservationUseCase(private val repository: ObservationRepository) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        revision: ObservationRecordRevision,
        expectedRevisionNumber: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): ObservationRecordRevision {
        requireObservationActor(actor)
        require(repository.hasSensitiveConsent(actor))
        val snapshot = repository.load(actor)
        snapshot.recordRevisions.firstOrNull { it.id == revision.id }?.let {
            require(it == revision)
            return it
        }
        val original = requireNotNull(snapshot.records.firstOrNull { it.id == revision.recordId })
        require(validateObservationCorrection(original, revision, expectedRevisionNumber, actor, now).isEmpty()) {
            "Revisa la corrección y su motivo."
        }
        val currentNumber = snapshot.recordRevisions.filter { it.recordId == revision.recordId }
            .maxOfOrNull { it.revisionNumber } ?: 1
        require(currentNumber == expectedRevisionNumber) { "El registro cambió; vuelve a consultarlo." }
        return repository.appendRecordCorrection(actor, revision, expectedRevisionNumber)
    }
}

/** Stable identity and scheduled association cannot be reassigned by a correction. */
fun validateObservationCorrection(
    original: ObservationRecord,
    revision: ObservationRecordRevision,
    expectedRevisionNumber: Int,
    actor: GlucoseActor,
    now: LocalDateTime,
): Map<String, String> {
    val errors = validateObservation(revision.snapshot, now, original).toMutableMap()
    val replacement = revision.snapshot
    if (revision.id.isBlank() || revision.recordId != original.id || replacement.id != original.id ||
        replacement.observationId != original.observationId || replacement.personUnderCareId != original.personUnderCareId ||
        replacement.followUpId != original.followUpId || replacement.occurrenceId != original.occurrenceId ||
        replacement.scheduledAt != original.scheduledAt || replacement.createdAt != original.createdAt ||
        replacement.actorId != original.actorId || original.personUnderCareId != actor.personUnderCareId ||
        revision.actorId != actor.caregiverId || revision.revisionNumber != expectedRevisionNumber + 1 ||
        expectedRevisionNumber < 1 || revision.correctedAt > now || revision.correctedAt < original.createdAt) {
        errors["correction"] = "No se puede cambiar la identidad o asociación de este registro."
    }
    if (revision.reason.isNullOrBlank() || !fitsObservationTextCapture(revision.reason, CaptureLimit.CHANGE_REASON.maximum)) {
        errors["correctionReason"] = "Indica el motivo de la corrección (hasta 300 caracteres)."
    }
    return errors
}

class SaveObservationFollowUpUseCase(private val repository: ObservationRepository) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        followUp: ObservationFollowUp,
        expectedRevisionNumber: Int,
    ): ObservationFollowUp {
        requireObservationActor(actor)
        require(ObservationCatalog.get(followUp.observationId).supportsFollowUp)
        require(followUp.schedule.personUnderCareId == actor.personUnderCareId)
        require(followUp.schedule.currentRevision.authorActorId == actor.caregiverId)
        require(followUp.schedule.currentRevision.revisionNumber == expectedRevisionNumber + 1)
        require(repository.hasSensitiveConsent(actor))
        val previous = repository.load(actor).followUps.firstOrNull {
            it.schedule.id == followUp.schedule.id
        }?.schedule?.currentRevision
        require(validateObservationFollowUpText(followUp.schedule.currentRevision, previous).isEmpty())
        return repository.appendFollowUp(actor, followUp, expectedRevisionNumber)
    }
}

/** Contextual errors are keyed by field id. No clinical thresholds or inferred symptoms. */
fun validateObservation(
    record: ObservationRecord,
    now: LocalDateTime,
    original: ObservationRecord? = null,
): Map<String, String> {
    val errors = linkedMapOf<String, String>()
    errors.putAll(validateObservationText(record, original))
    val definition = ObservationCatalog.all.firstOrNull { it.id == record.observationId }
        ?: return mapOf("observation" to "Elige una observación del catálogo.")
    if (record.id.isBlank() || record.personUnderCareId.isBlank() || record.actorId.isBlank()) {
        errors["identity"] = "No se pudo identificar el registro."
    }
    if (record.occurredOn > now.toLocalDate() ||
        (record.occurredOn == now.toLocalDate() && record.occurredAt?.let { it > now.toLocalTime() } == true)) {
        errors["date"] = "La fecha y hora no pueden estar en el futuro."
    }
    if ((record.timePrecision == ObservationTimePrecision.UNKNOWN) != (record.occurredAt == null)) {
        errors["time"] = "Indica la hora o selecciona que no la conoces."
    }
    val linked = record.occurrenceId != null
    if (listOf(record.followUpId, record.occurrenceId, record.scheduledAt).count { it != null } !in listOf(0, 3)) {
        errors["occurrence"] = "Revisa la asociación con la revisión programada."
    }
    if (definition.eventOnly && record.result != ObservationResult.EVENT &&
        !(linked && record.result == ObservationResult.NOT_PERFORMED)) {
        errors["result"] = "Registra lo que sucedió en este acontecimiento."
    } else if (!definition.eventOnly && record.result == ObservationResult.EVENT) {
        errors["result"] = "Selecciona el resultado de la observación."
    }
    if (record.result == ObservationResult.NOT_PERFORMED && !linked) {
        errors["result"] = "Este resultado corresponde a una revisión programada."
    }
    if (record.result == ObservationResult.NO_CHANGES && !linked && record.observationId != "OBS-001") {
        errors["result"] = "Describe el cambio que observaste."
    }
    if (record.answers.keys.any { key -> definition.fields.none { it.id == key } }) {
        errors["answers"] = "Hay una respuesta que no pertenece a este formulario."
    }
    val reviewClosed = record.result in listOf(ObservationResult.NO_CHANGES, ObservationResult.NOT_PERFORMED)
    definition.fields.forEach { field ->
        val values = record.answers[field.id].orEmpty().filter(String::isNotBlank)
        val required = field.required && !reviewClosed
        if (required && values.isEmpty()) errors[field.id] = "Completa este dato."
        when (field.type) {
            ObservationFieldType.TEXT -> if (values.size > 1) {
                errors[field.id] = "Escribe una sola respuesta para este dato."
            }
            ObservationFieldType.SINGLE_CHOICE -> if (values.size > 1 || values.any { it !in field.options }) {
                errors[field.id] = "Elige una opción de la lista."
            }
            ObservationFieldType.MULTIPLE_CHOICE -> if (values.distinct().size != values.size ||
                values.any { it !in field.options }) errors[field.id] = "Revisa las opciones seleccionadas."
        }
    }
    if (record.observationId == "OBS-001" && record.result != ObservationResult.NOT_PERFORMED &&
        record.answers["foot"].isNullOrEmpty()) errors["foot"] = "Indica qué pie revisaste."
    val hasConcreteChoice = definition.fields.any { field ->
        field.type != ObservationFieldType.TEXT && record.answers[field.id].orEmpty().any {
            !it.startsWith("Otro") && it != "No se conoce" && it != "No aplica"
        }
    }
    val choseOther = record.answers.values.flatten().any { it.startsWith("Otro") }
    val unspecifiedFootChange = definition.id == "OBS-001" &&
        record.result == ObservationResult.CHANGES &&
        record.answers["changes"].orEmpty().none(String::isNotBlank) &&
        record.answers["finding"].orEmpty().none(String::isNotBlank)
    if (record.description.isBlank() && (definition.id == "OBS-027" ||
        (!reviewClosed && (!hasConcreteChoice || choseOther || unspecifiedFootChange)))) {
        errors["description"] = "Describe lo que notaste con tus propias palabras."
    }
    if (record.localPhotoIds.isNotEmpty() && !definition.photoAllowed) {
        errors["photos"] = "Este formulario no admite fotografías."
    }
    if (record.localPhotoIds.size > 5 || record.localPhotoIds.distinct().size != record.localPhotoIds.size ||
        record.localPhotoIds.any(String::isBlank)) errors["photos"] = "Revisa las fotografías seleccionadas."
    if (record.schemaVersion != definition.schemaVersion) errors["schema"] = "Vuelve a abrir el formulario."
    return errors
}

/** New capture limits never constrain reading, and unchanged historical text remains editable. */
fun validateObservationText(
    record: ObservationRecord,
    original: ObservationRecord? = null,
): Map<String, String> = buildMap {
    val baseline = original?.takeIf {
        it.id == record.id && it.personUnderCareId == record.personUnderCareId &&
            it.observationId == record.observationId
    }
    if (!fitsObservationTextCapture(record.description, CaptureLimit.OBSERVATION_DESCRIPTION.maximum, baseline?.description)) {
        put("description", observationTextCaptureMessage(record.description, CaptureLimit.OBSERVATION_DESCRIPTION))
    }
    val definition = ObservationCatalog.all.firstOrNull { it.id == record.observationId } ?: return@buildMap
    definition.fields.filter { it.type == ObservationFieldType.TEXT }.forEach { field ->
        record.answers[field.id].orEmpty().forEachIndexed { index, value ->
            if (!fitsObservationTextCapture(value, CaptureLimit.OBSERVATION_TEXT.maximum, baseline?.answers?.get(field.id)?.getOrNull(index))) {
                put(field.id, observationTextCaptureMessage(value, CaptureLimit.OBSERVATION_TEXT))
            }
        }
    }
}

/** Text-only persistence guard, independent of recurrence and clinical catalog rules. */
fun validateObservationFollowUpText(
    revision: GlucoseFollowUpRevision,
    original: GlucoseFollowUpRevision? = null,
): Map<String, String> = buildMap {
    val baseline = original?.takeIf { it.followUpId == revision.followUpId }
    if (revision.indication.isBlank() || !fitsObservationTextCapture(revision.indication, CaptureLimit.CARE_INSTRUCTION.maximum, baseline?.indication)) {
        put("indication", "Transcribe la indicación en hasta 1000 caracteres.")
    }
    if (!fitsObservationTextCapture(revision.changeReason.orEmpty(), CaptureLimit.CHANGE_REASON.maximum, baseline?.changeReason)) {
        put("changeReason", "Usa hasta 300 caracteres para el motivo.")
    }
}

/**
 * Observation payloads retain their existing writeUTF format (65,535 modified-UTF-8 bytes per field).
 * This capacity guard applies only to new/replaced text, never to loading historical payloads.
 */
fun fitsObservationTextCapture(value: String, maximum: Int, original: String? = null): Boolean =
    (original != null && value == original) ||
        (fitsTextLimit(value, maximum) && observationTextFitsPayload(value))

private fun observationTextFitsPayload(value: String): Boolean {
    var bytes = 0
    for (character in value) {
        bytes += when (character.code) {
            in 1..0x7f -> 1
            in 0..0x7ff -> 2
            else -> 3 // Includes each UTF-16 surrogate, exactly as DataOutputStream.writeUTF.
        }
        if (bytes > 65_535) return false
    }
    return true
}

private fun observationTextCaptureMessage(value: String, limit: CaptureLimit): String =
    if (fitsTextLimit(value, limit.maximum)) {
        "La secuencia Unicode es demasiado extensa. Acórtala para guardar."
    } else limit.message

private fun requireObservationActor(actor: GlucoseActor) {
    require(actor.authorizationCurrent &&
        listOf(actor.profileId, actor.caregiverId, actor.personUnderCareId).none(String::isBlank))
}

/**
 * Shared recurrence data, resolved at each event's real start. Later suspension retains
 * past closed events, cancels future/open events and never backfills a late activation.
 */
fun projectObservationOccurrences(
    date: LocalDate,
    followUps: List<ObservationFollowUp>,
    records: List<ObservationRecord>,
    now: LocalDateTime = LocalDateTime.now(),
): List<ObservationOccurrence> = followUps.flatMap { followUp ->
    val schedule = followUp.schedule
    val candidates = schedule.revisions.flatMap { revision ->
        revision.slots.map { slot -> slot.time to slot }
    }.distinctBy { (_, slot) -> listOf(slot.id, slot.time, slot.timingType, slot.windowEnd, slot.endsNextDay) }
    candidates.mapNotNull { (_, candidate) ->
        val start = date.atTime(candidate.time)
        val revision = schedule.revisions.lastOrNull {
            it.effectiveFrom <= date && it.capturedAt <= start && it.capturedAt <= now
        } ?: return@mapNotNull null
        val slot = revision.slots.firstOrNull {
            it.id == candidate.id && it.time == candidate.time && it.timingType == candidate.timingType &&
                it.windowEnd == candidate.windowEnd && it.endsNextDay == candidate.endsNextDay
        } ?: return@mapNotNull null
        if (!revision.active || date < revision.startsOn || revision.endsOn?.let { date > it } == true ||
            !revision.appliesOn(date)) return@mapNotNull null
        val end = slot.windowEnd?.let { date.plusDays(if (slot.endsNextDay) 1 else 0).atTime(it) }
        // Once cancelled, an open window cannot be recreated by a later reactivation.
        // Text-only revisions retain the same logical slot; changed geometry cancels it.
        if (end != null && schedule.revisions.any { later ->
            val changeAt = maxOf(later.effectiveFrom.atStartOfDay(), later.capturedAt)
            changeAt <= now && changeAt >= start && changeAt < end &&
                (!later.active || later.slots.none {
                    it.id == slot.id && it.time == slot.time && it.timingType == slot.timingType &&
                        it.windowEnd == slot.windowEnd && it.endsNextDay == slot.endsNextDay
                })
        }) return@mapNotNull null
        val id = listOf(schedule.id, slot.id, date, slot.time, slot.timingType,
            slot.windowEnd ?: "", slot.endsNextDay).joinToString(":")
        val record = records.firstOrNull {
            it.personUnderCareId == schedule.personUnderCareId && it.occurrenceId == id
        }
        ObservationOccurrence(
            id = id, observationId = followUp.observationId, followUpId = schedule.id,
            followUpRevisionId = revision.id, slotId = slot.id, scheduledAt = start, windowEndAt = end,
            reminderAt = slot.reminderTime?.takeIf { revision.remindersEnabled && slot.reminderEnabled }
                ?.let { date.plusDays(if (slot.reminderNextDay) 1 else 0).atTime(it) },
            record = record,
            state = when {
                record != null -> ObservationOccurrenceState.RECORDED
                (end ?: start) <= now -> ObservationOccurrenceState.UNCONFIRMED
                else -> ObservationOccurrenceState.UPCOMING
            },
        )
    }
}.distinctBy(ObservationOccurrence::id).sortedBy(ObservationOccurrence::scheduledAt)

/** Mi día and reminders consume the same exact event identities and revision rules. */
fun projectUpcomingObservationOccurrences(
    snapshot: ObservationSnapshot,
    now: LocalDateTime,
    limit: Int = 4,
): List<ObservationOccurrence> {
    require(limit >= 0)
    return (0L..1L).flatMap { offset ->
        projectObservationOccurrences(now.toLocalDate().plusDays(offset), snapshot.followUps, snapshot.records, now)
    }.filter { it.record == null && it.state == ObservationOccurrenceState.UPCOMING }
        .sortedBy(ObservationOccurrence::scheduledAt).take(limit)
}

/** Date filters apply to the actual fact; linked facts suppress their scheduled placeholder. */
fun projectObservationTimeline(
    date: LocalDate,
    category: ObservationCategory?,
    snapshot: ObservationSnapshot,
    now: LocalDateTime = LocalDateTime.now(),
    observationId: String? = null,
): List<ObservationTimelineEntry> {
    val entries = snapshot.records.filter { it.occurredOn == date }.map {
        ObservationTimelineEntry(it.observationId, record = it)
    } + projectObservationOccurrences(date, snapshot.followUps, snapshot.records, now)
        .filter { it.record == null && it.state != ObservationOccurrenceState.UPCOMING }
        .map { ObservationTimelineEntry(it.observationId, occurrence = it) }
    return entries.filter { (observationId == null || it.observationId == observationId) &&
            (category == null || ObservationCatalog.get(it.observationId).category == category) }
        .sortedWith(compareBy<ObservationTimelineEntry> {
            it.record?.occurredAt ?: it.occurrence?.scheduledAt?.toLocalTime() ?: LocalTime.MIN
        }.thenBy { it.record?.id ?: it.occurrence?.id })
}
