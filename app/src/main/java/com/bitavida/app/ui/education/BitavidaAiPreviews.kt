package com.bitavida.app.ui.education

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.bitavida.app.domain.education.*
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaTheme

class AiPagePreviewProvider : PreviewParameterProvider<AiPage> {
    override val values = AiPage.entries.asSequence()
}

@Preview(name = "IA · claro", widthDp = 390, heightDp = 900, showBackground = true)
@Preview(name = "IA · oscuro", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "IA · compacto", widthDp = 320, heightDp = 760)
@Preview(name = "IA · texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
private fun AiDestinationPreview(@PreviewParameter(AiPagePreviewProvider::class) page: AiPage) {
    val catalog = EducationTopic.entries.map { topic ->
        EducationEntry(
            topic, topic.question,
            "Explicación ficticia de preview para revisar la jerarquía y la lectura. No contiene datos personales.",
            "Ejemplo cotidiano ficticio que ilustra el concepto educativo.",
            listOf(EducationTopic.MEASUREMENT_HISTORY).filter { it != topic },
            EducationSource("Fuente de ejemplo", "Referencia ficticia de preview"),
            importance = "Un párrafo ficticio explica por qué este tema ayuda a organizar la información.",
            details = listOf("Información complementaria ficticia para comprobar texto ampliado y desplazamiento."),
            concepts = listOf("Cada registro conserva su contexto.", "Una ausencia no confirma un acontecimiento."),
        )
    }
    val entry = AiPageEntry(1, page, EducationResult.Answer(catalog.first(), page == AiPage.DETAIL),
        "¿Para qué sirve medir la glucosa?")
    val state = BitavidaAiUiState(pages = listOf(entry), group = EducationGroup.GLUCOSE)
    AiPreviewFrame {
        AiPageList(entry, state, catalog, {}, rememberLazyListState(), motion = false)
    }
}

@Preview(name = "IA · ambigüedad", widthDp = 390, heightDp = 900)
@Preview(name = "IA · ambigüedad 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
private fun AiAmbiguityPreview() {
    val entry = AiPageEntry(1, AiPage.ANSWER,
        EducationResult.Fallback(EducationFallback.UNCERTAIN,
            listOf(EducationTopic.GLUCOSE_PURPOSE, EducationTopic.PRESSURE_PURPOSE)),
        "Pregunta ficticia ambigua")
    AiPreviewFrame {
        AiPageList(entry, BitavidaAiUiState(), emptyList(), {}, rememberLazyListState(), false)
    }
}

@Preview(name = "IA · filtros horizontales", widthDp = 320, heightDp = 180)
@Preview(name = "IA · filtros oscuros", widthDp = 320, heightDp = 180, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "IA · filtros 200 %", widthDp = 320, heightDp = 240, fontScale = 2f)
@Composable
private fun AiHorizontalFiltersPreview() {
    AiPreviewFrame {
        AiGroupFilters(EducationGroup.MEASUREMENTS, {}, motion = false)
    }
}

@Preview(name = "IA · análisis breve estático", widthDp = 320, heightDp = 900)
@Composable
private fun AiActiveAnalysisPreview() {
    AiPreviewFrame {
        BitavidaAiContent(
            BitavidaAiUiState(question = "Pregunta ficticia", analyzing = true),
            {}, motionOverride = false,
        )
    }
}

@Preview(name = "IA central · claro", widthDp = 390, heightDp = 900, showBackground = true)
@Preview(name = "IA central · oscuro", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "IA central · compacto", widthDp = 320, heightDp = 760)
@Preview(name = "IA central · texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
private fun CentralAiPreview() {
    BitavidaTheme {
        BitavidaFluidBackground {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                AiCentralHeader(onBack = {})
                AiPageList(AiPageEntry(), BitavidaAiUiState(), emptyList(), {},
                    rememberLazyListState(), motion = false, central = true)
            }
        }
    }
}

@Composable
private fun AiPreviewFrame(content: @Composable () -> Unit) {
    BitavidaTheme {
        BitavidaFluidBackground {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                CurrentMedicationBack({}, motionAllowed = false, enabled = true, label = "Bitavida IA")
                content()
            }
        }
    }
}
