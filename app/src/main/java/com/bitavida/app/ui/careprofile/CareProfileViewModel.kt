package com.bitavida.app.ui.careprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.model.CURRENT_LEGAL_DOCUMENT_VERSION
import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.model.LegalDocument
import com.bitavida.app.domain.model.LegalDocumentDecision
import com.bitavida.app.domain.repository.CareProfileIntegrityException
import com.bitavida.app.domain.repository.CareProfileKeyUnavailableException
import com.bitavida.app.domain.repository.CareProfileRepository
import com.bitavida.app.domain.repository.CareProfileStorageUnavailableException
import com.bitavida.app.domain.usecase.CreateCareProfileCommand
import com.bitavida.app.domain.usecase.CreateCareProfileResult
import com.bitavida.app.domain.usecase.CreateCareProfileUseCase
import com.bitavida.app.domain.usecase.GetCareProfileUseCase
import com.bitavida.app.domain.validation.CareProfileField
import com.bitavida.app.domain.validation.CareProfileValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CareProfileViewModel(
    repository: CareProfileRepository,
) : ViewModel() {
    private val createCareProfile = CreateCareProfileUseCase(repository)
    private val getCareProfile = GetCareProfileUseCase(repository)
    private val updateNames = com.bitavida.app.domain.usecase.UpdateCareProfileNames(repository)
    private val mutableNamesState = MutableStateFlow(CareProfileNamesState())
    val namesState: StateFlow<CareProfileNamesState> = mutableNamesState.asStateFlow()
    private val mutableUiState = MutableStateFlow(CareProfileUiState())
    private val mutableStartupState =
        MutableStateFlow<CareProfileStartupState>(CareProfileStartupState.Loading)
    private val mutableOnboardingVisible = MutableStateFlow(false)

    val uiState: StateFlow<CareProfileUiState> = mutableUiState.asStateFlow()
    val startupState: StateFlow<CareProfileStartupState> =
        mutableStartupState.asStateFlow()
    val onboardingVisible: StateFlow<Boolean> = mutableOnboardingVisible.asStateFlow()

    init {
        loadStoredProfile()
    }

    fun retryLoading() {
        loadStoredProfile()
    }

    fun finishOnboardingNavigation() {
        mutableOnboardingVisible.value = false
    }

    fun openNamesEditor() {
        val current = (mutableStartupState.value as? CareProfileStartupState.Ready)?.profile ?: return
        mutableNamesState.value = CareProfileNamesState(
            open = true,
            caregiverName = current.caregiver.displayName,
            personName = current.personUnderCare.displayName,
            expectedCaregiverName = current.caregiver.displayName,
            expectedPersonName = current.personUnderCare.displayName,
        )
    }

    fun closeNamesEditor() {
        if (!mutableNamesState.value.saving) mutableNamesState.value = CareProfileNamesState()
    }

    fun changeCaregiverName(value: String) {
        val current = mutableNamesState.value
        if (!current.saving) mutableNamesState.value = current.copy(
            caregiverName = CareProfileValidator.limitNameInput(value), errors = current.errors - CareProfileField.CAREGIVER_NAME, message = null)
    }

    fun changePersonName(value: String) {
        val current = mutableNamesState.value
        if (!current.saving) mutableNamesState.value = current.copy(
            personName = CareProfileValidator.limitNameInput(value), errors = current.errors - CareProfileField.PERSON_UNDER_CARE_NAME, message = null)
    }

    fun saveNames() {
        val draft = mutableNamesState.value
        val profile = (mutableStartupState.value as? CareProfileStartupState.Ready)?.profile ?: return
        if (!draft.open || draft.saving) return
        mutableNamesState.value = draft.copy(saving = true, message = null)
        val command = com.bitavida.app.domain.repository.UpdateCareProfileNamesCommand(
            operationId = java.util.UUID.randomUUID().toString(), profileId = profile.id,
            caregiverId = profile.caregiver.id, personUnderCareId = profile.personUnderCare.id,
            expectedCaregiverName = draft.expectedCaregiverName, expectedPersonName = draft.expectedPersonName,
            caregiverName = draft.caregiverName, personName = draft.personName,
            capturedAtEpochMillis = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            try {
                when (val result = updateNames(command)) {
                    is com.bitavida.app.domain.usecase.UpdateNamesResult.Invalid ->
                        mutableNamesState.value = mutableNamesState.value.copy(saving = false,
                            errors = result.errors.associate { it.field to it.error })
                    is com.bitavida.app.domain.usecase.UpdateNamesResult.Finished -> when (val stored = result.result) {
                        is com.bitavida.app.domain.repository.UpdateCareProfileNamesResult.Stored -> {
                            mutableStartupState.value = stored.profile.toReadyState()
                            mutableNamesState.value = CareProfileNamesState()
                        }
                        com.bitavida.app.domain.repository.UpdateCareProfileNamesResult.Conflict ->
                            mutableNamesState.value = mutableNamesState.value.copy(saving = false,
                                message = "Los datos cambiaron. Cierra y vuelve a abrir la edición.")
                        com.bitavida.app.domain.repository.UpdateCareProfileNamesResult.Unauthorized ->
                            mutableNamesState.value = mutableNamesState.value.copy(saving = false,
                                message = "No se pudo verificar la autorización para modificar estos datos.")
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableNamesState.value = mutableNamesState.value.copy(saving = false,
                    message = "No se pudieron guardar los cambios. Inténtalo de nuevo.")
            }
        }
    }

    fun onAction(action: CareProfileAction) {
        when (action) {
            CareProfileAction.Continue -> continueFlow()
            CareProfileAction.Back -> goBack()
            CareProfileAction.Cancel ->
                updateState { copy(effect = CareProfileEffect.Cancelled) }
            CareProfileAction.Confirm -> confirm()
            CareProfileAction.EditCaregiver ->
                updateState {
                    copy(
                        step = CareProfileStep.CAREGIVER,
                        editingFromReview = true,
                        fieldErrors = emptyMap(),
                        globalError = null,
                    )
                }
            CareProfileAction.EditPersonUnderCare ->
                updateState {
                    copy(
                        step = CareProfileStep.PERSON_UNDER_CARE,
                        editingFromReview = true,
                        fieldErrors = emptyMap(),
                        globalError = null,
                    )
                }
            CareProfileAction.ContinueToToday ->
                updateState { copy(effect = CareProfileEffect.Completed) }
            CareProfileAction.EffectConsumed ->
                updateState { copy(effect = null) }
            CareProfileAction.CaregiverNameFocusLost ->
                validateNameOnBlur(
                    field = CareProfileField.CAREGIVER_NAME,
                    value = uiState.value.caregiverName,
                )
            CareProfileAction.PersonUnderCareNameFocusLost ->
                validateNameOnBlur(
                    field = CareProfileField.PERSON_UNDER_CARE_NAME,
                    value = uiState.value.personUnderCareName,
                )
            is CareProfileAction.CaregiverNameChanged ->
                updateState {
                    copy(
                        caregiverName = action.value,
                        fieldErrors = fieldErrors - CareProfileField.CAREGIVER_NAME,
                        globalError = null,
                    )
                }
            is CareProfileAction.PersonUnderCareNameChanged ->
                updateState {
                    copy(
                        personUnderCareName = action.value,
                        fieldErrors = fieldErrors - CareProfileField.PERSON_UNDER_CARE_NAME,
                        globalError = null,
                    )
                }
            is CareProfileAction.AuthorizationChanged ->
                updateState {
                    copy(
                        authorizationDeclared = action.declared,
                        fieldErrors = fieldErrors - CareProfileField.AUTHORIZATION_DECLARATION,
                        globalError = null,
                    )
                }
            is CareProfileAction.TermsAcceptanceChanged ->
                updateState {
                    copy(
                        termsAccepted = action.accepted,
                        missingLegalDecisions = if (action.accepted) {
                            missingLegalDecisions - CareProfileLegalDecision.TERMS_OF_USE
                        } else {
                            missingLegalDecisions
                        },
                    )
                }
            is CareProfileAction.PrivacyNoticeAcknowledgementChanged ->
                updateState {
                    copy(
                        privacyNoticeAcknowledged = action.acknowledged,
                        missingLegalDecisions = if (action.acknowledged) {
                            missingLegalDecisions - CareProfileLegalDecision.PRIVACY_NOTICE
                        } else {
                            missingLegalDecisions
                        },
                    )
                }
        }
    }

    private fun loadStoredProfile() {
        if (mutableStartupState.value == CareProfileStartupState.Loading &&
            mutableOnboardingVisible.value
        ) {
            return
        }
        mutableStartupState.value = CareProfileStartupState.Loading
        viewModelScope.launch {
            try {
                val profile = getCareProfile()
                if (profile == null) {
                    mutableOnboardingVisible.value = true
                    mutableStartupState.value = CareProfileStartupState.NoProfile
                } else {
                    mutableOnboardingVisible.value = false
                    mutableStartupState.value = profile.toReadyState()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: CareProfileKeyUnavailableException) {
                mutableStartupState.value = CareProfileStartupState.Error(
                    CareProfileStartupError.KEY_UNAVAILABLE,
                )
            } catch (error: CareProfileIntegrityException) {
                mutableStartupState.value = CareProfileStartupState.Error(
                    CareProfileStartupError.INCOMPLETE_STORAGE,
                )
            } catch (error: CareProfileStorageUnavailableException) {
                mutableStartupState.value = CareProfileStartupState.Error(
                    CareProfileStartupError.STORAGE_UNAVAILABLE,
                )
            }
        }
    }

    private fun continueFlow() {
        if (uiState.value.isSaving) return
        when (uiState.value.step) {
            CareProfileStep.WELCOME ->
                updateState { copy(step = CareProfileStep.PRIVACY) }
            CareProfileStep.PRIVACY -> validateLegalDecisions()
            CareProfileStep.AUTHORIZATION -> validateAuthorization()
            CareProfileStep.CAREGIVER -> validateName(
                field = CareProfileField.CAREGIVER_NAME,
                value = uiState.value.caregiverName,
                next = if (uiState.value.editingFromReview) {
                    CareProfileStep.REVIEW
                } else {
                    CareProfileStep.PERSON_UNDER_CARE
                },
            )
            CareProfileStep.PERSON_UNDER_CARE -> validateName(
                field = CareProfileField.PERSON_UNDER_CARE_NAME,
                value = uiState.value.personUnderCareName,
                next = CareProfileStep.REVIEW,
            )
            CareProfileStep.REVIEW -> confirm()
            CareProfileStep.COMPLETED ->
                onAction(CareProfileAction.ContinueToToday)
        }
    }

    private fun validateLegalDecisions() {
        val state = uiState.value
        val missing = buildSet {
            if (!state.termsAccepted) add(CareProfileLegalDecision.TERMS_OF_USE)
            if (!state.privacyNoticeAcknowledged) {
                add(CareProfileLegalDecision.PRIVACY_NOTICE)
            }
        }
        if (missing.isEmpty()) {
            updateState {
                copy(
                    step = CareProfileStep.AUTHORIZATION,
                    missingLegalDecisions = emptySet(),
                )
            }
        } else {
            updateState { copy(missingLegalDecisions = missing) }
        }
    }

    private fun validateAuthorization() {
        if (uiState.value.authorizationDeclared) {
            updateState {
                copy(
                    step = CareProfileStep.CAREGIVER,
                    fieldErrors = emptyMap(),
                )
            }
        } else {
            updateState {
                copy(
                    fieldErrors = mapOf(
                        CareProfileField.AUTHORIZATION_DECLARATION to
                            com.bitavida.app.domain.validation.CareProfileValidationError
                                .AUTHORIZATION_REQUIRED,
                    ),
                )
            }
        }
    }

    private fun validateNameOnBlur(
        field: CareProfileField,
        value: String,
    ) {
        val error = CareProfileValidator.validateName(value, field)
        updateState {
            copy(
                fieldErrors = if (error == null) {
                    fieldErrors - field
                } else {
                    fieldErrors + (field to error.error)
                },
            )
        }
    }

    private fun validateName(
        field: CareProfileField,
        value: String,
        next: CareProfileStep,
    ) {
        val error = CareProfileValidator.validateName(value, field)
        if (error == null) {
            updateState {
                copy(
                    step = next,
                    editingFromReview = false,
                    fieldErrors = fieldErrors - field,
                )
            }
        } else {
            updateState {
                copy(fieldErrors = fieldErrors + (field to error.error))
            }
        }
    }

    private fun confirm() {
        val state = uiState.value
        if (state.isSaving) return
        updateState { copy(isSaving = true, globalError = null) }
        viewModelScope.launch {
            try {
                when (
                    val result = createCareProfile(
                        CreateCareProfileCommand(
                            caregiverName = state.caregiverName,
                            personUnderCareName = state.personUnderCareName,
                            authorizationDeclared = state.authorizationDeclared,
                            termsAccepted = state.termsAccepted,
                            privacyNoticeAcknowledged =
                                state.privacyNoticeAcknowledged,
                        ),
                    )
                ) {
                    is CreateCareProfileResult.Created -> {
                        val persistedProfile = getCareProfile()
                            ?: throw CareProfileIntegrityException()
                        mutableStartupState.value = persistedProfile.toReadyState()
                        updateState {
                            copy(
                                step = CareProfileStep.COMPLETED,
                                createdProfile = persistedProfile,
                                fieldErrors = emptyMap(),
                                globalError = null,
                                isSaving = false,
                            )
                        }
                    }
                    is CreateCareProfileResult.Invalid ->
                        updateState {
                            copy(
                                fieldErrors =
                                    result.errors.associate { it.field to it.error },
                                globalError = null,
                                isSaving = false,
                            )
                        }
                    is CreateCareProfileResult.MissingLegalDecisions ->
                        updateState {
                            copy(
                                step = CareProfileStep.PRIVACY,
                                missingLegalDecisions = result.documents.mapTo(
                                    mutableSetOf(),
                                ) { document ->
                                    when (document) {
                                        LegalDocument.TERMS_OF_USE ->
                                            CareProfileLegalDecision.TERMS_OF_USE
                                        LegalDocument.PRIVACY_NOTICE ->
                                            CareProfileLegalDecision.PRIVACY_NOTICE
                                    }
                                },
                                isSaving = false,
                            )
                        }
                    CreateCareProfileResult.ProfileAlreadyExists ->
                        updateState {
                            copy(
                                globalError =
                                    CareProfileGlobalError.PROFILE_ALREADY_EXISTS,
                                isSaving = false,
                            )
                        }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: CareProfileKeyUnavailableException) {
                updateState {
                    copy(
                        globalError = CareProfileGlobalError.KEY_UNAVAILABLE,
                        isSaving = false,
                    )
                }
            } catch (error: CareProfileIntegrityException) {
                updateState {
                    copy(
                        globalError = CareProfileGlobalError.INCOMPLETE_STORAGE,
                        isSaving = false,
                    )
                }
            } catch (error: CareProfileStorageUnavailableException) {
                updateState {
                    copy(
                        globalError = CareProfileGlobalError.STORAGE_UNAVAILABLE,
                        isSaving = false,
                    )
                }
            }
        }
    }

    private fun goBack() {
        if (uiState.value.isSaving) return
        if (uiState.value.editingFromReview) {
            updateState {
                copy(
                    step = CareProfileStep.REVIEW,
                    editingFromReview = false,
                    fieldErrors = emptyMap(),
                    globalError = null,
                )
            }
            return
        }
        val previous = when (uiState.value.step) {
            CareProfileStep.WELCOME -> return
            CareProfileStep.PRIVACY -> CareProfileStep.WELCOME
            CareProfileStep.AUTHORIZATION -> CareProfileStep.PRIVACY
            CareProfileStep.CAREGIVER -> CareProfileStep.AUTHORIZATION
            CareProfileStep.PERSON_UNDER_CARE -> CareProfileStep.CAREGIVER
            CareProfileStep.REVIEW -> CareProfileStep.PERSON_UNDER_CARE
            CareProfileStep.COMPLETED -> return
        }
        updateState {
            copy(
                step = previous,
                fieldErrors = emptyMap(),
                globalError = null,
            )
        }
    }

    private inline fun updateState(
        transform: CareProfileUiState.() -> CareProfileUiState,
    ) {
        mutableUiState.value = mutableUiState.value.transform()
    }

    private fun CareProfile.toReadyState(): CareProfileStartupState.Ready {
        val restrictions = buildSet {
            if (!consent.isCurrent) {
                add(CareProfileRestriction.CAREGIVER_AUTHORIZATION_REVOKED)
            }
            val currentTerms = legalAcceptances.any {
                it.document == LegalDocument.TERMS_OF_USE &&
                    it.decision == LegalDocumentDecision.ACCEPTED &&
                    it.documentVersion == CURRENT_LEGAL_DOCUMENT_VERSION &&
                    it.isCurrent
            }
            if (!currentTerms) add(CareProfileRestriction.TERMS_ACCEPTANCE_REQUIRED)
            val currentPrivacy = legalAcceptances.any {
                it.document == LegalDocument.PRIVACY_NOTICE &&
                    it.decision == LegalDocumentDecision.ACKNOWLEDGED &&
                    it.documentVersion == CURRENT_LEGAL_DOCUMENT_VERSION &&
                    it.isCurrent
            }
            if (!currentPrivacy) {
                add(CareProfileRestriction.PRIVACY_ACKNOWLEDGEMENT_REQUIRED)
            }
        }
        return CareProfileStartupState.Ready(this, restrictions)
    }

    companion object {
        fun factory(repository: CareProfileRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(CareProfileViewModel::class.java))
                    return CareProfileViewModel(repository) as T
                }
            }
    }
}
