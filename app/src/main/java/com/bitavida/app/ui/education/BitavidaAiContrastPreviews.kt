package com.bitavida.app.ui.education

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.education.*
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaTheme

enum class AiReadingContrastState { ANSWER_EXPANDED, DETAIL_EXPANDED, DETAIL_COLLAPSED }

class AiReadingContrastProvider : PreviewParameterProvider<AiReadingContrastState> {
    override val values = AiReadingContrastState.entries.asSequence()
}

private fun contrastEntry() = EducationEntry(
    topic = EducationTopic.GLUCOSE_CONTEXT,
    title = "Tema educativo de ejemplo",
    explanation = "Texto ficticio para revisar la explicación principal y su jerarquía visual.",
    example = "Ejemplo ficticio dentro de una tarjeta tintada con los colores oficiales.",
    related = listOf(EducationTopic.GLUCOSE_PURPOSE, EducationTopic.MEASUREMENT_HISTORY),
    source = EducationSource("Fuente ficticia de preview", "Referencia bibliográfica ficticia"),
    importance = "Texto de importancia para comprobar la misma legibilidad sobre el fondo.",
    details = listOf("Información complementaria ficticia visible al expandir el tema."),
    concepts = listOf("Primer concepto ficticio.", "Segundo concepto ficticio."),
)

@Preview(name = "Lectura · claro", widthDp = 390, heightDp = 1600)
@Preview(name = "Lectura · oscuro", widthDp = 390, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AiReadingContrastPreview(@PreviewParameter(AiReadingContrastProvider::class) state: AiReadingContrastState) {
    val expanded = state != AiReadingContrastState.DETAIL_COLLAPSED
    val detail = state != AiReadingContrastState.ANSWER_EXPANDED
    ContrastFrame {
        AiAnswerCard(
            EducationResult.Answer(contrastEntry(), fromLibrary = detail),
            {}, motion = false, developed = detail,
            sourceExpanded = expanded, moreExpanded = expanded,
        )
    }
}

@Preview(name = "Estados secundarios · claro", widthDp = 390, heightDp = 1300)
@Preview(name = "Estados secundarios · oscuro", widthDp = 390, heightDp = 1300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AiSecondaryContrastPreview() {
    ContrastFrame {
        AiFallbackCard(
            EducationResult.Fallback(EducationFallback.UNCERTAIN,
                listOf(EducationTopic.GLUCOSE_PURPOSE, EducationTopic.PRESSURE_PURPOSE)),
            {}, motion = false,
        )
        Spacer(Modifier.height(16.dp))
        AiPanel {
            AiSectionHeading("Probemos con otra palabra", small = true)
            androidx.compose.material3.Text(
                "Ejemplo ficticio del estado vacío de la biblioteca.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(name = "Consulta activa · claro", widthDp = 390, heightDp = 1000)
@Preview(name = "Consulta activa · oscuro", widthDp = 390, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AiBusyContrastPreview() {
    ContrastFrame {
        BitavidaAiContent(
            BitavidaAiUiState(question = "Pregunta ficticia", analyzing = true),
            {}, motionOverride = false,
        )
    }
}

/** Deliberately has no Surface or local foreground: entry points must supply their own semantics. */
@Composable
private fun ContrastFrame(content: @Composable () -> Unit) {
    BitavidaTheme {
        BitavidaFluidBackground {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            ) {
                CurrentMedicationBack({}, motionAllowed = false, enabled = true, label = "Bitavida IA")
                content()
            }
        }
    }
}
