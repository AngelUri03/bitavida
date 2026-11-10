package com.bitavida.app.ui.careprofile

import androidx.lifecycle.ViewModel
import com.bitavida.app.data.careprofile.InMemoryCareProfileRepository
import com.bitavida.app.domain.repository.CareProfileRepository
import com.bitavida.app.domain.usecase.CreateCareProfileCommand
import com.bitavida.app.domain.usecase.CreateCareProfileResult
import com.bitavida.app.domain.usecase.CreateCareProfileUseCase
import com.bitavida.app.domain.usecase.GetCareProfileUseCase
import com.bitavida.app.domain.validation.CareProfileField
import com.bitavida.app.domain.validation.CareProfileValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CareProfileViewModel(
    repository: CareProfileRepository = InMemoryCareProfileRepository(),
) : ViewModel() {
    private val createCareProfile = CreateCareProfileUseCase(repository)
    private val getCareProfile = GetCareProfileUseCase(repository)
    private val mutableUiState = MutableStateFlow(CareProfileUiState())

    val uiState: StateFlow<CareProfileUiState> = mutableUiState.asStateFlow()

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

    private fun continueFlow() {
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
                updateState { copy(effect = CareProfileEffect.Completed) }
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
        when (
            val result = createCareProfile(
                CreateCareProfileCommand(
                    caregiverName = state.caregiverName,
                    personUnderCareName = state.personUnderCareName,
                    authorizationDeclared = state.authorizationDeclared,
                ),
            )
        ) {
            is CreateCareProfileResult.Created ->
                updateState {
                    copy(
                        step = CareProfileStep.COMPLETED,
                        createdProfile = getCareProfile() ?: result.profile,
                        fieldErrors = emptyMap(),
                        globalError = null,
                    )
                }
            is CreateCareProfileResult.Invalid ->
                updateState {
                    copy(
                        fieldErrors = result.errors.associate { it.field to it.error },
                        globalError = null,
                    )
                }
            CreateCareProfileResult.ProfileAlreadyExists ->
                updateState {
                    copy(globalError = CareProfileGlobalError.PROFILE_ALREADY_EXISTS)
                }
        }
    }

    private fun goBack() {
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
}
