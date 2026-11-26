package com.bitavida.app.domain.model

enum class MedicationReminderRuntimeState {
    DISABLED,
    PERMISSION_REQUIRED,
    NO_SCHEDULED_NOTICE,
    ACTIVE,
    SYSTEM_LIMITED,
}
