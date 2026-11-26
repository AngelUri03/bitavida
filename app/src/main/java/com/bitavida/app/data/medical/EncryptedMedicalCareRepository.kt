package com.bitavida.app.data.medical

import android.content.Context
import com.bitavida.app.data.local.secure.MedicalCareConcurrentUpdateException
import com.bitavida.app.data.local.secure.MedicalIndicationEntity
import com.bitavida.app.data.local.secure.MedicalIndicationRevisionEntity
import com.bitavida.app.data.local.secure.MedicationAdministrationEntity
import com.bitavida.app.data.local.secure.MedicationConfigurationEntity
import com.bitavida.app.data.local.secure.MedicationIntakeRecordEntity
import com.bitavida.app.data.local.secure.MedicationIntakeRevisionEntity
import com.bitavida.app.data.local.secure.MedicationPlanEntity
import com.bitavida.app.data.local.secure.MedicationPlanRevisionEntity
import com.bitavida.app.data.local.secure.PersistedMedicalCare
import com.bitavida.app.data.local.secure.ScheduledDoseEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.data.local.secure.SensitiveDataConsentEntity
import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.InformationOrigin
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MedicalIndication
import com.bitavida.app.domain.model.MedicalIndicationChangeKind
import com.bitavida.app.domain.model.MedicalIndicationRevision
import com.bitavida.app.domain.model.MedicalIndicationStatus
import com.bitavida.app.domain.model.MedicationActivityRelation
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationCycle
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationHourlyIntervalMode
import com.bitavida.app.domain.model.MedicationIntakeChangeKind
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationIntakeRevision
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationReminderEffectiveState
import com.bitavida.app.domain.model.MedicationReminderPreference
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.ScheduledDose
import com.bitavida.app.domain.repository.MedicalCareConcurrentChangeException
import com.bitavida.app.domain.repository.MedicalCareIntegrityException
import com.bitavida.app.domain.repository.MedicalCareRepository
import com.bitavida.app.domain.repository.MedicalCareRepositoryException
import com.bitavida.app.domain.repository.MedicalCareStorageUnavailableException
import kotlinx.coroutines.CancellationException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class EncryptedMedicalCareRepository(
    context: Context,
) : MedicalCareRepository {
    private val applicationContext = context.applicationContext

    override suspend fun load(personUnderCareId: String): MedicalCareOverview =
        repositoryCall {
            SecureDatabaseProvider.open(applicationContext)
                .medicalCareDao()
                .load(personUnderCareId)
                .toDomain()
        }

    override suspend fun createIndication(indication: MedicalIndication) {
        repositoryCall {
            require(indication.revisions.size == 1)
            SecureDatabaseProvider.open(applicationContext)
                .medicalCareDao()
                .createIndication(
                    indication.toEntity(),
                    indication.currentRevision.toEntity(),
                )
        }
    }

    override suspend fun appendIndicationRevision(
        indicationId: String,
        expectedRevisionNumber: Int,
        revision: MedicalIndicationRevision,
    ) {
        repositoryCall {
            require(revision.indicationId == indicationId)
            require(revision.revisionNumber == expectedRevisionNumber + 1)
            SecureDatabaseProvider.open(applicationContext)
                .medicalCareDao()
                .appendIndicationRevision(
                    indicationId,
                    expectedRevisionNumber,
                    revision.toEntity(),
                )
        }
    }

    override suspend fun hasActiveSensitiveDataConsent(
        profileId: String,
        caregiverId: String,
        personUnderCareId: String,
        documentVersion: String,
    ): Boolean = repositoryCall {
        SecureDatabaseProvider.open(applicationContext)
            .medicalCareDao()
            .activeSensitiveDataConsentCount(
                profileId,
                caregiverId,
                personUnderCareId,
                documentVersion,
            ) == 1
    }

    override suspend fun recordSensitiveDataConsent(
        profileId: String,
        caregiverId: String,
        personUnderCareId: String,
        documentVersion: String,
        acceptedAtEpochMillis: Long,
    ) {
        repositoryCall {
            SecureDatabaseProvider.open(applicationContext)
                .medicalCareDao()
                .insertSensitiveDataConsent(
                    SensitiveDataConsentEntity(
                        id = UUID.randomUUID().toString(),
                        profileId = profileId,
                        caregiverId = caregiverId,
                        personUnderCareId = personUnderCareId,
                        documentVersion = documentVersion,
                        acceptedAtEpochMillis = acceptedAtEpochMillis,
                        status = "ACTIVE",
                    ),
                )
        }
    }

    override suspend fun createMedicationPlan(plan: MedicationPlan) {
        repositoryCall {
            require(plan.revisions.size == 1)
            val revision = plan.currentRevision
            SecureDatabaseProvider.open(applicationContext)
                .medicalCareDao()
                .createMedicationPlan(
                    plan.toEntity(),
                    revision.toEntity(),
                    revision.scheduledDoses.map(ScheduledDose::toEntity),
                    revision.configuration?.toEntity(revision.id),
                    revision.configuration?.administrations.orEmpty().map {
                        it.toEntity(revision.id)
                    },
                )
        }
    }

    override suspend fun appendMedicationPlanRevision(
        medicationPlanId: String,
        expectedRevisionNumber: Int,
        revision: MedicationPlanRevision,
    ) {
        repositoryCall {
            require(revision.medicationPlanId == medicationPlanId)
            require(revision.revisionNumber == expectedRevisionNumber + 1)
            SecureDatabaseProvider.open(applicationContext)
                .medicalCareDao()
                .appendMedicationPlanRevision(
                    medicationPlanId,
                    expectedRevisionNumber,
                    revision.toEntity(),
                    revision.scheduledDoses.map(ScheduledDose::toEntity),
                    revision.configuration?.toEntity(revision.id),
                    revision.configuration?.administrations.orEmpty().map {
                        it.toEntity(revision.id)
                    },
                )
        }
    }

    override suspend fun createMedicationIntake(record: MedicationIntakeRecord) {
        repositoryCall {
            require(record.revisions.size == 1)
            SecureDatabaseProvider.open(applicationContext)
                .medicalCareDao()
                .createMedicationIntake(
                    record = record.toEntity(),
                    revision = record.currentRevision.toEntity(),
                )
        }
    }

    override suspend fun appendMedicationIntakeRevision(
        recordId: String,
        expectedRevisionNumber: Int,
        revision: MedicationIntakeRevision,
    ) {
        repositoryCall {
            require(revision.intakeRecordId == recordId)
            require(revision.revisionNumber == expectedRevisionNumber + 1)
            SecureDatabaseProvider.open(applicationContext)
                .medicalCareDao()
                .appendMedicationIntakeRevision(
                    recordId = recordId,
                    expectedRevisionNumber = expectedRevisionNumber,
                    revision = revision.toEntity(),
                )
        }
    }

    private suspend fun <T> repositoryCall(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: MedicalCareRepositoryException) {
        throw error
    } catch (error: MedicalCareConcurrentUpdateException) {
        throw MedicalCareConcurrentChangeException()
    } catch (error: IllegalArgumentException) {
        throw MedicalCareIntegrityException(error)
    } catch (error: Exception) {
        throw MedicalCareStorageUnavailableException(error)
    }
}

private fun PersistedMedicalCare.toDomain(): MedicalCareOverview {
    val revisionsByIndication = indicationRevisions.groupBy { it.indicationId }
    val indications = indications.map { indication ->
        val revisions = revisionsByIndication[indication.id].orEmpty()
            .sortedBy { it.revisionNumber }
            .map(MedicalIndicationRevisionEntity::toDomain)
        require(revisions.size == indication.currentRevisionNumber)
        MedicalIndication(
            id = indication.id,
            personUnderCareId = indication.personUnderCareId,
            origin = enumValueOf<InformationOrigin>(indication.origin),
            createdAtEpochMillis = indication.createdAtEpochMillis,
            createdByActorId = indication.createdByActorId,
            contentMode = enumValueOf<ClinicalContentMode>(indication.contentMode),
            revisions = revisions,
        )
    }
    require(revisionsByIndication.keys == indications.map { it.id }.toSet())

    val dosesByRevision = scheduledDoses.groupBy { it.medicationPlanRevisionId }
    val configurationsByRevision =
        medicationConfigurations.associateBy { it.medicationPlanRevisionId }
    val administrationsByRevision =
        medicationAdministrations.groupBy { it.medicationPlanRevisionId }
    val revisionsByPlan = medicationPlanRevisions.groupBy { it.medicationPlanId }
    val plans = medicationPlans.map { plan ->
        val revisions = revisionsByPlan[plan.id].orEmpty()
            .sortedBy { it.revisionNumber }
            .map { revision ->
                revision.toDomain(
                    doses = dosesByRevision[revision.id].orEmpty(),
                    configuration = configurationsByRevision[revision.id],
                    administrations =
                        administrationsByRevision[revision.id].orEmpty(),
                )
            }
        require(revisions.size == plan.currentRevisionNumber)
        require(
            plan.medicalIndicationId == null ||
                indications.any { it.id == plan.medicalIndicationId },
        )
        MedicationPlan(
            id = plan.id,
            medicationSeriesId = plan.medicationSeriesId ?: plan.id,
            personUnderCareId = plan.personUnderCareId,
            medicalIndicationId = plan.medicalIndicationId,
            medicalContentOrigin = enumValueOf<InformationOrigin>(
                plan.medicalContentOrigin,
            ),
            scheduleOrigin = enumValueOf<InformationOrigin>(plan.scheduleOrigin),
            createdAtEpochMillis = plan.createdAtEpochMillis,
            createdByActorId = plan.createdByActorId,
            contentMode = enumValueOf<ClinicalContentMode>(plan.contentMode),
            revisions = revisions,
        )
    }
    require(revisionsByPlan.keys == plans.map { it.id }.toSet())
    val knownRevisionIds = medicationPlanRevisions.map { it.id }.toSet()
    require(dosesByRevision.keys.all(knownRevisionIds::contains))
    require(configurationsByRevision.keys.all(knownRevisionIds::contains))
    require(administrationsByRevision.keys.all(knownRevisionIds::contains))

    val intakeRevisionsByRecord =
        medicationIntakeRevisions.groupBy { it.intakeRecordId }
    val intakes = medicationIntakeRecords.map { record ->
        val revisions = intakeRevisionsByRecord[record.id].orEmpty()
            .sortedBy { it.revisionNumber }
            .map(MedicationIntakeRevisionEntity::toDomain)
        require(revisions.size == record.currentRevisionNumber)
        require(plans.any { it.id == record.medicationPlanId })
        MedicationIntakeRecord(
            id = record.id,
            medicationPlanId = record.medicationPlanId,
            createdAtEpochMillis = record.createdAtEpochMillis,
            createdByActorId = record.createdByActorId,
            revisions = revisions,
        )
    }
    require(
        intakeRevisionsByRecord.keys ==
            medicationIntakeRecords.map { it.id }.toSet(),
    )
    return MedicalCareOverview(indications, plans, intakes)
}

private fun MedicalIndication.toEntity() = MedicalIndicationEntity(
    id = id,
    personUnderCareId = personUnderCareId,
    origin = origin.name,
    createdAtEpochMillis = createdAtEpochMillis,
    createdByActorId = createdByActorId,
    contentMode = contentMode.name,
    currentRevisionNumber = currentRevision.revisionNumber,
)

private fun MedicalIndicationRevision.toEntity() = MedicalIndicationRevisionEntity(
    id = id,
    indicationId = indicationId,
    revisionNumber = revisionNumber,
    originalText = originalText,
    indicatedOnEpochDay = indicatedOn.toEpochDay(),
    capturedAtEpochMillis = capturedAtEpochMillis,
    declaredProfessionalOrSource = declaredProfessionalOrSource,
    validFromEpochDay = validFrom?.toEpochDay(),
    validUntilEpochDay = validUntil?.toEpochDay(),
    status = status.name,
    changeKind = changeKind.name,
    changeReason = changeReason,
    authorActorId = authorActorId,
)

private fun MedicalIndicationRevisionEntity.toDomain() = MedicalIndicationRevision(
    id = id,
    indicationId = indicationId,
    revisionNumber = revisionNumber,
    originalText = originalText,
    indicatedOn = LocalDate.ofEpochDay(indicatedOnEpochDay),
    capturedAtEpochMillis = capturedAtEpochMillis,
    declaredProfessionalOrSource = declaredProfessionalOrSource,
    validFrom = validFromEpochDay?.let(LocalDate::ofEpochDay),
    validUntil = validUntilEpochDay?.let(LocalDate::ofEpochDay),
    status = enumValueOf<MedicalIndicationStatus>(status),
    changeKind = enumValueOf<MedicalIndicationChangeKind>(changeKind),
    changeReason = changeReason,
    authorActorId = authorActorId,
)

private fun MedicationPlan.toEntity() = MedicationPlanEntity(
    id = id,
    medicationSeriesId = medicationSeriesId,
    personUnderCareId = personUnderCareId,
    medicalIndicationId = medicalIndicationId,
    medicalContentOrigin = medicalContentOrigin.name,
    scheduleOrigin = scheduleOrigin.name,
    createdAtEpochMillis = createdAtEpochMillis,
    createdByActorId = createdByActorId,
    contentMode = contentMode.name,
    currentRevisionNumber = currentRevision.revisionNumber,
)

private fun MedicationPlanRevision.toEntity() = MedicationPlanRevisionEntity(
    id = id,
    medicationPlanId = medicationPlanId,
    revisionNumber = revisionNumber,
    medicationName = medicationName,
    doseAsIndicated = doseAsIndicated,
    unitOrPresentation = unitOrPresentation,
    frequencyAsIndicated = frequencyAsIndicated,
    startsOnEpochDay = startsOn.toEpochDay(),
    endsOnEpochDay = endsOn?.toEpochDay(),
    originalAdditionalInstructions = originalAdditionalInstructions,
    capturedAtEpochMillis = capturedAtEpochMillis,
    authorActorId = authorActorId,
    changeKind = changeKind.name,
    changeReason = changeReason,
    lifecycleEffectiveAtEpochMillis = lifecycleEffectiveAtEpochMillis,
)

private fun MedicationPlanRevisionEntity.toDomain(
    doses: List<ScheduledDoseEntity>,
    configuration: MedicationConfigurationEntity?,
    administrations: List<MedicationAdministrationEntity>,
) = MedicationPlanRevision(
    id = id,
    medicationPlanId = medicationPlanId,
    revisionNumber = revisionNumber,
    medicationName = medicationName,
    doseAsIndicated = doseAsIndicated,
    unitOrPresentation = unitOrPresentation,
    frequencyAsIndicated = frequencyAsIndicated,
    startsOn = LocalDate.ofEpochDay(startsOnEpochDay),
    endsOn = endsOnEpochDay?.let(LocalDate::ofEpochDay),
    originalAdditionalInstructions = originalAdditionalInstructions,
    scheduledDoses = doses.sortedBy { it.localTimeMinutes }.map(ScheduledDoseEntity::toDomain),
    capturedAtEpochMillis = capturedAtEpochMillis,
    authorActorId = authorActorId,
    changeKind = enumValueOf<MedicationPlanChangeKind>(changeKind),
    changeReason = changeReason,
    lifecycleEffectiveAtEpochMillis = lifecycleEffectiveAtEpochMillis,
    configuration = configuration?.toDomain(administrations),
)

private fun ScheduledDose.toEntity() = ScheduledDoseEntity(
    id = id,
    medicationPlanRevisionId = medicationPlanRevisionId,
    localTimeMinutes = time.hour * 60 + time.minute,
    configuredAtEpochMillis = configuredAtEpochMillis,
    configuredByActorId = configuredByActorId,
    origin = origin.name,
)

private fun ScheduledDoseEntity.toDomain(): ScheduledDose {
    require(localTimeMinutes in 0 until 24 * 60)
    return ScheduledDose(
        id = id,
        medicationPlanRevisionId = medicationPlanRevisionId,
        time = localTimeMinutes.toLocalTime(),
        configuredAtEpochMillis = configuredAtEpochMillis,
        configuredByActorId = configuredByActorId,
        origin = enumValueOf<InformationOrigin>(origin),
    )
}

private fun MedicationConfiguration.toEntity(
    revisionId: String,
) = MedicationConfigurationEntity(
    id = revisionId,
    medicationPlanRevisionId = revisionId,
    form = form.name,
    concentrationValue = concentrationValue,
    concentrationUnit = concentrationUnit,
    baseAmountValue = baseAmount.value,
    baseAmountUnit = baseAmount.unit,
    frequency = frequency.name,
    weekdaysMask = weekdays.fold(0) { mask, day ->
        mask or (1 shl (day.value - 1))
    },
    everyNDaysInterval = everyNDaysInterval,
    everyNDaysReferenceEpochDay = everyNDaysReferenceDate?.toEpochDay(),
    hourlyInterval = hourlyInterval,
    hourlyMode = hourlyMode?.name,
    hourlyReferenceEpochDay = hourlyReferenceDate?.toEpochDay(),
    hourlyReferenceMinute = hourlyReferenceTime?.toMinuteOfDay(),
    hourlyWindowStartMinute = hourlyWindowStart?.toMinuteOfDay(),
    hourlyWindowEndMinute = hourlyWindowEnd?.toMinuteOfDay(),
    hourlyWindowEndsNextDay = hourlyWindowEndsNextDay,
    hourlyFirstAdministrationMinute =
        hourlyFirstAdministration?.toMinuteOfDay(),
    cycleActiveDays = cycle?.activeDays,
    cycleRestDays = cycle?.restDays,
    cycleReferenceEpochDay = cycle?.referenceDate?.toEpochDay(),
    cycleCount = cycle?.numberOfCycles,
    asNeededInstruction = asNeededInstruction,
    asNeededRestrictions = asNeededRestrictions,
    startsOnEpochDay = startsOn.toEpochDay(),
    endsOnEpochDay = endsOn?.toEpochDay(),
    status = status.name,
    reminderPreference = reminderPreference.name,
    secondReminderEnabled = secondReminderEnabled,
    reminderEffectiveState = reminderEffectiveState.name,
)

private fun MedicationAdministration.toEntity(
    revisionId: String,
) = MedicationAdministrationEntity(
    id = UUID.randomUUID().toString(),
    logicalId = this.id,
    medicationPlanRevisionId = revisionId,
    timing = timing.name,
    exactTimeMinute = exactTime?.toMinuteOfDay(),
    windowStartMinute = windowStart?.toMinuteOfDay(),
    windowEndMinute = windowEnd?.toMinuteOfDay(),
    windowEndsNextDay = windowEndsNextDay,
    noticeTimeMinute = noticeTime?.toMinuteOfDay(),
    noticeNextDay = noticeNextDay,
    reminderEnabled = reminderEnabled,
    amountOverrideValue = amountOverride?.value,
    amountOverrideUnit = amountOverride?.unit,
    activityRelation = activityRelation?.name,
    customActivity = customActivity,
    activityNoticeTimeMinute = activityNoticeTime?.toMinuteOfDay(),
)

private fun MedicationConfigurationEntity.toDomain(
    administrationEntities: List<MedicationAdministrationEntity>,
): MedicationConfiguration {
    val cycle = if (
        cycleActiveDays != null &&
        cycleRestDays != null &&
        cycleReferenceEpochDay != null
    ) {
        MedicationCycle(
            activeDays = cycleActiveDays,
            restDays = cycleRestDays,
            referenceDate = LocalDate.ofEpochDay(cycleReferenceEpochDay),
            numberOfCycles = cycleCount,
        )
    } else {
        null
    }
    return MedicationConfiguration(
        form = enumValueOf<MedicationProductForm>(form),
        concentrationValue = concentrationValue,
        concentrationUnit = concentrationUnit,
        baseAmount = MedicationAmount(baseAmountValue, baseAmountUnit),
        frequency = enumValueOf<MedicationFrequencyKind>(frequency),
        weekdays = DayOfWeek.entries.filterTo(mutableSetOf()) { day ->
            weekdaysMask and (1 shl (day.value - 1)) != 0
        },
        everyNDaysInterval = everyNDaysInterval,
        everyNDaysReferenceDate =
            everyNDaysReferenceEpochDay?.let(LocalDate::ofEpochDay),
        hourlyInterval = hourlyInterval,
        hourlyMode = hourlyMode?.let {
            enumValueOf<MedicationHourlyIntervalMode>(it)
        },
        hourlyReferenceDate =
            hourlyReferenceEpochDay?.let(LocalDate::ofEpochDay),
        hourlyReferenceTime = hourlyReferenceMinute?.toLocalTime(),
        hourlyWindowStart = hourlyWindowStartMinute?.toLocalTime(),
        hourlyWindowEnd = hourlyWindowEndMinute?.toLocalTime(),
        hourlyWindowEndsNextDay = hourlyWindowEndsNextDay,
        hourlyFirstAdministration =
            hourlyFirstAdministrationMinute?.toLocalTime(),
        cycle = cycle,
        asNeededInstruction = asNeededInstruction,
        asNeededRestrictions = asNeededRestrictions,
        startsOn = LocalDate.ofEpochDay(startsOnEpochDay),
        endsOn = endsOnEpochDay?.let(LocalDate::ofEpochDay),
        status = enumValueOf<MedicationTreatmentStatus>(status),
        reminderPreference =
            enumValueOf<MedicationReminderPreference>(reminderPreference),
        secondReminderEnabled = secondReminderEnabled,
        reminderEffectiveState =
            enumValueOf<MedicationReminderEffectiveState>(
                reminderEffectiveState,
            ),
        administrations = administrationEntities.map {
            it.toDomainAdministration()
        },
    )
}

private fun MedicationAdministrationEntity.toDomainAdministration() =
    MedicationAdministration(
        id = logicalId ?: id,
        timing = enumValueOf<MedicationAdministrationTiming>(timing),
        exactTime = exactTimeMinute?.toLocalTime(),
        windowStart = windowStartMinute?.toLocalTime(),
        windowEnd = windowEndMinute?.toLocalTime(),
        windowEndsNextDay = windowEndsNextDay,
        noticeTime = noticeTimeMinute?.toLocalTime(),
        noticeNextDay = noticeNextDay,
        reminderEnabled = reminderEnabled,
        amountOverride = if (
            amountOverrideValue != null && amountOverrideUnit != null
        ) {
            MedicationAmount(amountOverrideValue, amountOverrideUnit)
        } else {
            null
        },
        activityRelation = activityRelation?.let {
            enumValueOf<MedicationActivityRelation>(it)
        },
        customActivity = customActivity,
        activityNoticeTime = activityNoticeTimeMinute?.toLocalTime(),
    )

private fun MedicationIntakeRecord.toEntity() = MedicationIntakeRecordEntity(
    id = id,
    medicationPlanId = medicationPlanId,
    createdAtEpochMillis = createdAtEpochMillis,
    createdByActorId = createdByActorId,
    currentRevisionNumber = currentRevision.revisionNumber,
)

private fun MedicationIntakeRevision.toEntity() = MedicationIntakeRevisionEntity(
    id = id,
    intakeRecordId = intakeRecordId,
    revisionNumber = revisionNumber,
    outcome = outcome.name,
    scheduledEpochDay = scheduledAt.toLocalDate().toEpochDay(),
    scheduledMinute = scheduledAt.toLocalTime().toMinuteOfDay(),
    actualEpochDay = actualAt?.toLocalDate()?.toEpochDay(),
    actualMinute = actualAt?.toLocalTime()?.toMinuteOfDay(),
    actualAmountValue = actualAmount?.value,
    actualAmountUnit = actualAmount?.unit,
    scheduledOccurrenceId = scheduledOccurrenceId,
    observation = observation,
    changeKind = changeKind.name,
    changeReason = changeReason,
    capturedAtEpochMillis = capturedAtEpochMillis,
    authorActorId = authorActorId,
)

private fun MedicationIntakeRevisionEntity.toDomain(): MedicationIntakeRevision {
    require(scheduledMinute in 0 until 24 * 60)
    require(actualMinute == null || actualMinute in 0 until 24 * 60)
    require((actualEpochDay == null) == (actualMinute == null))
    require((actualAmountValue == null) == (actualAmountUnit == null))
    return MedicationIntakeRevision(
        id = id,
        intakeRecordId = intakeRecordId,
        revisionNumber = revisionNumber,
        outcome = enumValueOf<MedicationIntakeOutcome>(outcome),
        scheduledAt = LocalDateTime.of(
            LocalDate.ofEpochDay(scheduledEpochDay),
            scheduledMinute.toLocalTime(),
        ),
        actualAt = actualEpochDay?.let { day ->
            LocalDateTime.of(
                LocalDate.ofEpochDay(day),
                requireNotNull(actualMinute).toLocalTime(),
            )
        },
        actualAmount = actualAmountValue?.let {
            MedicationAmount(it, requireNotNull(actualAmountUnit))
        },
        observation = observation,
        scheduledOccurrenceId = scheduledOccurrenceId,
        changeKind = enumValueOf<MedicationIntakeChangeKind>(changeKind),
        changeReason = changeReason,
        capturedAtEpochMillis = capturedAtEpochMillis,
        authorActorId = authorActorId,
    )
}

private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

private fun Int.toLocalTime(): LocalTime {
    require(this in 0 until 24 * 60)
    return LocalTime.of(this / 60, this % 60)
}
