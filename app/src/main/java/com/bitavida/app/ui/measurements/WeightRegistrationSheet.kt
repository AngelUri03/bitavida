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
internal fun WeightRegistrationSheet(
    state: WeightUiState,
    motionAllowed: Boolean,
    onAction: (WeightAction) -> Unit,
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
            onAction(WeightAction.RequestClose)
        },
    ) {
        val picker = LocalBitavidaDateTimePickerController.current
        SecondaryText("Peso")
        WeightRegistrationContent(
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
                            WeightAction.ActualTime(
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
                            WeightAction.ActualTime(
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
