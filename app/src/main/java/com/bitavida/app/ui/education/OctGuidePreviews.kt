package com.bitavida.app.ui.education

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.theme.BitavidaTheme

@Preview(name = "OCT claro", widthDp = 390, heightDp = 1600, showBackground = true)
@Preview(name = "OCT oscuro", widthDp = 390, heightDp = 1600,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "OCT compacto", widthDp = 320, heightDp = 1600, showBackground = true)
@Preview(name = "OCT texto 200%", widthDp = 320, heightDp = 1600, fontScale = 2f, showBackground = true)
@Composable
private fun OctGuidePreview() {
    val guide = BundledEducationRepository(loadModelText = { "" }).procedures().single { it.id == "oct" }
    BitavidaTheme(darkTheme = isSystemInDarkTheme()) {
        BitavidaFluidBackground(Modifier.fillMaxSize(), motionAllowed = false) {
            BitavidaAiContentColors {
                LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    item {
                        ProcedureGuideContent(
                            guide = guide,
                            motion = false,
                            expanded = setOf("method"),
                            onToggle = {},
                            sourceExpanded = false,
                            onToggleSource = {},
                            onRelated = {},
                            onAsk = {},
                        )
                    }
                }
            }
        }
    }
}
