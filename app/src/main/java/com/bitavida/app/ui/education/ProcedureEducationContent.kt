package com.bitavida.app.ui.education

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.education.EducationTokenizer
import com.bitavida.app.domain.education.ProcedureCategory
import com.bitavida.app.domain.education.ProcedureGuide
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.components.BitavidaThreadAction
import com.bitavida.app.ui.measurements.GlucoseCaptureChip
import com.bitavida.app.ui.theme.BitavidaSpacing

/** The same educational home primitives as the medication context; text stays in the session. */
@Composable
internal fun ProcedureHomeContent(
    question: String,
    onQuestionChange: (String) -> Unit,
    onAsk: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onAbout: () -> Unit,
    motion: Boolean,
) {
    val examples = listOf(
        "¿Para qué sirve la HbA1c?",
        "¿Qué revisan en una consulta de diabetes?",
        "¿Cómo es un fondo de ojo?",
        "¿Qué sucede durante una revisión de pies?",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AiHero(
            subtitle = "Aprende sobre consultas, estudios y procedimientos relacionados con la diabetes.",
            deviceIndicator = "Información educativa · Sin conexión",
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiSectionHeading("¿Qué te gustaría aprender hoy?", small = true)
            BitavidaLimitedTextField(
                value = question,
                onValueChange = onQuestionChange,
                limit = CaptureLimit.CARE_QUESTION,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pregúntale a Bitavida IA", style = MaterialTheme.typography.bodySmall) },
                placeholder = { Text("Escribe una duda sobre consultas o estudios…", style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = MaterialTheme.shapes.large,
                minLines = 1,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onAsk(question) }),
                supportingText = { Text("Sin nombres ni valores personales.", style = MaterialTheme.typography.bodySmall) },
            )
            BitavidaThreadAction(
                "Encontrar respuesta", R.drawable.ic_bitavida_ai, { onAsk(question) },
                Modifier.fillMaxWidth(), emphasized = true, enabled = question.isNotBlank(),
                motionEnabledOverride = motion,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            AiEyebrow("IDEAS PARA EMPEZAR")
            val large = LocalDensity.current.fontScale >= 1.3f
            BoxWithConstraints {
                val columns = if (maxWidth >= 340.dp && !large) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    examples.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                            row.forEach { text ->
                                AiQuestionCard(text, R.drawable.ic_v2_file, motion, Modifier.weight(1f)) { onAsk(text) }
                            }
                            if (row.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        AiNavigationRow(
            "Biblioteca de consultas y procedimientos",
            "Estudios, valoraciones, tratamientos y cirugías explicados de forma sencilla.",
            R.drawable.ic_v2_file, motion, onOpenLibrary,
        )
        AiNavigationRow("¿Cómo funciona Bitavida IA?", "Conoce su inteligencia local", R.drawable.ic_bitavida_ai, motion, onAbout)
    }
}

/** Preserve cross-listed guides without repeating them in combined search results. */
internal fun filterProcedureLibrary(
    all: List<ProcedureGuide>,
    namedMatches: List<ProcedureGuide>,
    search: String,
    selected: ProcedureCategory?,
): List<ProcedureGuide> {
    val normalized = EducationTokenizer.normalize(search)
    val categoryMatches = if (normalized.isBlank()) emptySet() else ProcedureCategory.entries.filter {
        EducationTokenizer.normalize(it.label).contains(normalized)
    }.toSet()
    val matching = if (search.isBlank()) all else {
        val fromCategory = all.filter { guide -> guide.categories.any { it in categoryMatches } }
        (namedMatches + fromCategory).distinctBy { it.id }
    }
    return matching.filter { selected == null || selected in it.categories }
}

/** One catalogue, grouped by its primary category; a category filter also finds cross-listed guides. */
internal fun LazyListScope.procedureLibraryItems(
    viewModel: BitavidaAiViewModel,
    search: String,
    onSearch: (String) -> Unit,
    selected: ProcedureCategory?,
    onSelect: (ProcedureCategory?) -> Unit,
    onOpen: (String) -> Unit,
    motion: Boolean,
) {
    val all = viewModel.procedureGuides
    val filtered = filterProcedureLibrary(all,
        if (search.isBlank()) emptyList() else viewModel.searchProcedureGuides(search), search, selected)
    item("procedure_library_header") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AiEyebrow("CONOCIMIENTO EN TU DISPOSITIVO")
            AiSectionHeading("Biblioteca de consultas y procedimientos")
            Text("Estudios, valoraciones, tratamientos y cirugías explicados de forma sencilla.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BitavidaLimitedTextField(
                limit = CaptureLimit.SEARCH,
                value = search,
                onValueChange = onSearch,
                label = { Text("Buscar estudio, consulta o procedimiento", style = MaterialTheme.typography.bodyMedium) },
                placeholder = { Text("HbA1c, fondo de ojo, revisión de pies…", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge,
                shape = MaterialTheme.shapes.medium, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                item("all") {
                    GlucoseCaptureChip(label = "Todo", selected = selected == null, enabled = true,
                        motionAllowed = motion, onClick = { onSelect(null) })
                }
                items(ProcedureCategory.entries, key = { it.name }) { category ->
                    GlucoseCaptureChip(label = category.label, selected = selected == category, enabled = true,
                        motionAllowed = motion, onClick = { onSelect(category) })
                }
            }
            Text("${filtered.size} guías", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (filtered.isEmpty()) item("procedure_library_empty") {
        AiPanel {
            AiSectionHeading("Probemos con otra palabra", small = true)
            Text("No encontré guías con esa búsqueda. Puedes probar otro nombre o elegir una categoría.",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
    val grouped = filtered.groupBy { if (selected != null) selected else it.categories.firstOrNull() }
    grouped.forEach { (category, entries) ->
        item("procedure_category_${category?.name ?: "other"}") {
            AiSectionHeading(category?.label ?: "Otros temas", small = true)
        }
        items(entries, key = { "procedure_${it.id}" }) { guide ->
            AiNavigationRow(guide.title, guide.summary ?: guide.description, R.drawable.ic_v2_file, motion) { onOpen(guide.id) }
        }
    }
    item("procedure_library_review") { AiReviewNote() }
}

/** Shared disclosure rows and source presentation used by the medication guide. */
@Composable
internal fun ProcedureGuideContent(
    guide: ProcedureGuide,
    motion: Boolean,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    sourceExpanded: Boolean,
    onToggleSource: () -> Unit,
    onRelated: (String) -> Unit,
    onAsk: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var unavailable by remember(guide.id) { mutableStateOf(false) }
    val duration = if (motion) BitavidaFluidMotion.Selection else 0
    val sections = listOf(
        Triple("what", "Qué es", guide.description),
        Triple("purpose", "Para qué sirve", guide.purpose),
        Triple("during", "Qué sucede durante la atención", guide.during),
        Triple("method", "Cómo suele realizarse", guide.method),
        Triple("preparation", "Preparación", guide.preparation),
        Triple("after", "Qué sucede después", guide.after),
        Triple("questions", "Preguntas para el médico", guide.questions.joinToString("\n") { "• $it" }),
        Triple("important", "Aspectos importantes", guide.important),
    ).filter { it.third.isNotBlank() } + guide.extraSections.filter { it.body.isNotBlank() }
        .map { Triple(it.id, it.title, it.body) }
    val sources = (listOf(guide.source) + guide.additionalSources).distinctBy { it.reference }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AiEyebrow(guide.categories.firstOrNull()?.label?.uppercase().orEmpty())
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(guide.title, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            Text(guide.summary ?: guide.description, style = MaterialTheme.typography.bodyLarge)
            Text(guide.context, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        sections.forEach { (key, title, body) ->
            val visible = key in expanded
            Column {
                AiDisclosure(title, if (visible) "Información educativa" else "Toca para consultar",
                    R.drawable.ic_v2_file, visible, { onToggle(key) }, motion)
                AnimatedVisibility(visible, enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
                    Text(body, modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        AiSectionHeading("Preguntas relacionadas", small = true)
        val relatedQuestions = guide.relatedQuestions.takeIf { it.isNotEmpty() }
            ?.map { it.id to it.question }
            ?: listOf(
                "purpose" to "¿Para qué sirve ${guide.title}?",
                "preparation" to "¿Qué preparación necesita ${guide.title}?",
                "questions" to "¿Qué puedo preguntar al médico sobre ${guide.title}?",
            )
        relatedQuestions.forEach { (section, question) ->
            AiNavigationRow(question, "Consultar contenido documentado", R.drawable.ic_bitavida_ai, motion) {
                onRelated(section)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column {
            AiDisclosure("Fuentes y revisión", "${sources.size} ${if (sources.size == 1) "referencia" else "referencias"} · Revisión clínica pendiente",
                R.drawable.ic_v2_file, sourceExpanded, onToggleSource, motion)
            AnimatedVisibility(sourceExpanded, enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Revisión editorial: ${guide.reviewedOn}" +
                        (guide.contentVersion?.let { " · Versión: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    sources.forEach { source ->
                        Text(source.title, style = MaterialTheme.typography.titleSmall)
                        Text("Consulta: ${source.consultedOn}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (source.reference.startsWith("https://")) {
                            TextButton(onClick = {
                                unavailable = runCatching { uri.openUri(source.reference) }.isFailure
                            }, modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)) {
                                Text("Consultar documento oficial", style = MaterialTheme.typography.labelLarge)
                            }
                        } else Text(source.reference, style = MaterialTheme.typography.bodySmall)
                    }
                    sections.forEach { (key, title, _) ->
                        val references = guide.sectionSourceReferences[key].orEmpty() +
                            guide.extraSections.firstOrNull { it.id == key }?.sourceReferences.orEmpty()
                        val names = sources.filter { it.reference in references }.joinToString { it.title }
                        if (names.isNotBlank()) Text("$title: $names",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (unavailable) Text("No hay una aplicación disponible para abrir este enlace.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Las referencias están incluidas en la app. Abrir los documentos requiere conexión.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        AiReviewNote()
        AiNavigationRow("Pregúntale a Bitavida IA", "Explora otra duda", R.drawable.ic_bitavida_ai, motion, onAsk)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Portada Citas claro", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "Portada Citas oscuro", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Portada Citas compacto", showBackground = true, widthDp = 320)
@androidx.compose.ui.tooling.preview.Preview(name = "Portada Citas 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun ProcedureHomePreview() {
    com.bitavida.app.ui.theme.BitavidaTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
        com.bitavida.app.ui.components.BitavidaFluidBackground(Modifier.fillMaxSize(), motionAllowed = false) {
            BitavidaAiContentColors {
                androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    item { ProcedureHomeContent("", {}, {}, {}, {}, motion = false) }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Biblioteca Citas claro", showBackground = true, widthDp = 390, heightDp = 900)
@androidx.compose.ui.tooling.preview.Preview(name = "Biblioteca Citas oscuro", showBackground = true, widthDp = 390, heightDp = 900, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Biblioteca Citas 200%", showBackground = true, widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable private fun ProcedureLibraryPreview() {
    val viewModel = remember {
        BitavidaAiViewModel(com.bitavida.app.domain.usecase.AskBitavidaAi(
            com.bitavida.app.data.education.BundledEducationRepository(loadModelText = { "" })
        ))
    }
    com.bitavida.app.ui.theme.BitavidaTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
        com.bitavida.app.ui.components.BitavidaFluidBackground(Modifier.fillMaxSize(), motionAllowed = false) {
            BitavidaAiContentColors {
                androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    procedureLibraryItems(viewModel, "", {}, null, {}, {}, motion = false)
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "OCT método claro", widthDp = 390, heightDp = 1800, showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "OCT método oscuro", widthDp = 390, heightDp = 1800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "OCT método compacto", widthDp = 320, heightDp = 1800, showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "OCT método 200%", widthDp = 320, heightDp = 1800, fontScale = 2f, showBackground = true)
@Composable private fun OctGuideInlinePreview() {
    val guide = com.bitavida.app.data.education.BundledEducationRepository(loadModelText = { "" })
        .procedures().single { it.id == "oct" }
    com.bitavida.app.ui.theme.BitavidaTheme {
        com.bitavida.app.ui.components.BitavidaFluidBackground(Modifier.fillMaxSize(), motionAllowed = false) {
            BitavidaAiContentColors {
                androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    item {
                        ProcedureGuideContent(
                            guide = guide, motion = false, expanded = setOf("method"),
                            onToggle = {}, sourceExpanded = false, onToggleSource = {},
                            onRelated = {}, onAsk = {},
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Ficha Citas claro", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "Ficha Citas oscuro", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Ficha Citas 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun ProcedureGuidePreview() {
    com.bitavida.app.ui.theme.BitavidaTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
        com.bitavida.app.ui.components.BitavidaFluidBackground(Modifier.fillMaxSize(), motionAllowed = false) {
            BitavidaAiContentColors {
                androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    item {
                        ProcedureGuideContent(
                            guide = com.bitavida.app.data.education.ProcedureGuideCatalog.entries.first(),
                            motion = false, expanded = emptySet(), onToggle = {}, sourceExpanded = false,
                            onToggleSource = {}, onRelated = {}, onAsk = {},
                        )
                    }
                }
            }
        }
    }
}
