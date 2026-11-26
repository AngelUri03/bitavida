package com.bitavida.app.domain.repository

import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MedicalIndication
import com.bitavida.app.domain.model.MedicalIndicationRevision
import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationIntakeRevision
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanRevision

interface MedicalCareRepository {
    suspend fun load(personUnderCareId: String): MedicalCareOverview

    suspend fun createIndication(indication: MedicalIndication)

    suspend fun appendIndicationRevision(
        indicationId: String,
        expectedRevisionNumber: Int,
        revision: MedicalIndicationRevision,
    )

    suspend fun hasActiveSensitiveDataConsent(
        profileId: String,
        caregiverId: String,
        personUnderCareId: String,
        documentVersion: String,
    ): Boolean

    suspend fun recordSensitiveDataConsent(
        profileId: String,
        caregiverId: String,
        personUnderCareId: String,
        documentVersion: String,
        acceptedAtEpochMillis: Long,
    )

    suspend fun createMedicationPlan(plan: MedicationPlan)

    suspend fun appendMedicationPlanRevision(
        medicationPlanId: String,
        expectedRevisionNumber: Int,
        revision: MedicationPlanRevision,
    )

    suspend fun createMedicationIntake(record: MedicationIntakeRecord)

    suspend fun appendMedicationIntakeRevision(
        recordId: String,
        expectedRevisionNumber: Int,
        revision: MedicationIntakeRevision,
    )
}

sealed class MedicalCareRepositoryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class MedicalCareStorageUnavailableException(cause: Throwable? = null) :
    MedicalCareRepositoryException("Encrypted medical care storage is unavailable.", cause)

class MedicalCareIntegrityException(cause: Throwable? = null) :
    MedicalCareRepositoryException("Stored medical care data is incomplete or invalid.", cause)

class MedicalCareConcurrentChangeException :
    MedicalCareRepositoryException("The record changed before this revision could be stored.")
