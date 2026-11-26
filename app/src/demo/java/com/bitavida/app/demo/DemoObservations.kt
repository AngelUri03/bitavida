package com.bitavida.app.demo

import android.content.Context
import com.bitavida.app.data.observations.EncryptedObservationRepository
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.CorrectObservationUseCase
import com.bitavida.app.domain.usecase.SaveObservationFollowUpUseCase
import com.bitavida.app.domain.usecase.SaveObservationUseCase
import com.bitavida.app.domain.usecase.projectObservationOccurrences
import java.time.DayOfWeek
import java.time.LocalTime

/** Uses the production encrypted repository; IDs remain stable across interrupted setup. */
internal suspend fun seedDemoObservations(
    context: Context,
    actor: GlucoseActor,
    reference: DemoReference,
): Set<String> {
    val repository = EncryptedObservationRepository(context)
    val first = reference.firstDate
    val followId = "demo-observation-followup"
    var snapshot = repository.load(actor)
    if (snapshot.followUps.none { it.schedule.id == followId }) {
        val created = first.atTime(6, 0)
        val revision = GlucoseFollowUpRevision(
            id = "$followId-r1", followUpId = followId, revisionNumber = 1,
            indication = "Revisión de ejemplo registrada para el seguimiento.",
            frequency = GlucoseFollowUpFrequency.SPECIFIC_DAYS,
            weekdays = setOf(DayOfWeek.MONDAY), startsOn = first, endsOn = null,
            effectiveFrom = first,
            slots = listOf(GlucoseFollowUpSlot(
                id = "$followId-slot", time = LocalTime.of(18, 0),
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = false,
            )),
            remindersEnabled = false, changeKind = GlucoseFollowUpChangeKind.CREATED,
            capturedAt = created, authorActorId = actor.caregiverId,
        )
        SaveObservationFollowUpUseCase(repository)(actor, ObservationFollowUp(
            "OBS-001", GlucoseFollowUp(followId, actor.personUnderCareId, created,
                actor.caregiverId, listOf(revision)),
        ), 0)
    }
    snapshot = repository.load(actor)
    val expected = mutableSetOf<String>()
    var date = first
    while (date <= reference.lastDate) {
        val index = date.dayOfMonth
        val whenObserved = date.atTime(14, 0)
        if (index % 2 == 0 && reference.includesOccurredAt(whenObserved)) {
            val id = "demo-observation-spontaneous-$date"
            expected += id
            if (snapshot.records.none { it.id == id }) {
                val (observation, answers, description) = when (index % 8) {
                    0 -> Triple("OBS-008", mapOf("change" to listOf("Más cansancio")),
                        "Necesitó una pausa durante la caminata.")
                    2 -> Triple("OBS-010", mapOf("change" to listOf("Boca seca")),
                        "Comentó que tenía la boca seca por la tarde.")
                    4 -> Triple("OBS-019", mapOf("change" to listOf("Despierta seguido")),
                        "Comentó que se despertó durante la noche.")
                    else -> Triple("OBS-027", emptyMap(),
                        "Comentó que descansó de forma diferente a lo habitual.")
                }
                val photoIds = when (index) {
                    6 -> listOf("$id-photo", "$id-photo-vertical")
                    22 -> listOf("$id-photo-vertical")
                    else -> emptyList()
                }
                photoIds.forEach { ensureDemoObservationImage(context, actor, it) }
                val record = ObservationRecord(
                    id = id, observationId = observation, personUnderCareId = actor.personUnderCareId,
                    occurredOn = date, occurredAt = whenObserved.toLocalTime(),
                    timePrecision = ObservationTimePrecision.EXACT, result = ObservationResult.CHANGES,
                    answers = answers, description = description, createdAt = whenObserved,
                    actorId = actor.caregiverId, localPhotoIds = photoIds,
                )
                check(SaveObservationUseCase(repository)(actor, record, whenObserved).id == id)
            }
        }
        val reviewTime = date.atTime(18, 0)
        if (date.dayOfWeek == DayOfWeek.MONDAY && reference.includesOccurredAt(reviewTime) &&
            index % 3 != 0) {
            val id = "demo-observation-review-$date"
            expected += id
            if (snapshot.records.none { it.id == id }) {
                val occurrence = projectObservationOccurrences(date, snapshot.followUps, snapshot.records, reviewTime)
                    .single { it.followUpId == followId }
                val omitted = index % 4 == 0
                val record = ObservationRecord(
                    id = id, observationId = "OBS-001", personUnderCareId = actor.personUnderCareId,
                    occurredOn = date, occurredAt = reviewTime.toLocalTime(),
                    timePrecision = ObservationTimePrecision.EXACT,
                    result = if (omitted) ObservationResult.NOT_PERFORMED else ObservationResult.NO_CHANGES,
                    answers = if (omitted) emptyMap() else mapOf("foot" to listOf("Ambos")),
                    description = if (omitted) "Revisión no realizada según lo declarado." else "",
                    followUpId = followId, occurrenceId = occurrence.id,
                    scheduledAt = occurrence.scheduledAt, createdAt = reviewTime, actorId = actor.caregiverId,
                )
                check(SaveObservationUseCase(repository)(actor, record, reviewTime).id == id)
            }
        }
        date = date.plusDays(1)
    }
    snapshot = repository.load(actor)
    val correctionDate = first.withDayOfMonth(4)
    val originalId = "demo-observation-spontaneous-$correctionDate"
    val correctionId = "demo-observation-correction-$correctionDate"
    val original = snapshot.records.firstOrNull { it.id == originalId }
    if (original != null && snapshot.recordRevisions.none { it.id == correctionId }) {
        val correctedAt = correctionDate.plusDays(1).atTime(10, 0)
        check(reference.includesOccurredAt(correctedAt))
        val correction = ObservationRecordRevision(
            id = correctionId, recordId = original.id, revisionNumber = 2,
            snapshot = original.copy(description = "Comentó que se despertó varias veces durante la noche."),
            reason = "Se aclaró la descripción del registro de ejemplo.",
            correctedAt = correctedAt, actorId = actor.caregiverId,
        )
        check(CorrectObservationUseCase(repository)(actor, correction, 1, correctedAt).id == correctionId)
    }
    // Replace one image in a separate correction; the first revision and its
    // attachment remain available for historical cuts and audit.
    snapshot = repository.load(actor)
    val photoCorrectionDate = first.withDayOfMonth(6)
    val photoRecordId = "demo-observation-spontaneous-$photoCorrectionDate"
    val photoCorrectionId = "demo-observation-photo-correction-$photoCorrectionDate"
    val photoOriginal = snapshot.records.firstOrNull { it.id == photoRecordId }
    if (photoOriginal != null && snapshot.recordRevisions.none { it.id == photoCorrectionId }) {
        val correctedAt = photoCorrectionDate.plusDays(1).atTime(10, 0)
        check(reference.includesOccurredAt(correctedAt))
        val replacementId = "$photoRecordId-photo-replacement-vertical"
        ensureDemoObservationImage(context, actor, replacementId)
        val correction = ObservationRecordRevision(
            id = photoCorrectionId, recordId = photoOriginal.id, revisionNumber = 2,
            snapshot = photoOriginal.copy(localPhotoIds = listOf("$photoRecordId-photo", replacementId)),
            reason = "Se sustituyó una ilustración del ejemplo.", correctedAt = correctedAt,
            actorId = actor.caregiverId,
        )
        check(CorrectObservationUseCase(repository)(actor, correction, 1, correctedAt).id == photoCorrectionId)
    }
    val stored = repository.load(actor)
    check(stored.records.map { it.id }.containsAll(expected))
    check(stored.followUps.any { it.schedule.id == followId })
    val photoIdsToVerify = stored.records.filter { it.id in expected }.flatMap { it.localPhotoIds } +
        stored.recordRevisions.filter { it.recordId in expected }.flatMap { it.snapshot.localPhotoIds }
    photoIdsToVerify.distinct().forEach {
        val bytes = requireNotNull(com.bitavida.app.data.observations.ObservationPhotoStore(context).read(actor, it))
        check(bytes.isNotEmpty())
        bytes.fill(0)
    }
    return expected
}
