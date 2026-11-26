package com.bitavida.app.data.appointments

import android.content.Context
import com.bitavida.app.data.local.secure.CareEventEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.*
import com.bitavida.app.domain.usecase.CareEventValidationException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EncryptedCareEventRepository(context: Context) : CareEventRepository {
    private val appContext = context.applicationContext
    private val consentRepository = com.bitavida.app.data.observations.EncryptedObservationRepository(appContext)
    private fun CareEventActor.consentActor() = GlucoseActor(profileId, caregiverId, personUnderCareId, authorizationCurrent)
    override suspend fun hasSensitiveConsent(actor: CareEventActor): Boolean = consentRepository.hasSensitiveConsent(actor.consentActor())
    override suspend fun acceptSensitiveConsent(actor: CareEventActor, acceptedAtEpochMillis: Long) =
        consentRepository.acceptSensitiveConsent(actor.consentActor(), acceptedAtEpochMillis)
    override suspend fun load(actor: CareEventActor): List<CareEvent> = storage {
        SecureDatabaseProvider.open(appContext).careEventDao().load(actor).map { it.toDomain() }
    }
    override suspend fun history(actor: CareEventActor, id: String): List<CareEvent> = storage {
        SecureDatabaseProvider.open(appContext).careEventDao().history(actor, id).map { it.snapshot.toDomain() }
    }
    override suspend fun save(actor: CareEventActor, command: CareEventCommand, now: LocalDateTime): CareEvent = storage {
        SecureDatabaseProvider.open(appContext).careEventDao().apply(actor, command, careEventFingerprint(command), now).toDomain()
    }
    override suspend fun saveWithPhotos(actor: CareEventActor, command: CareEventCommand, now: LocalDateTime,
        photos: List<CareEventPhotoInput>, removedPhotoIds: Set<String>): CareEvent = storage {
        SecureDatabaseProvider.open(appContext).careEventDao()
            .applyWithPhotos(actor, command, careEventPhotoFingerprint(command, photos, removedPhotoIds), now, photos, removedPhotoIds).toDomain()
    }
    private suspend fun <T> storage(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: CareEventValidationException) { throw error }
        catch (error: CareEventAuthorizationException) { throw error }
        catch (error: CareEventSensitiveConsentException) { throw error }
        catch (error: Exception) { throw CareEventStorageException(error) }
    }
}

internal fun CareEventEntity.toDomain() = CareEvent(id, personUnderCareId,
    CareEventDraft(CareEventKind.valueOf(kind), title, transcribedIndication,
        suggestedDate?.let(LocalDate::parse), scheduledAt?.let(LocalDateTime::parse), location,
        instructions, caregiverNotes, CareEventStatus.valueOf(status), changeReason, scheduledDate?.let(LocalDate::parse), reminderEnabled,
        CareEventCategory.valueOf(category), specialty, institution, office, reminderMinutesBefore,
        CareEventPurpose.valueOf(purpose), CareEventTimeMode.valueOf(timeMode), CareEventDuration.valueOf(duration),
        CareEventModality.valueOf(modality), professional, reference, contactInformation, otherPreparation,
        preparations.split(',').filter { it.isNotBlank() }.map(CareEventPreparation::valueOf).toSet(),
        reminderOffsetsMinutes.split(',').filter { it.isNotBlank() }.map(String::toInt).toSet(), showInToday, verifiedUnitId,
        CareConsultationType.valueOf(consultationType), isFollowUp),
    LocalDateTime.parse(createdAt), LocalDateTime.parse(modifiedAt), revision)

internal fun CareEvent.toEntity(actor: CareEventActor) = CareEventEntity(id, actor.profileId, actor.caregiverId,
    personUnderCareId, draft.kind.name, draft.title, draft.transcribedIndication, draft.suggestedDate?.toString(),
    draft.scheduledAt?.toString(), draft.scheduledDate?.toString(), draft.location, draft.instructions, draft.caregiverNotes, draft.status.name,
    draft.changeReason, createdAt.toString(), modifiedAt.toString(), revision, draft.reminderEnabled,
    draft.category.name, draft.specialty, draft.institution, draft.office, draft.reminderMinutesBefore,
    draft.purpose.name, draft.timeMode.name, draft.duration.name, draft.modality.name,
    draft.professional, draft.reference, draft.contactInformation, draft.otherPreparation,
    draft.preparations.map { it.name }.sorted().joinToString(","),
    draft.reminderOffsetsMinutes.sorted().joinToString(","), draft.showInToday, draft.verifiedUnitId,
    draft.consultationType.name, draft.isFollowUp)

/** Bind an idempotent save to its photo mutations, not only its event fields. */
internal fun careEventPhotoFingerprint(command: CareEventCommand, photos: List<CareEventPhotoInput>,
    removedPhotoIds: Set<String>): String {
    val base = careEventFingerprint(command)
    if (photos.isEmpty() && removedPhotoIds.isEmpty()) return base
    val digest = MessageDigest.getInstance("SHA-256")
    fun update(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
        digest.update(bytes)
    }
    update(base)
    photos.sortedBy { it.id }.forEach { photo ->
        update(photo.id)
        update(photo.replacesId.orEmpty())
        digest.update(MessageDigest.getInstance("SHA-256").digest(photo.jpeg))
    }
    removedPhotoIds.sorted().forEach(::update)
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun careEventFingerprint(command: CareEventCommand): String {
    val draft = when (command) {
        is CareEventCommand.Create -> command.draft
        is CareEventCommand.Revise -> command.draft
    }
    val values = listOf(command.operationId, command.id,
        if (command is CareEventCommand.Revise) "revise:${command.expectedRevision}" else "create",
        draft.kind.name, draft.title, draft.transcribedIndication, draft.suggestedDate?.toString(),
        draft.scheduledAt?.toString(), draft.scheduledDate?.toString(), draft.location, draft.instructions, draft.caregiverNotes, draft.status.name, draft.changeReason, draft.reminderEnabled.toString())
    // Preserve fingerprints from older versions for idempotent retries of unchanged requests.
    val extensions = if (draft.category == CareEventCategory.GENERAL && draft.specialty.isEmpty() &&
        draft.institution.isEmpty() && draft.office.isEmpty() && draft.reminderMinutesBefore == 60) emptyList()
        else listOf(draft.category.name, draft.specialty, draft.institution, draft.office, draft.reminderMinutesBefore.toString())
    val hasWizardFields = draft.purpose != CareEventPurpose.LEGACY || draft.timeMode != CareEventTimeMode.LEGACY ||
        draft.duration != CareEventDuration.UNSPECIFIED || draft.modality != CareEventModality.IN_PERSON ||
        draft.professional.isNotEmpty() || draft.reference.isNotEmpty() || draft.contactInformation.isNotEmpty() ||
        draft.otherPreparation.isNotEmpty() || draft.preparations.isNotEmpty() ||
        draft.reminderOffsetsMinutes.isNotEmpty() || !draft.showInToday
    val wizard = if (!hasWizardFields) emptyList() else listOf(
        "wizard-v1", draft.purpose.name, draft.timeMode.name, draft.duration.name, draft.modality.name,
        draft.professional, draft.reference, draft.contactInformation, draft.otherPreparation,
        draft.preparations.map { it.name }.sorted().joinToString(","),
        draft.reminderOffsetsMinutes.sorted().joinToString(","), draft.showInToday.toString(),
    )
    val verifiedUnit = if (draft.verifiedUnitId.isEmpty()) emptyList() else listOf("verified-unit-v1", draft.verifiedUnitId)
    val consultation = if (draft.consultationType == CareConsultationType.UNSPECIFIED && !draft.isFollowUp)
        emptyList() else listOf("consultation-v1", draft.consultationType.name, draft.isFollowUp.toString())
    val canonical = (values + extensions + wizard + verifiedUnit + consultation).joinToString("") { if (it == null) "-1:" else "${it.length}:$it" }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
