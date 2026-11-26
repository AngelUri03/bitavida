package com.bitavida.app.ui.measurements

import androidx.compose.runtime.Composable
import com.bitavida.app.ui.components.BitavidaDatePickerConstraints
import com.bitavida.app.ui.components.BitavidaTimePickerConstraints
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Uses the shared selector content inside the same modal frame as capture. */
@Composable
internal fun GlucoseRegistrationSheet(
    state: GlucoseUiState,
    motionAllowed: Boolean,
    onAction: (GlucoseAction) -> Unit,
) {
    val draft = state.draft ?: return

    MedicationLifecycleSheetFrame(
        medication = null,
        title = if (state.editingRecordId == null) "Registrar medición" else "Corregir registro",
        saving = state.saving,
        motionAllowed = motionAllowed,
        onDismiss = { onAction(GlucoseAction.RequestClose) },
    ) {
        val dateTimePicker = LocalBitavidaDateTimePickerController.current
        SecondaryText("Glucosa capilar")
        GlucoseRegistrationContent(
            state = state,
            onAction = onAction,
            onSelectDate = {
                val today = LocalDate.now(ZoneId.systemDefault())
                dateTimePicker.showDate(
                    selectedDate = draft.actualAt.toLocalDate(),
                    constraints = BitavidaDatePickerConstraints(
                        maxDate = today,
                        yearRange = 1900..today.year,
                    ),
                    onConfirm = { date ->
                        onAction(
                            GlucoseAction.ActualTime(
                                date.atTime(draft.actualAt.toLocalTime()),
                            ),
                        )
                    },
                )
            },
            onSelectTime = {
                val now = LocalDateTime.now(ZoneId.systemDefault())
                dateTimePicker.showTime(
                    selectedTime = draft.actualAt.toLocalTime(),
                    constraints = BitavidaTimePickerConstraints(
                        maxTime = now.toLocalTime()
                            .takeIf { draft.actualAt.toLocalDate() == now.toLocalDate() },
                        invalidSelectionMessage = "No puedes registrar una hora futura.",
                    ),
                    onConfirm = { time ->
                        onAction(
                            GlucoseAction.ActualTime(
                                draft.actualAt.toLocalDate().atTime(time),
                            ),
                        )
                    },
                )
            },
            motionEnabledOverride = motionAllowed,
        )
    }
}
