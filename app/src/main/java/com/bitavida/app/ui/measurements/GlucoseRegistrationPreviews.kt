package com.bitavida.app.ui.measurements

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.bitavida.app.domain.model.ConfirmedGlucoseOccurrence
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseRegistrationContext
import com.bitavida.app.domain.usecase.GlucoseDraft
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime

class GlucoseCapturePreviewProvider : PreviewParameterProvider<GlucoseDraft> {
    override val values: Sequence<GlucoseDraft> get() {
        val time = LocalDateTime.of(2026, 11, 16, 9, 15)
        val first = GlucoseDraft("fictitious-reading", GlucoseRegistrationContext.Independent, time, firstMeasurement = true)
        return sequenceOf(
            first,
            first.copy(firstMeasurement = false),
            first.copy(reading = "128", moment = GlucoseContext.AFTER_MEAL,
                note = "Nota ficticia de ejemplo"),
            first.copy(context = GlucoseRegistrationContext.Scheduled(
                ConfirmedGlucoseOccurrence("fictitious-occurrence", "fictitious-plan", time.minusHours(2), GlucoseContext.FASTING)),
                firstMeasurement = false),
            first.copy(context = GlucoseRegistrationContext.Additional("fictitious-plan"), firstMeasurement = false),
        )
    }
}

@Preview(name = "Captura · claro", widthDp = 390, heightDp = 900)
@Preview(name = "Captura · oscuro", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Captura · compacto", widthDp = 320, heightDp = 800)
@Preview(name = "Captura · movimiento reducido", widthDp = 390, heightDp = 900)
@Preview(name = "Captura · texto 200 %", widthDp = 320, heightDp = 900, fontScale = 2f)
@Composable
private fun GlucoseCapturePreview(@PreviewParameter(GlucoseCapturePreviewProvider::class) draft: GlucoseDraft) {
    BitavidaTheme {
        BitavidaFluidBackground(motionAllowed = false) {
            Box(Modifier.fillMaxSize()) {
                GlucoseRegistrationSheet(
                    state = GlucoseUiState(loading = false, draft = draft),
                    motionAllowed = false,
                    onAction = {},
                )
            }
        }
    }
}
