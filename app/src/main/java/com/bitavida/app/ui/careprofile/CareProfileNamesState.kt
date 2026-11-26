package com.bitavida.app.ui.careprofile

import com.bitavida.app.domain.validation.CareProfileField
import com.bitavida.app.domain.validation.CareProfileValidationError

data class CareProfileNamesState(
    val open: Boolean = false,
    val caregiverName: String = "",
    val personName: String = "",
    val expectedCaregiverName: String = "",
    val expectedPersonName: String = "",
    val errors: Map<CareProfileField, CareProfileValidationError> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
)
