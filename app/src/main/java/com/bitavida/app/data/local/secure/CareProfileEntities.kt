package com.bitavida.app.data.local.secure

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "caregivers")
data class CaregiverEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val declarationSource: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "persons_under_care",
    foreignKeys = [
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["declaredByCaregiverId"],
        ),
    ],
    indices = [Index(value = ["declaredByCaregiverId"])],
)
data class PersonUnderCareEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val declaredByCaregiverId: String,
    val declarationSource: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "care_relationships",
    foreignKeys = [
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["caregiverId"],
        ),
        ForeignKey(
            entity = PersonUnderCareEntity::class,
            parentColumns = ["id"],
            childColumns = ["personUnderCareId"],
        ),
    ],
    indices = [
        Index(value = ["caregiverId"]),
        Index(value = ["personUnderCareId"]),
    ],
)
data class CareRelationshipEntity(
    @PrimaryKey val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val usage: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "consent_records",
    foreignKeys = [
        ForeignKey(
            entity = CareRelationshipEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["caregiverId"],
        ),
        ForeignKey(
            entity = PersonUnderCareEntity::class,
            parentColumns = ["id"],
            childColumns = ["personUnderCareId"],
        ),
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["caregiverId"]),
        Index(value = ["personUnderCareId"]),
    ],
)
data class ConsentRecordEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val kind: String,
    val authorizationDeclaration: String,
    val status: String,
    val recordedAtEpochMillis: Long,
    val revokedAtEpochMillis: Long?,
)

@Entity(
    tableName = "legal_acceptance_records",
    foreignKeys = [
        ForeignKey(
            entity = CareRelationshipEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["actorId"],
        ),
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["actorId"]),
        Index(value = ["document", "documentVersion"]),
    ],
)
data class LegalAcceptanceEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val actorId: String,
    val document: String,
    val documentVersion: String,
    val decision: String,
    val status: String,
    val recordedAtEpochMillis: Long,
    val revokedAtEpochMillis: Long?,
)
