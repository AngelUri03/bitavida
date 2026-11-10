package com.bitavida.app.data.careprofile

import android.content.Context
import com.bitavida.app.data.local.secure.CareProfileDatabaseIntegrityException
import com.bitavida.app.data.local.secure.CareProfileWriteBundle
import com.bitavida.app.data.local.secure.CareRelationshipEntity
import com.bitavida.app.data.local.secure.CaregiverEntity
import com.bitavida.app.data.local.secure.ConsentRecordEntity
import com.bitavida.app.data.local.secure.LegalAcceptanceEntity
import com.bitavida.app.data.local.secure.PersistedCareProfile
import com.bitavida.app.data.local.secure.PersonUnderCareEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.data.local.secure.SecurePersistenceException
import com.bitavida.app.data.local.secure.WrappedDatabaseKeyException
import com.bitavida.app.data.local.secure.WrappingKeyUnavailableException
import com.bitavida.app.domain.model.AuthorizationDeclaration
import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.model.CareProfileUsage
import com.bitavida.app.domain.model.Caregiver
import com.bitavida.app.domain.model.ConsentKind
import com.bitavida.app.domain.model.ConsentRecord
import com.bitavida.app.domain.model.ConsentStatus
import com.bitavida.app.domain.model.LegalAcceptanceRecord
import com.bitavida.app.domain.model.LegalAcceptanceStatus
import com.bitavida.app.domain.model.LegalDocument
import com.bitavida.app.domain.model.LegalDocumentDecision
import com.bitavida.app.domain.model.PersonUnderCare
import com.bitavida.app.domain.model.ProfileDeclarationSource
import com.bitavida.app.domain.repository.CareProfileIntegrityException
import com.bitavida.app.domain.repository.CareProfileKeyUnavailableException
import com.bitavida.app.domain.repository.CareProfileRepository
import com.bitavida.app.domain.repository.CareProfileRepositoryException
import com.bitavida.app.domain.repository.CareProfileStorageUnavailableException
import com.bitavida.app.domain.repository.CreateCareProfileStorageResult
import kotlinx.coroutines.CancellationException

class EncryptedCareProfileRepository(
    context: Context,
) : CareProfileRepository {
    private val applicationContext = context.applicationContext

    override suspend fun create(profile: CareProfile): CreateCareProfileStorageResult =
        repositoryCall {
            val stored = SecureDatabaseProvider.open(applicationContext)
                .careProfileDao()
                .create(profile.toWriteBundle())
            if (stored) {
                CreateCareProfileStorageResult.Stored
            } else {
                CreateCareProfileStorageResult.ProfileAlreadyExists
            }
        }

    override suspend fun get(): CareProfile? = repositoryCall {
        SecureDatabaseProvider.open(applicationContext)
            .careProfileDao()
            .load()
            ?.toDomain()
    }

    private suspend fun <T> repositoryCall(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: CareProfileRepositoryException) {
        throw error
    } catch (error: WrappingKeyUnavailableException) {
        throw CareProfileKeyUnavailableException(error)
    } catch (error: WrappedDatabaseKeyException) {
        throw CareProfileKeyUnavailableException(error)
    } catch (error: CareProfileDatabaseIntegrityException) {
        throw CareProfileIntegrityException(error)
    } catch (error: IllegalArgumentException) {
        throw CareProfileIntegrityException(error)
    } catch (error: SecurePersistenceException) {
        throw CareProfileStorageUnavailableException(error)
    } catch (error: Exception) {
        throw CareProfileStorageUnavailableException(error)
    }
}

private fun CareProfile.toWriteBundle(): CareProfileWriteBundle =
    CareProfileWriteBundle(
        relationship = CareRelationshipEntity(
            profileId = id,
            caregiverId = caregiver.id,
            personUnderCareId = personUnderCare.id,
            usage = usage.name,
            createdAtEpochMillis = createdAtEpochMillis,
        ),
        caregiver = CaregiverEntity(
            id = caregiver.id,
            displayName = caregiver.displayName,
            declarationSource = caregiver.declarationSource.name,
            createdAtEpochMillis = createdAtEpochMillis,
        ),
        personUnderCare = PersonUnderCareEntity(
            id = personUnderCare.id,
            displayName = personUnderCare.displayName,
            declaredByCaregiverId = personUnderCare.declaredByCaregiverId,
            declarationSource = personUnderCare.declarationSource.name,
            createdAtEpochMillis = createdAtEpochMillis,
        ),
        consent = ConsentRecordEntity(
            id = consent.id,
            profileId = id,
            caregiverId = consent.caregiverId,
            personUnderCareId = consent.personUnderCareId,
            kind = consent.kind.name,
            authorizationDeclaration = consent.authorizationDeclaration.name,
            status = consent.status.name,
            recordedAtEpochMillis = consent.recordedAtEpochMillis,
            revokedAtEpochMillis = consent.revokedAtEpochMillis,
        ),
        legalAcceptances = legalAcceptances.map { acceptance ->
            LegalAcceptanceEntity(
                id = acceptance.id,
                profileId = id,
                actorId = acceptance.actorId,
                document = acceptance.document.name,
                documentVersion = acceptance.documentVersion,
                decision = acceptance.decision.name,
                status = acceptance.status.name,
                recordedAtEpochMillis = acceptance.recordedAtEpochMillis,
                revokedAtEpochMillis = acceptance.revokedAtEpochMillis,
            )
        },
    )

private fun PersistedCareProfile.toDomain(): CareProfile =
    CareProfile(
        id = relationship.profileId,
        caregiver = Caregiver(
            id = caregiver.id,
            displayName = caregiver.displayName,
            declarationSource = enumValueOf<ProfileDeclarationSource>(
                caregiver.declarationSource,
            ),
        ),
        personUnderCare = PersonUnderCare(
            id = personUnderCare.id,
            displayName = personUnderCare.displayName,
            declaredByCaregiverId = personUnderCare.declaredByCaregiverId,
            declarationSource = enumValueOf<ProfileDeclarationSource>(
                personUnderCare.declarationSource,
            ),
        ),
        consent = ConsentRecord(
            id = consent.id,
            caregiverId = consent.caregiverId,
            personUnderCareId = consent.personUnderCareId,
            kind = enumValueOf<ConsentKind>(consent.kind),
            authorizationDeclaration = enumValueOf<AuthorizationDeclaration>(
                consent.authorizationDeclaration,
            ),
            status = enumValueOf<ConsentStatus>(consent.status),
            recordedAtEpochMillis = consent.recordedAtEpochMillis,
            revokedAtEpochMillis = consent.revokedAtEpochMillis,
        ),
        legalAcceptances = legalAcceptances.map { acceptance ->
            LegalAcceptanceRecord(
                id = acceptance.id,
                actorId = acceptance.actorId,
                document = enumValueOf<LegalDocument>(acceptance.document),
                documentVersion = acceptance.documentVersion,
                decision = enumValueOf<LegalDocumentDecision>(acceptance.decision),
                status = enumValueOf<LegalAcceptanceStatus>(acceptance.status),
                recordedAtEpochMillis = acceptance.recordedAtEpochMillis,
                revokedAtEpochMillis = acceptance.revokedAtEpochMillis,
            )
        },
        createdAtEpochMillis = relationship.createdAtEpochMillis,
        usage = enumValueOf<CareProfileUsage>(relationship.usage),
    )
