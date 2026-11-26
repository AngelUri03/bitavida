package com.bitavida.app.ui.education

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.education.*
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.measurements.GlucoseCaptureChip
import com.bitavida.app.ui.theme.BitavidaSpacing

private const val GENERAL_GUIDES = "Guías generales"

/** Search only public packaged descriptors; never inspects a treatment or patient record. */
fun filterDrugLibrary(entries: List<DrugEducation>, family: String?, query: String): List<DrugEducation> {
    val terms = EducationTokenizer.normalize(query.take(100)).split(" ").filter(String::isNotBlank)
    return entries.filter { entry ->
        (family == null || entry.family == family) && terms.all { term ->
            term in EducationTokenizer.normalize(
                listOf(entry.name, entry.family, entry.summary).plus(entry.aliases).joinToString(" "))
        }
    }
}

@Composable
internal fun RegisteredMedicationGuideAction(motion: Boolean, onClick: () -> Unit) {
    AiNavigationRow("Conocer este medicamento", "Ficha educativa · Sin cambiar tu tratamiento",
        R.drawable.ic_bitavida_ai, motion, onClick)
}

@Composable
internal fun AiMedicationSuggestions(onAction: (BitavidaAiAction) -> Unit, motion: Boolean) {
    val questions = listOf("¿Para qué sirve la metformina?", "¿Qué precauciones tiene la semaglutida?")
    val largeText = LocalDensity.current.fontScale >= 1.3f
    BoxWithConstraints {
        val columns = if (maxWidth >= 340.dp && !largeText) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            questions.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    row.forEach { question ->
                        AiQuestionCard(question, R.drawable.ic_v2_pill, motion, Modifier.weight(1f)) {
                            onAction(BitavidaAiAction.Ask(question))
                        }
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.medicationLibraryItems(
    state: BitavidaAiUiState,
    drugs: List<DrugEducation>,
    guides: List<EducationEntry>,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
) {
    val filtered = filterDrugLibrary(drugs, state.libraryFamily, state.librarySearch)
    val filteredGuides = if (state.libraryFamily == null || state.libraryFamily == GENERAL_GUIDES)
        filterEducationLibrary(guides, EducationGroup.MEDICATIONS, state.librarySearch) else emptyList()
    item("pharmacy_header") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AiEyebrow("CONOCIMIENTO EN TU DISPOSITIVO")
            AiSectionHeading("Biblioteca de medicamentos")
            Text("Principios activos, familias y preguntas para tu consulta.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.librarySearch,
                onValueChange = { onAction(BitavidaAiAction.SearchLibrary(it)) },
                label = { Text("Buscar medicamento o contenido", style = MaterialTheme.typography.bodyMedium) },
                placeholder = { Text("Metformina, insulina…", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth().testTag("ai_drug_search"),
                textStyle = MaterialTheme.typography.bodyLarge, shape = MaterialTheme.shapes.medium,
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            AiDrugFamilyFilters(drugs.map { it.family }.distinct(), state.libraryFamily, onAction, motion)
            Text("${filtered.size} fichas · ${filteredGuides.size} guías",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (filtered.isEmpty() && filteredGuides.isEmpty()) {
        item("pharmacy_empty") {
            AiPanel {
                AiSectionHeading("Probemos con otro nombre", small = true)
                Text("Busca el principio activo escrito en el envase. Las marcas comerciales no identifican por sí solas una ficha.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    filtered.groupBy { it.family }.forEach { (family, entries) ->
        item("family_$family") { AiSectionHeading(family, small = true) }
        items(entries, key = { "drug_${it.id}" }) { drug ->
            AiNavigationRow(drug.name, drug.summary, R.drawable.ic_v2_pill, motion) {
                onAction(BitavidaAiAction.ReadDrug(drug.id))
            }
        }
    }
    if (filteredGuides.isNotEmpty()) {
        item("general_guides") { AiSectionHeading(GENERAL_GUIDES, small = true) }
        items(filteredGuides, key = { "guide_${it.topic.name}" }) { guide ->
            AiNavigationRow(guide.title, guide.topic.question, R.drawable.ic_bitavida_ai, motion) {
                onAction(BitavidaAiAction.ReadTopic(guide.topic))
            }
        }
    }
    item("pharmacy_review") { AiReviewNote() }
}

@Composable
private fun AiDrugFamilyFilters(
    families: List<String>, selected: String?, onAction: (BitavidaAiAction) -> Unit, motion: Boolean,
) {
    val choices = remember(families) { listOf<String?>(null, GENERAL_GUIDES) + families }
    val index = choices.indexOf(selected).coerceAtLeast(0)
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        LaunchedEffect(index, motion, width, fontScale) {
            if (motion) scroll.animateScrollToItem(index) else scroll.scrollToItem(index)
        }
        LazyRow(state = scroll, modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            items(choices, key = { it ?: "ALL" }) { family ->
                GlucoseCaptureChip(
                    label = family ?: "Todo", selected = family == selected, enabled = true,
                    motionAllowed = motion, onClick = { onAction(BitavidaAiAction.SelectDrugFamily(family)) },
                )
            }
        }
    }
}

@Composable
internal fun AiDrugOptions(
    result: EducationResult.DrugOptions, onAction: (BitavidaAiAction) -> Unit, motion: Boolean,
) = BitavidaAiContentColors {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AiPanel {
            AiIcon(R.drawable.ic_bitavida_ai)
            AiSectionHeading("Elige el principio activo o la formulación", small = true)
            Text(result.message, style = MaterialTheme.typography.bodyLarge)
        }
        result.candidates.forEach { drug ->
            AiNavigationRow(drug.name, drug.family, R.drawable.ic_v2_pill, motion) {
                onAction(BitavidaAiAction.ReadDrug(drug.id, result.intent))
            }
        }
        AiNavigationRow("Explorar la biblioteca", "Busca por principio activo o familia", R.drawable.ic_v2_file, motion) {
            onAction(BitavidaAiAction.OpenLibrary())
        }
    }
}

@Composable
internal fun AiDrugAnswer(
    answer: EducationResult.DrugAnswer,
    expanded: Set<DrugIntent>,
    sourceExpanded: Boolean,
    pageId: Long,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
    catalog: List<DrugEducation>,
) = BitavidaAiContentColors {
    val drug = answer.drug
    val duration = if (motion) BitavidaFluidMotion.Selection else 0
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AiEyebrow(if (answer.fromLibrary) drug.family.uppercase() else "PRINCIPIO ACTIVO IDENTIFICADO")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(drug.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(drug.summary, style = MaterialTheme.typography.bodyLarge)
            if (drug.aliases.isNotEmpty()) Text("Nombres genéricos alternativos: ${drug.aliases.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        drug.sections.groupBy { it.intent }.entries
            .sortedBy { if (it.key == answer.intent) 0 else 1 }
            .forEach { (intent, sections) ->
            val visible = intent in expanded
            Column {
                AiDisclosure(
                    intent.sectionTitle(), if (visible) "Información educativa" else "Toca para consultar",
                    R.drawable.ic_v2_file, visible,
                    { onAction(BitavidaAiAction.ToggleDrugSection(pageId, intent)) }, motion,
                )
                AnimatedVisibility(visible, enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
                    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        sections.forEach { section ->
                            if (sections.size > 1) AiSectionHeading(section.title, small = true)
                            Text(section.body, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        AiSectionHeading("Preguntas relacionadas", small = true)
        val documentedQuestions = drug.relatedQuestions.filter { question ->
            question.question.isNotBlank() && question.intent != answer.intent &&
                drug.sections.any { it.intent == question.intent && it.body.isNotBlank() }
        }
        val relatedQuestions = if (drug.relatedQuestions.isNotEmpty()) {
            documentedQuestions.map { it.question to it.intent }
        } else {
            listOf(DrugIntent.PURPOSE, DrugIntent.SIDE_EFFECTS, DrugIntent.PRECAUTIONS, DrugIntent.CONSULTATION)
                .filter { it != answer.intent && drug.sections.any { section -> section.intent == it } }
                .map { it.question(drug.name) to it }
        }
        relatedQuestions.take(3).forEach { (question, intent) ->
            AiNavigationRow(question, "Consultar contenido documentado",
                R.drawable.ic_bitavida_ai, motion) {
                onAction(BitavidaAiAction.ReadDrug(drug.id, intent))
            }
        }
        drug.relatedIds.mapNotNull { id -> catalog.find { it.id == id } }.forEach { related ->
            AiNavigationRow(related.name, related.family, R.drawable.ic_v2_pill, motion) {
                onAction(BitavidaAiAction.ReadDrug(related.id))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column {
            AiDisclosure("Fuentes y revisión", "${drug.sources.size} referencias · Revisión clínica pendiente",
                R.drawable.ic_v2_file, sourceExpanded, { onAction(BitavidaAiAction.ToggleSource(pageId)) }, motion)
            AnimatedVisibility(sourceExpanded, enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(drug.statusNote, style = MaterialTheme.typography.bodySmall)
                    Text(drug.availabilityNote, style = MaterialTheme.typography.bodySmall)
                    drug.sources.forEach { source -> AiDrugSource(source) }
                    Text("Referencias incluidas en la app. Abrir el documento externo requiere conexión.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        AiReviewNote()
    }
}

@Composable
private fun AiDrugSource(source: DrugReference) {
    val uri = LocalUriHandler.current
    var unavailable by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(source.institution, style = MaterialTheme.typography.titleSmall)
        Text(source.document, style = MaterialTheme.typography.bodySmall)
        Text("${source.version} · Consulta: ${source.reviewedOn}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { unavailable = runCatching { uri.openUri(source.url) }.isFailure },
            modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)) {
            Text("Consultar documento oficial", style = MaterialTheme.typography.labelLarge)
        }
        if (unavailable) Text("No hay una aplicación disponible para abrir este enlace.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun DrugIntent.sectionTitle(): String = when (this) {
    DrugIntent.IDENTIFY -> "Qué es"
    DrugIntent.PURPOSE -> "Para qué se utiliza"
    DrugIntent.MECHANISM -> "Cómo funciona"
    DrugIntent.SIDE_EFFECTS -> "Efectos y señales importantes"
    DrugIntent.CONTRAINDICATIONS -> "Contraindicaciones"
    DrugIntent.PRECAUTIONS -> "Precauciones"
    DrugIntent.INTERACTIONS -> "Interacciones · Lista no exhaustiva"
    DrugIntent.FORMULATIONS -> "Presentaciones y aspectos prácticos"
    DrugIntent.CONSULTATION -> "Preguntas para el médico"
}

private fun DrugIntent.question(name: String): String = when (this) {
    DrugIntent.PURPOSE -> "¿Para qué sirve $name?"
    DrugIntent.SIDE_EFFECTS -> "¿Qué efectos secundarios tiene $name?"
    DrugIntent.PRECAUTIONS -> "¿Qué precauciones tiene $name?"
    DrugIntent.CONSULTATION -> "¿Qué preguntar al médico sobre $name?"
    else -> sectionTitle()
}
