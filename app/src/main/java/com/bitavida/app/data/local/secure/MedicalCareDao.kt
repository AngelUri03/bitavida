package com.bitavida.app.data.local.secure

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.bitavida.app.domain.usecase.MEDICATION_INTAKE_OPERATION_PREFIX

data class PersistedMedicalCare(
    val indications: List<MedicalIndicationEntity>,
    val indicationRevisions: List<MedicalIndicationRevisionEntity>,
    val medicationPlans: List<MedicationPlanEntity>,
    val medicationPlanRevisions: List<MedicationPlanRevisionEntity>,
    val scheduledDoses: List<ScheduledDoseEntity>,
    val medicationConfigurations: List<MedicationConfigurationEntity>,
    val medicationAdministrations: List<MedicationAdministrationEntity>,
    val medicationIntakeRecords: List<MedicationIntakeRecordEntity>,
    val medicationIntakeRevisions: List<MedicationIntakeRevisionEntity>,
)

internal class MedicalCareDatabaseIntegrityException(message: String) :
    IllegalStateException(message)

internal class MedicalCareConcurrentUpdateException(message: String) :
    IllegalStateException(message)

/** Ignores only generated revision ID and capture time, never request values or actor. */
internal fun sameMedicationIntakeOperation(
    previous: MedicationIntakeRecordEntity,
    original: MedicationIntakeRevisionEntity?,
    requested: MedicationIntakeRecordEntity,
    revision: MedicationIntakeRevisionEntity,
): Boolean = previous.id == requested.id &&
    previous.medicationPlanId == requested.medicationPlanId &&
    previous.createdByActorId == requested.createdByActorId &&
    original != null &&
    original.copy(id = revision.id, capturedAtEpochMillis = revision.capturedAtEpochMillis) == revision

@Dao
abstract class MedicalCareDao {
    @Query("SELECT * FROM medical_indications WHERE personUnderCareId = :personUnderCareId ORDER BY createdAtEpochMillis DESC")
    protected abstract suspend fun indications(personUnderCareId: String): List<MedicalIndicationEntity>

    @Query(
        """
        SELECT r.* FROM medical_indication_revisions r
        INNER JOIN medical_indications i ON i.id = r.indicationId
        WHERE i.personUnderCareId = :personUnderCareId
        ORDER BY r.indicationId, r.revisionNumber
        """,
    )
    protected abstract suspend fun indicationRevisions(
        personUnderCareId: String,
    ): List<MedicalIndicationRevisionEntity>

    @Query("SELECT * FROM medication_plans WHERE personUnderCareId = :personUnderCareId ORDER BY createdAtEpochMillis DESC")
    protected abstract suspend fun medicationPlans(personUnderCareId: String): List<MedicationPlanEntity>

    @Query(
        """
        SELECT r.* FROM medication_plan_revisions r
        INNER JOIN medication_plans p ON p.id = r.medicationPlanId
        WHERE p.personUnderCareId = :personUnderCareId
        ORDER BY r.medicationPlanId, r.revisionNumber
        """,
    )
    protected abstract suspend fun medicationPlanRevisions(
        personUnderCareId: String,
    ): List<MedicationPlanRevisionEntity>

    @Query(
        """
        SELECT d.* FROM scheduled_doses d
        INNER JOIN medication_plan_revisions r ON r.id = d.medicationPlanRevisionId
        INNER JOIN medication_plans p ON p.id = r.medicationPlanId
        WHERE p.personUnderCareId = :personUnderCareId
        ORDER BY d.medicationPlanRevisionId, d.localTimeMinutes
        """,
    )
    protected abstract suspend fun scheduledDoses(
        personUnderCareId: String,
    ): List<ScheduledDoseEntity>

    @Query(
        """
        SELECT c.* FROM medication_configurations c
        INNER JOIN medication_plan_revisions r ON r.id = c.medicationPlanRevisionId
        INNER JOIN medication_plans p ON p.id = r.medicationPlanId
        WHERE p.personUnderCareId = :personUnderCareId
        """,
    )
    protected abstract suspend fun medicationConfigurations(
        personUnderCareId: String,
    ): List<MedicationConfigurationEntity>

    @Query(
        """
        SELECT a.* FROM medication_administrations a
        INNER JOIN medication_plan_revisions r ON r.id = a.medicationPlanRevisionId
        INNER JOIN medication_plans p ON p.id = r.medicationPlanId
        WHERE p.personUnderCareId = :personUnderCareId
        """,
    )
    protected abstract suspend fun medicationAdministrations(
        personUnderCareId: String,
    ): List<MedicationAdministrationEntity>

    @Query(
        """
        SELECT i.* FROM medication_intake_records i
        INNER JOIN medication_plans p ON p.id = i.medicationPlanId
        WHERE p.personUnderCareId = :personUnderCareId
        ORDER BY i.createdAtEpochMillis DESC
        """,
    )
    protected abstract suspend fun medicationIntakeRecords(
        personUnderCareId: String,
    ): List<MedicationIntakeRecordEntity>

    @Query(
        """
        SELECT r.* FROM medication_intake_revisions r
        INNER JOIN medication_intake_records i ON i.id = r.intakeRecordId
        INNER JOIN medication_plans p ON p.id = i.medicationPlanId
        WHERE p.personUnderCareId = :personUnderCareId
        ORDER BY r.intakeRecordId, r.revisionNumber
        """,
    )
    protected abstract suspend fun medicationIntakeRevisions(
        personUnderCareId: String,
    ): List<MedicationIntakeRevisionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM sensitive_data_consents
        WHERE profileId = :profileId
            AND caregiverId = :caregiverId
            AND personUnderCareId = :personUnderCareId
            AND documentVersion = :documentVersion
            AND status = 'ACTIVE'
        """,
    )
    abstract suspend fun activeSensitiveDataConsentCount(
        profileId: String,
        caregiverId: String,
        personUnderCareId: String,
        documentVersion: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSensitiveDataConsent(entity: SensitiveDataConsentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertIndication(entity: MedicalIndicationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertIndicationRevision(entity: MedicalIndicationRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedicationPlan(entity: MedicationPlanEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedicationPlanRevision(entity: MedicationPlanRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertScheduledDoses(entities: List<ScheduledDoseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedicationConfiguration(
        entity: MedicationConfigurationEntity,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedicationAdministrations(
        entities: List<MedicationAdministrationEntity>,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedicationIntakeRecord(
        entity: MedicationIntakeRecordEntity,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedicationIntakeRevision(
        entity: MedicationIntakeRevisionEntity,
    )

    @Query(
        """
        UPDATE medical_indications
        SET currentRevisionNumber = :newRevisionNumber
        WHERE id = :indicationId AND currentRevisionNumber = :expectedRevisionNumber
        """,
    )
    protected abstract suspend fun advanceIndicationRevision(
        indicationId: String,
        expectedRevisionNumber: Int,
        newRevisionNumber: Int,
    ): Int

    @Query(
        """
        UPDATE medication_plans
        SET currentRevisionNumber = :newRevisionNumber
        WHERE id = :medicationPlanId AND currentRevisionNumber = :expectedRevisionNumber
        """,
    )
    protected abstract suspend fun advanceMedicationPlanRevision(
        medicationPlanId: String,
        expectedRevisionNumber: Int,
        newRevisionNumber: Int,
    ): Int

    @Query(
        """
        SELECT c.status
        FROM medication_plans p
        INNER JOIN medication_plan_revisions r
            ON r.medicationPlanId = p.id
            AND r.revisionNumber = p.currentRevisionNumber
        INNER JOIN medication_configurations c
            ON c.medicationPlanRevisionId = r.id
        WHERE p.id = :medicationPlanId
        """,
    )
    protected abstract suspend fun currentMedicationPlanStatus(
        medicationPlanId: String,
    ): String?

    @Query(
        """
        UPDATE medication_intake_records
        SET currentRevisionNumber = :newRevisionNumber
        WHERE id = :recordId AND currentRevisionNumber = :expectedRevisionNumber
        """,
    )
    protected abstract suspend fun advanceMedicationIntakeRevision(
        recordId: String,
        expectedRevisionNumber: Int,
        newRevisionNumber: Int,
    ): Int

    @Transaction
    open suspend fun load(personUnderCareId: String): PersistedMedicalCare =
        PersistedMedicalCare(
            indications = indications(personUnderCareId),
            indicationRevisions = indicationRevisions(personUnderCareId),
            medicationPlans = medicationPlans(personUnderCareId),
            medicationPlanRevisions = medicationPlanRevisions(personUnderCareId),
            scheduledDoses = scheduledDoses(personUnderCareId),
            medicationConfigurations = medicationConfigurations(personUnderCareId),
            medicationAdministrations = medicationAdministrations(personUnderCareId),
            medicationIntakeRecords = medicationIntakeRecords(personUnderCareId),
            medicationIntakeRevisions = medicationIntakeRevisions(personUnderCareId),
        )

    @Transaction
    open suspend fun createIndication(
        indication: MedicalIndicationEntity,
        revision: MedicalIndicationRevisionEntity,
    ) {
        insertIndication(indication)
        insertIndicationRevision(revision)
    }

    @Transaction
    open suspend fun appendIndicationRevision(
        indicationId: String,
        expectedRevisionNumber: Int,
        revision: MedicalIndicationRevisionEntity,
    ) {
        val updated = advanceIndicationRevision(
            indicationId,
            expectedRevisionNumber,
            revision.revisionNumber,
        )
        if (updated != 1) {
            throw MedicalCareConcurrentUpdateException(
                "Medical indication revision changed concurrently.",
            )
        }
        insertIndicationRevision(revision)
    }

    @Transaction
    open suspend fun createMedicationPlan(
        plan: MedicationPlanEntity,
        revision: MedicationPlanRevisionEntity,
        scheduledDoses: List<ScheduledDoseEntity>,
        configuration: MedicationConfigurationEntity?,
        administrations: List<MedicationAdministrationEntity>,
    ) {
        insertMedicationPlan(plan)
        insertMedicationPlanRevision(revision)
        insertScheduledDoses(scheduledDoses)
        configuration?.let { insertMedicationConfiguration(it) }
        insertMedicationAdministrations(administrations)
    }

    @Transaction
    open suspend fun appendMedicationPlanRevision(
        medicationPlanId: String,
        expectedRevisionNumber: Int,
        revision: MedicationPlanRevisionEntity,
        scheduledDoses: List<ScheduledDoseEntity>,
        configuration: MedicationConfigurationEntity?,
        administrations: List<MedicationAdministrationEntity>,
    ) {
        if (
            currentMedicationPlanStatus(medicationPlanId) == "COMPLETED" &&
            revision.changeKind != "REMOVED"
        ) {
            throw MedicalCareDatabaseIntegrityException(
                "A completed medication period cannot be revised.",
            )
        }
        val updated = advanceMedicationPlanRevision(
            medicationPlanId,
            expectedRevisionNumber,
            revision.revisionNumber,
        )
        if (updated != 1) {
            throw MedicalCareConcurrentUpdateException(
                "Medication plan revision changed concurrently.",
            )
        }
        insertMedicationPlanRevision(revision)
        insertScheduledDoses(scheduledDoses)
        configuration?.let { insertMedicationConfiguration(it) }
        insertMedicationAdministrations(administrations)
    }

    @Query("SELECT * FROM medication_intake_records WHERE id = :recordId LIMIT 1")
    protected abstract suspend fun medicationIntakeById(recordId: String): MedicationIntakeRecordEntity?

    @Query("SELECT * FROM medication_intake_revisions WHERE intakeRecordId = :recordId AND revisionNumber = 1 LIMIT 1")
    protected abstract suspend fun firstMedicationIntakeRevision(recordId: String): MedicationIntakeRevisionEntity?

    @Transaction
    open suspend fun createMedicationIntake(
        record: MedicationIntakeRecordEntity,
        revision: MedicationIntakeRevisionEntity,
    ) {
        // The operation ID is the record primary key. The read and insert share
        // one transaction, so concurrent retries cannot create two records.
        if (record.id.startsWith(MEDICATION_INTAKE_OPERATION_PREFIX)) {
            val previous = medicationIntakeById(record.id)
            if (previous != null) {
                val original = firstMedicationIntakeRevision(record.id)
                if (!sameMedicationIntakeOperation(previous, original, record, revision)) {
                    throw MedicalCareConcurrentUpdateException("Medication intake operation identity conflict.")
                }
                return
            }
        }
        insertMedicationIntakeRecord(record)
        insertMedicationIntakeRevision(revision)
    }

    @Transaction
    open suspend fun appendMedicationIntakeRevision(
        recordId: String,
        expectedRevisionNumber: Int,
        revision: MedicationIntakeRevisionEntity,
    ) {
        val updated = advanceMedicationIntakeRevision(
            recordId = recordId,
            expectedRevisionNumber = expectedRevisionNumber,
            newRevisionNumber = revision.revisionNumber,
        )
        if (updated != 1) {
            throw MedicalCareConcurrentUpdateException(
                "Medication intake changed concurrently.",
            )
        }
        insertMedicationIntakeRevision(revision)
    }
}
