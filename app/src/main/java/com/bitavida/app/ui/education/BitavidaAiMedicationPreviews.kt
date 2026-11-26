package com.bitavida.app.ui.education

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
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

enum class MedicationAiPreviewState { HOME, LIBRARY, ANSWER, DETAIL, AMBIGUITY }
class MedicationAiPreviewProvider : PreviewParameterProvider<MedicationAiPreviewState> {
    override val values = MedicationAiPreviewState.entries.asSequence()
}

@Preview(name = "Farmacología · claro", widthDp = 390, heightDp = 900)
@Preview(name = "Farmacología · oscuro", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Farmacología · compacto", widthDp = 320, heightDp = 760)
@Preview(name = "Farmacología · texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
private fun MedicationAiPreview(@PreviewParameter(MedicationAiPreviewProvider::class) preview: MedicationAiPreviewState) {
    val drug = DrugEducation("ficticio_oral", "Principio ficticio oral", "Familia de ejemplo",
        listOf("nombre genérico ficticio"), "Resumen ficticio para comprobar jerarquía y contraste.",
        DrugIntent.entries.map {
            DrugEducationSection(it, "Apartado de ejemplo", "Información ficticia de preview. No describe medicamentos ni personas reales.")
        },
        listOf(DrugReference("Institución de ejemplo", "Documento ficticio",
            "https://example.org/fixture", "Versión ficticia")))
    val alternative = drug.copy(id = "ficticio_inyectable", name = "Principio ficticio inyectable")
    val page = when (preview) {
        MedicationAiPreviewState.HOME -> AiPage.HOME
        MedicationAiPreviewState.LIBRARY -> AiPage.LIBRARY
        MedicationAiPreviewState.DETAIL -> AiPage.DETAIL
        else -> AiPage.ANSWER
    }
    val result = if (preview == MedicationAiPreviewState.AMBIGUITY)
        EducationResult.DrugOptions(listOf(drug, alternative), "Confirma la formulación que deseas conocer.")
        else EducationResult.DrugAnswer(drug, DrugIntent.SIDE_EFFECTS, page == AiPage.DETAIL)
    val entry = AiPageEntry(1, page, result, "Pregunta ficticia sobre un principio activo")
    val state = BitavidaAiUiState(group = EducationGroup.MEDICATIONS,
        navigationContext = EducationGroup.MEDICATIONS, pages = listOf(entry),
        sourceVisibility = mapOf(1L to (preview == MedicationAiPreviewState.DETAIL)),
        drugSections = mapOf(1L to if (preview == MedicationAiPreviewState.DETAIL)
            DrugIntent.entries.toSet() else setOf(DrugIntent.SIDE_EFFECTS)))
    BitavidaTheme {
        BitavidaFluidBackground {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                CurrentMedicationBack({}, motionAllowed = false, enabled = true, label = "Medicamentos")
                AiPageList(entry, state, emptyList(), {}, rememberLazyListState(), false, listOf(drug, alternative))
            }
        }
    }
}
