package com.bitavida.app.ui.measurements

import androidx.compose.runtime.Composable
import com.bitavida.app.ui.components.BitavidaDatePickerConstraints
import com.bitavida.app.ui.components.BitavidaTimePickerConstraints
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
internal fun BloodPressureRegistrationSheet(
    state: BloodPressureUiState,
    motionAllowed: Boolean,
    onAction: (BloodPressureAction) -> Unit,
) {
    val draft = state.draft ?: return
    MedicationLifecycleSheetFrame(
        medication = null,
        title = if (state.editingRecordId == null) {
            "Registrar medición"
        } else {
            "Corregir registro"
        },
        saving = state.saving,
        motionAllowed = motionAllowed,
        onDismiss = {
            onAction(BloodPressureAction.RequestClose)
        },
    ) {
        val picker = LocalBitavidaDateTimePickerController.current
        SecondaryText("Presión arterial")
        BloodPressureRegistrationContent(
            state = state,
            onAction = onAction,
            onSelectDate = {
                val today = LocalDate.now(ZoneId.systemDefault())
                picker.showDate(
                    selectedDate = draft.actualAt.toLocalDate(),
                    constraints = BitavidaDatePickerConstraints(
                        maxDate = today,
                        yearRange = 1900..today.year,
                    ),
                    onConfirm = { date ->
                        onAction(
                            BloodPressureAction.ActualTime(
                                date.atTime(
                                    draft.actualAt.toLocalTime(),
                                ),
                            ),
                        )
                    },
                )
            },
            onSelectTime = {
                val now = LocalDateTime.now(
                    ZoneId.systemDefault(),
                )
                picker.showTime(
                    selectedTime = draft.actualAt.toLocalTime(),
                    constraints = BitavidaTimePickerConstraints(
                        maxTime = now.toLocalTime().takeIf {
                            draft.actualAt.toLocalDate() ==
                                now.toLocalDate()
                        },
                        invalidSelectionMessage =
                            "No puedes registrar una hora futura.",
                    ),
                    onConfirm = { time ->
                        onAction(
                            BloodPressureAction.ActualTime(
                                draft.actualAt.toLocalDate()
                                    .atTime(time),
                            ),
                        )
                    },
                )
            },
            motionEnabledOverride = motionAllowed,
        )
    }
}
