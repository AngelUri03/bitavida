package com.bitavida.app.ui.careprofile

import androidx.compose.runtime.Immutable
import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.validation.CareProfileField
import com.bitavida.app.domain.validation.CareProfileValidationError

enum class CareProfileStep {
    WELCOME,
    PRIVACY,
    AUTHORIZATION,
    CAREGIVER,
    PERSON_UNDER_CARE,
    REVIEW,
    COMPLETED,
}

@Immutable
data class CareProfileUiState(
    val step: CareProfileStep = CareProfileStep.WELCOME,
    val caregiverName: String = "",
    val personUnderCareName: String = "",
    val authorizationDeclared: Boolean = false,
    val termsAccepted: Boolean = false,
    val privacyNoticeAcknowledged: Boolean = false,
    val missingLegalDecisions: Set<CareProfileLegalDecision> = emptySet(),
    val fieldErrors: Map<CareProfileField, CareProfileValidationError> = emptyMap(),
    val globalError: CareProfileGlobalError? = null,
    val createdProfile: CareProfile? = null,
    val editingFromReview: Boolean = false,
    val effect: CareProfileEffect? = null,
)

enum class CareProfileLegalDecision {
    TERMS_OF_USE,
    PRIVACY_NOTICE,
}

enum class CareProfileGlobalError {
    PROFILE_ALREADY_EXISTS,
}

sealed interface CareProfileAction {
    data object Continue : CareProfileAction
    data object Back : CareProfileAction
    data object Cancel : CareProfileAction
    data object Confirm : CareProfileAction
    data object EditCaregiver : CareProfileAction
    data object EditPersonUnderCare : CareProfileAction
    data object ContinueToToday : CareProfileAction
    data object EffectConsumed : CareProfileAction
    data object CaregiverNameFocusLost : CareProfileAction
    data object PersonUnderCareNameFocusLost : CareProfileAction
    data class CaregiverNameChanged(val value: String) : CareProfileAction
    data class PersonUnderCareNameChanged(val value: String) : CareProfileAction
    data class AuthorizationChanged(val declared: Boolean) : CareProfileAction
    data class TermsAcceptanceChanged(val accepted: Boolean) : CareProfileAction
    data class PrivacyNoticeAcknowledgementChanged(val acknowledged: Boolean) : CareProfileAction
}

sealed interface CareProfileEffect {
    data object Cancelled : CareProfileEffect
    data object Completed : CareProfileEffect
}
