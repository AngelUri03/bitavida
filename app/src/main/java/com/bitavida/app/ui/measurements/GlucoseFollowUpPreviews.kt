package com.bitavida.app.ui.measurements

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDate

@Preview(name = "Seguimiento · claro", widthDp = 390, heightDp = 900, showBackground = true)
@Preview(
    name = "Seguimiento · oscuro",
    widthDp = 390,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(name = "Seguimiento · 320 dp", widthDp = 320, heightDp = 900, showBackground = true)
@Preview(
    name = "Seguimiento · texto 200 %",
    widthDp = 320,
    heightDp = 1100,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun GlucoseFollowUpPreview() {
    BitavidaTheme {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(BitavidaSpacing.ScreenHorizontal),
        ) {
            GlucoseFollowUpContent(
                existing = null,
                person = "Persona de ejemplo",
                today = LocalDate.of(2026, 11, 17),
                saving = false,
                error = null,
                changeKind = GlucoseFollowUpChangeKind.CREATED,
                onBack = {},
                onSave = {},
            )
        }
    }
}
