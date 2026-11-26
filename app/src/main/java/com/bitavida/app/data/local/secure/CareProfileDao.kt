package com.bitavida.app.data.local.secure

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

data class PersistedCareProfile(
    val relationship: CareRelationshipEntity,
    val caregiver: CaregiverEntity,
    val personUnderCare: PersonUnderCareEntity,
    val consent: ConsentRecordEntity,
    val legalAcceptances: List<LegalAcceptanceEntity>,
)

data class CareProfileWriteBundle(
    val relationship: CareRelationshipEntity,
    val caregiver: CaregiverEntity,
    val personUnderCare: PersonUnderCareEntity,
    val consent: ConsentRecordEntity,
    val legalAcceptances: List<LegalAcceptanceEntity>,
)

internal class CareProfileDatabaseIntegrityException(message: String) :
    IllegalStateException(message)

@Dao
abstract class CareProfileDao {
    @Query("SELECT COUNT(*) FROM care_relationships")
    protected abstract suspend fun relationshipCount(): Int

    @Query("SELECT * FROM care_relationships LIMIT 2")
    protected abstract suspend fun relationships(): List<CareRelationshipEntity>

    @Query("SELECT * FROM caregivers WHERE id = :id LIMIT 1")
    protected abstract suspend fun caregiver(id: String): CaregiverEntity?

    @Query("SELECT * FROM persons_under_care WHERE id = :id LIMIT 1")
    protected abstract suspend fun personUnderCare(id: String): PersonUnderCareEntity?

    @Query("SELECT * FROM consent_records WHERE profileId = :profileId")
    protected abstract suspend fun consents(profileId: String): List<ConsentRecordEntity>

    @Query("SELECT * FROM legal_acceptance_records WHERE profileId = :profileId")
    protected abstract suspend fun legalAcceptances(
        profileId: String,
    ): List<LegalAcceptanceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCaregiver(entity: CaregiverEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPersonUnderCare(entity: PersonUnderCareEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRelationship(entity: CareRelationshipEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertConsent(entity: ConsentRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertLegalAcceptances(
        entities: List<LegalAcceptanceEntity>,
    )

    @Query("SELECT * FROM care_profile_name_revisions WHERE operationId = :operationId LIMIT 1")
    protected abstract suspend fun nameRevision(operationId: String): CareProfileNameRevisionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertNameRevision(revision: CareProfileNameRevisionEntity)

    @Query("UPDATE caregivers SET displayName = :name WHERE id = :id AND displayName = :expected")
    protected abstract suspend fun updateCaregiverName(id: String, expected: String, name: String): Int

    @Query("UPDATE persons_under_care SET displayName = :name WHERE id = :id AND displayName = :expected")
    protected abstract suspend fun updatePersonName(id: String, expected: String, name: String): Int

    @Transaction
    open suspend fun updateNames(revision: CareProfileNameRevisionEntity): String {
        val stored = load() ?: return "UNAUTHORIZED"
        if (stored.relationship.profileId != revision.profileId ||
            stored.relationship.caregiverId != revision.caregiverId ||
            stored.relationship.personUnderCareId != revision.personUnderCareId ||
            stored.consent.status != "ACTIVE") return "UNAUTHORIZED"
        val prior = nameRevision(revision.operationId)
        if (prior != null) return if (prior == revision) "STORED" else "CONFLICT"
        if (stored.caregiver.displayName != revision.previousCaregiverName ||
            stored.personUnderCare.displayName != revision.previousPersonName) return "CONFLICT"
        if (revision.caregiverName == revision.previousCaregiverName &&
            revision.personName == revision.previousPersonName) return "STORED"
        insertNameRevision(revision)
        check(updateCaregiverName(revision.caregiverId, revision.previousCaregiverName, revision.caregiverName) == 1)
        check(updatePersonName(revision.personUnderCareId, revision.previousPersonName, revision.personName) == 1)
        return "STORED"
    }

    @Transaction
    open suspend fun create(bundle: CareProfileWriteBundle): Boolean {
        if (relationshipCount() != 0) return false
        insertCaregiver(bundle.caregiver)
        insertPersonUnderCare(bundle.personUnderCare)
        insertRelationship(bundle.relationship)
        insertConsent(bundle.consent)
        insertLegalAcceptances(bundle.legalAcceptances)
        return true
    }

    @Transaction
    open suspend fun load(): PersistedCareProfile? {
        val relationships = relationships()
        if (relationships.isEmpty()) return null
        if (relationships.size != 1) {
            throw CareProfileDatabaseIntegrityException(
                "Expected one care relationship for this installation.",
            )
        }
        val relationship = relationships.single()
        val caregiver = caregiver(relationship.caregiverId)
            ?: throw CareProfileDatabaseIntegrityException("Missing caregiver.")
        val person = personUnderCare(relationship.personUnderCareId)
            ?: throw CareProfileDatabaseIntegrityException("Missing person under care.")
        val consents = consents(relationship.profileId)
        if (consents.size != 1) {
            throw CareProfileDatabaseIntegrityException(
                "Expected one caregiver authorization record.",
            )
        }
        return PersistedCareProfile(
            relationship = relationship,
            caregiver = caregiver,
            personUnderCare = person,
            consent = consents.single(),
            legalAcceptances = legalAcceptances(relationship.profileId),
        )
    }
}
