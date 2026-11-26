package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.usecase.MeasurementSummary

internal enum class GlucoseDetailMode {
    EMPTY_WITHOUT_FOLLOW_UP,
    EMPTY_WITH_FOLLOW_UP,
    RECORDED_WITHOUT_FOLLOW_UP,
    RECORDED_WITH_FOLLOW_UP,
}

internal data class GlucoseDetailPresentation(
    val mode: GlucoseDetailMode,
    val registerLabel: String,
    val showIndication: Boolean,
    val showTodayRegister: Boolean,
    val managementActions: List<MeasurementFollowUpAction>,
    val showHelp: Boolean,
    val showSuspend: Boolean,
)

internal fun glucoseDetailPresentation(
    summary: MeasurementSummary,
    hasFollowUpHistory: Boolean = false,
): GlucoseDetailPresentation {
    val hasRecords = summary.latest != null
    val hasFollowUp = summary.followUp != null
    val inactiveActions = buildList {
        add(MeasurementFollowUpAction.CONFIGURE)
        if (hasFollowUpHistory) add(MeasurementFollowUpAction.VIEW_CHANGES)
    }
    return GlucoseDetailPresentation(
        mode = when {
            hasRecords && hasFollowUp -> GlucoseDetailMode.RECORDED_WITH_FOLLOW_UP
            hasRecords -> GlucoseDetailMode.RECORDED_WITHOUT_FOLLOW_UP
            hasFollowUp -> GlucoseDetailMode.EMPTY_WITH_FOLLOW_UP
            else -> GlucoseDetailMode.EMPTY_WITHOUT_FOLLOW_UP
        },
        registerLabel = if (hasRecords) "Registrar medición" else "Registrar primera medición",
        showIndication = hasFollowUp,
        showTodayRegister = summary.followUp?.suspended == false,
        managementActions = if (hasFollowUp) {
            listOf(
                MeasurementFollowUpAction.CORRECT,
                MeasurementFollowUpAction.CHANGE_INDICATIONS,
                MeasurementFollowUpAction.VIEW_CHANGES,
            )
        } else {
            inactiveActions
        },
        showHelp = false,
        showSuspend = summary.followUp?.suspended == false,
    )
}
