package com.bitavida.app.ui.education

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.education.EducationEntry
import com.bitavida.app.domain.education.EducationGroup
import com.bitavida.app.domain.education.EducationSource
import com.bitavida.app.domain.education.EducationTopic
import com.bitavida.app.domain.model.ObservationCatalog
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaTheme

@Preview(name = "Observaciones IA · claro", widthDp = 390, heightDp = 900)
@Preview(name = "Observaciones IA · oscuro", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Observaciones IA · compacto", widthDp = 320, heightDp = 760)
@Preview(name = "Observaciones IA · texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
private fun ObservationAiHomePreview() {
    ObservationAiPreviewFrame {
        BitavidaAiContent(
            BitavidaAiUiState(
                group = EducationGroup.OBSERVATIONS,
                navigationContext = EducationGroup.OBSERVATIONS,
            ),
            {},
            motionOverride = false,
        )
    }
}

@Preview(name = "Biblioteca observaciones · claro", widthDp = 390, heightDp = 900)
@Preview(name = "Biblioteca observaciones · oscuro", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Biblioteca observaciones · compacto", widthDp = 320, heightDp = 760)
@Preview(name = "Biblioteca observaciones · texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
private fun ObservationAiLibraryPreview() {
    val topics = EducationTopic.entries.filter { it.observationIdForPreview() != null }
    val catalog = topics.mapIndexed { index, topic ->
        val definition = ObservationCatalog.all[index]
        EducationEntry(
            topic = topic,
            title = definition.name,
            explanation = "Explicación educativa ficticia para comprobar la composición de la biblioteca.",
            example = null,
            related = emptyList(),
            source = EducationSource("Institución ficticia", "Referencia ficticia"),
            observationId = definition.id,
            searchTerms = definition.synonyms,
        )
    }
    val page = AiPageEntry(1, AiPage.LIBRARY)
    val state = BitavidaAiUiState(
        group = EducationGroup.OBSERVATIONS,
        navigationContext = EducationGroup.OBSERVATIONS,
        pages = listOf(page),
    )
    ObservationAiPreviewFrame {
        AiPageList(page, state, catalog, {}, rememberLazyListState(), motion = false)
    }
}

private fun EducationTopic.observationIdForPreview(): String? =
    name.takeIf { it.startsWith("OBS_") && it.getOrNull(4)?.isDigit() == true }

@Composable
private fun ObservationAiPreviewFrame(content: @Composable () -> Unit) {
    BitavidaTheme {
        BitavidaFluidBackground {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                CurrentMedicationBack({}, motionAllowed = false, enabled = true, label = "Observaciones")
                content()
            }
        }
    }
}
