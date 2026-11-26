package com.bitavida.app.ui.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.bitavida.app.R
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing
import androidx.compose.ui.unit.dp

/** A single modal menu. Each choice replaces it with the existing module flow. */
@Composable
internal fun TodayMedicationOptionsSheet(
    item: TodayMedicationItem,
    motionAllowed: Boolean,
    enabled: Boolean,
    onOtherTime: () -> Unit,
    onWithNote: () -> Unit,
    onNotTaken: () -> Unit,
    onConsult: () -> Unit,
    onDismiss: () -> Unit,
) {
    TodayTypography {
        MedicationLifecycleSheetFrame(
            medication = null,
            title = "Opciones de la toma",
            saving = !enabled,
            motionAllowed = motionAllowed,
            onDismiss = onDismiss,
            contentHorizontalPadding = BitavidaSpacing.Large,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                Text(item.occurrence.medicationName, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(item.occurrence.amount.value + " " + item.occurrence.amount.unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            TodayMedicationOption("Registrar toma a otra hora", R.drawable.ic_v2_clock,
                "today_medication_other_time", enabled, motionAllowed, onOtherTime)
            TodayMedicationOption("Registrar toma con nota", R.drawable.ic_v2_file,
                "today_medication_with_note", enabled, motionAllowed, onWithNote)
            TodayMedicationOption("Declarar No se tomó", R.drawable.ic_v2_close,
                "today_medication_not_taken", enabled, motionAllowed, onNotTaken)
            TodayMedicationOption("Consultar medicamento", R.drawable.ic_v2_pill,
                "today_medication_consult", enabled, motionAllowed, onConsult)
        }
    }
}

@Composable
private fun TodayMedicationOption(
    label: String, icon: Int, tag: String, enabled: Boolean, motion: Boolean, onClick: () -> Unit,
) {
    CurrentDetailButton(
        kind = DetailButtonKind.OUTLINED,
        onClick = onClick,
        enabled = enabled,
        motionAllowedOverride = motion,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            Image(painterResource(icon), contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        }
    }
}
