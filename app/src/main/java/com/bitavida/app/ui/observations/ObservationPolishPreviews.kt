package com.bitavida.app.ui.observations

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.ui.measurements.MeasurementPanel
import com.bitavida.app.ui.measurements.SectionTitle
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme

@Preview(name = "Claro", widthDp = 390, heightDp = 650)
@Preview(name = "Oscuro", widthDp = 390, heightDp = 650, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Compacto", widthDp = 320, heightDp = 650)
@Preview(name = "Texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class ObservationPolishPreviewModes

/** Renders the actual sheet body; the modal container itself belongs to the shared Design System. */
@ObservationPolishPreviewModes
@Composable
private fun ObservationDeletionPreview() {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            ) {
                Text("¿Eliminar seguimiento?", Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                ObservationSuspensionContent(
                    reason = "Motivo ficticio de eliminación",
                    error = null,
                    saving = false,
                    motionAllowed = false,
                    onReasonChange = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }
}

@ObservationPolishPreviewModes
@Composable
private fun ObservationSearchActionPreview() {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
                ObservationCatalogSearchHeader(expanded = false, saving = false, motionAllowed = false, onToggle = {})
                ObservationCatalogSearchHeader(expanded = true, saving = false, motionAllowed = false, onToggle = {})
            }
        }
    }
}

@ObservationPolishPreviewModes
@Composable
private fun ObservationPhotoActionsPreview() {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            ) {
                MeasurementPanel {
                    SectionTitle("Fotografía (opcional)", small = true)
                    ObservationPhotoSourceActions(enabled = true, onCamera = {}, onGallery = {})
                }
            }
        }
    }
}
