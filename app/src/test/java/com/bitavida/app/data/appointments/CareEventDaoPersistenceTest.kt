package com.bitavida.app.data.appointments

import com.bitavida.app.data.local.secure.CareEventDao
import com.bitavida.app.data.local.secure.CareEventEntity
import com.bitavida.app.data.local.secure.CareEventRevisionEntity
import com.bitavida.app.data.local.secure.CareEventPhotoEntity
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.*
import com.bitavida.app.domain.usecase.*
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Exercises the production transaction body with a query double; no claim about SQLCipher on device. */
class CareEventDaoPersistenceTest {
    private val now = LocalDateTime.of(2026, 11, 21, 10, 0)
    private val actor = CareEventActor("profile-example", "caregiver-example", "person-example", true)
    private val draft = CareEventDraft(
        kind = CareEventKind.APPOINTMENT, title = "Consulta ficticia",
        status = CareEventStatus.CONFIRMED, scheduledAt = now.plusDays(2),
        purpose = CareEventPurpose.MEDICAL_CONSULTATION, timeMode = CareEventTimeMode.EXACT,
        duration = CareEventDuration.ONE_HOUR, modality = CareEventModality.VIDEO,
        institution = "Institución ficticia", professional = "Profesional ficticio",
        contactInformation = "Contacto ficticio", instructions = "Nota ficticia",
        preparations = setOf(CareEventPreparation.PREVIOUS_STUDIES, CareEventPreparation.OTHER),
        otherPreparation = "Preparativo ficticio", reminderEnabled = true,
        reminderOffsetsMinutes = setOf(30, 120, 1440), showInToday = true,
    )

    @Test fun editsKeepIdentityDetailsAndImmutablePriorSnapshot() = runBlocking {
        val dao = QueryDouble()
        val create = CareEventCommand.Create("create", "event", draft)
        val original = dao.apply(actor, create, careEventFingerprint(create), now)
        val revisedDraft = original.toDomain().draft.copy(
            title = "Consulta reprogramada", scheduledAt = now.plusDays(4),
            scheduledDate = now.plusDays(4).toLocalDate(), showInToday = false,
            reminderOffsetsMinutes = setOf(120),
        )
        val revise = CareEventCommand.Revise("revise", original.id, 1, revisedDraft)
        val changed = dao.apply(actor, revise, careEventFingerprint(revise), now.plusMinutes(1))
        assertEquals(original.id, changed.id)
        assertEquals(2, changed.revision)
        assertEquals(1, dao.current.size)
        assertEquals(listOf(original, changed), dao.history(actor, original.id).map { it.snapshot })
        assertEquals(draft.professional, changed.toDomain().draft.professional)
        assertEquals(draft.preparations, changed.toDomain().draft.preparations)
        assertEquals(draft.contactInformation, changed.toDomain().draft.contactInformation)
        assertEquals(setOf(120), changed.toDomain().draft.reminderOffsetsMinutes)
        assertTrue(original.toDomain().draft.showInToday)
        assertFalse(changed.toDomain().draft.showInToday)
        val day = now.plusDays(4).toLocalDate()
        assertTrue(careEventsForDay(listOf(changed.toDomain()), actor.personUnderCareId, day).isEmpty())
        assertEquals(1, careCalendarEventsForDay(listOf(changed.toDomain()), actor.personUnderCareId, day).size)
    }

    @Test fun consultationClassificationKeepsPreviousAuditedSnapshot() = runBlocking {
        val dao = QueryDouble()
        val originalDraft = draft.copy(consultationType = CareConsultationType.UNSPECIFIED, isFollowUp = false)
        val create = CareEventCommand.Create("classification-create", "classified-event", originalDraft)
        val initial = dao.apply(actor, create, careEventFingerprint(create), now)
        val update = CareEventCommand.Revise("classification-update", initial.id, 1,
            initial.toDomain().draft.copy(consultationType = CareConsultationType.GENERAL_MEDICINE, isFollowUp = true))
        val revised = dao.apply(actor, update, careEventFingerprint(update), now.plusMinutes(1))
        val versions = dao.history(actor, initial.id).map { it.snapshot.toDomain() }
        assertEquals(listOf(1, 2), versions.map { it.revision })
        assertEquals(CareConsultationType.UNSPECIFIED, versions.first().draft.consultationType)
        assertFalse(versions.first().draft.isFollowUp)
        assertEquals(CareConsultationType.GENERAL_MEDICINE, revised.toDomain().draft.consultationType)
        assertTrue(revised.toDomain().draft.isFollowUp)
    }

    @Test fun retriesDoNotDuplicateAndStaleEditsCannotOverwrite() = runBlocking {
        val dao = QueryDouble()
        val create = CareEventCommand.Create("create", "event", draft)
        val first = dao.apply(actor, create, careEventFingerprint(create), now)
        val update = CareEventCommand.Revise("update", "event", 1, first.toDomain().draft.copy(title = "Nombre actualizado"))
        val current = dao.apply(actor, update, careEventFingerprint(update), now.plusMinutes(1))
        assertEquals(first, dao.apply(actor, create, careEventFingerprint(create), now.plusMinutes(2)))
        assertEquals(current, dao.load(actor).single())
        assertEquals(2, dao.history(actor, "event").size)
        val stale = update.copy(operationId = "stale", draft = update.draft.copy(title = "Otro nombre"))
        val failure = runCatching { dao.apply(actor, stale, careEventFingerprint(stale), now.plusMinutes(3)) }.exceptionOrNull()
        assertEquals(CareEventProblem.CONFLICT, (failure as CareEventValidationException).problem)
        assertEquals(current, dao.load(actor).single())
        assertEquals(2, dao.history(actor, "event").size)
    }

    @Test fun revokedConsentOrRelationshipPreventWriteAndRetry() = runBlocking {
        val dao = QueryDouble()
        val command = CareEventCommand.Create("create", "event", draft)
        dao.consent = false
        assertTrue(runCatching { dao.apply(actor, command, careEventFingerprint(command), now) }
            .exceptionOrNull() is CareEventSensitiveConsentException)
        assertTrue(dao.current.isEmpty())
        dao.consent = true
        dao.apply(actor, command, careEventFingerprint(command), now)
        dao.authorized = false
        assertTrue(runCatching { dao.apply(actor, command, careEventFingerprint(command), now) }
            .exceptionOrNull() is CareEventAuthorizationException)
        assertEquals(1, dao.history.size)
    }

    @Test fun savingSelectedPhotosUsesEventRevisionAndRetriesDoNotDuplicate() = runBlocking {
        val dao = QueryDouble()
        val command = CareEventCommand.Create("photo-create", "photo-event", draft)
        val photos = listOf(CareEventPhotoInput("image-1", byteArrayOf(1, 2)), CareEventPhotoInput("image-2", byteArrayOf(3, 4)))
        val first = dao.applyWithPhotos(actor, command, careEventFingerprint(command), now, photos)
        val retry = dao.applyWithPhotos(actor, command, careEventFingerprint(command), now.plusMinutes(1), photos)
        assertEquals(first, retry)
        assertEquals(1, dao.current.size)
        assertEquals(2, dao.photos.size)
        assertTrue(dao.photos.values.all { it.eventId == first.id && it.eventRevision == first.revision })
    }

    @Test fun maxFiveAndReplacementRetainRemovedPhotoForAudit() = runBlocking {
        val dao = QueryDouble()
        val create = CareEventCommand.Create("five", "event", draft)
        val initial = (1..5).map { CareEventPhotoInput("image-$it", byteArrayOf(it.toByte())) }
        val saved = dao.applyWithPhotos(actor, create, careEventFingerprint(create), now, initial)
        val excessive = CareEventCommand.Create("six", "other", draft)
        assertTrue(runCatching {
            dao.applyWithPhotos(actor, excessive, careEventFingerprint(excessive), now,
                (1..6).map { CareEventPhotoInput("other-$it", byteArrayOf(1)) })
        }.isFailure)
        val revise = CareEventCommand.Revise("replace", saved.id, saved.revision, saved.toDomain().draft)
        dao.applyWithPhotos(actor, revise, careEventFingerprint(revise), now.plusMinutes(1),
            listOf(CareEventPhotoInput("replacement", byteArrayOf(9), "image-1")), setOf("image-1"))
        assertEquals(dao.photos["image-1"]?.displayOrder, dao.photos["replacement"]?.displayOrder)
        assertEquals(listOf("replacement", "image-2", "image-3", "image-4", "image-5"),
            dao.photos.values.filter { it.removedAtRevision == null }.sortedBy { it.displayOrder }.map { it.id })
        assertEquals(6, dao.photos.size)
        assertEquals(2, dao.photos["image-1"]?.removedAtRevision)
        assertEquals(5, dao.photos.values.count { it.removedAtRevision == null })
        assertEquals(1, dao.photos["image-1"]?.eventRevision)
    }

    private class QueryDouble : CareEventDao() {
        val current = linkedMapOf<String, CareEventEntity>()
        val history = linkedMapOf<String, CareEventRevisionEntity>()
        val photos = linkedMapOf<String, CareEventPhotoEntity>()
        override suspend fun insertPhoto(photo: CareEventPhotoEntity) { check(photo.id !in photos); photos[photo.id] = photo }
        override suspend fun photoCount(event: String, profile: String, person: String) =
            photos.values.count { it.eventId == event && it.profileId == profile && it.personUnderCareId == person && it.removedAtRevision == null }
        override suspend fun markPhotoRemoved(id: String, event: String, profile: String, person: String, revision: Int): Int {
            val item = photos[id] ?: return 0
            if (item.eventId != event || item.profileId != profile || item.personUnderCareId != person ||
                (item.removedAtRevision != null && item.removedAtRevision != revision)) return 0
            photos[id] = item.copy(removedAtRevision = revision)
            return 1
        }
        override suspend fun existingPhoto(id: String, event: String, profile: String, person: String) =
            photos.values.count { it.id == id && it.eventId == event && it.profileId == profile && it.personUnderCareId == person }
        override suspend fun photoOrder(id: String, event: String, profile: String, person: String): Long? =
            photos[id]?.takeIf { it.eventId == event && it.profileId == profile && it.personUnderCareId == person }?.displayOrder
        override suspend fun nextPhotoOrder(event: String, profile: String, person: String): Long =
            (photos.values.filter { it.eventId == event && it.profileId == profile && it.personUnderCareId == person }
                .maxOfOrNull { it.displayOrder } ?: -1L) + 1
        var authorized = true
        var consent = true
        override suspend fun currentRelationship(profile: String, actor: String, person: String) = if (authorized) 1 else 0
        override suspend fun sensitiveAuthorized(profile: String, actor: String, person: String, version: String) = if (consent) 1 else 0
        override suspend fun records(profile: String, person: String) =
            current.values.filter { it.profileId == profile && it.personUnderCareId == person }
        override suspend fun operation(operationId: String) = history[operationId]
        override suspend fun revisions(id: String) = history.values.filter { it.eventId == id }.sortedBy { it.snapshot.revision }
        override suspend fun insert(entity: CareEventEntity) { check(entity.id !in current); current[entity.id] = entity }
        override suspend fun update(entity: CareEventEntity): Int {
            if (entity.id !in current) return 0
            current[entity.id] = entity
            return 1
        }
        override suspend fun insertRevision(entity: CareEventRevisionEntity) {
            check(entity.operationId !in history)
            history[entity.operationId] = entity
        }
    }
}
