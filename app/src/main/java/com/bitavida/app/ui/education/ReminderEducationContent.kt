package com.bitavida.app.ui.education

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.education.*
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.measurements.GlucoseCaptureChip
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.theme.BitavidaSpacing

internal fun LazyListScope.reminderLibraryItems(
    state: BitavidaAiUiState,
    catalog: List<EducationEntry>,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
) {
    val filtered = filterEducationLibrary(catalog, null, state.librarySearch, unicodeSearch = true)
        .filter { state.reminderCategory == null || it.reminderCategory == state.reminderCategory }
    item("reminder_library_header") {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            AiSectionHeading("Biblioteca de Recordatorios")
            BitavidaLimitedTextField(
                limit = CaptureLimit.SEARCH,
                value = state.librarySearch,
                onValueChange = { onAction(BitavidaAiAction.SearchLibrary(it)) },
                label = { Text("Buscar temas") },
                placeholder = { Text("Compras, documentos, familia…") },
                modifier = Modifier.fillMaxWidth().testTag("reminder_ai_library_search"),
                singleLine = true, shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            ReminderCategoryFilters(state.reminderCategory, onAction, motion)
            Text("${filtered.size} de ${catalog.size} temas", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (filtered.isEmpty()) item("reminder_library_empty") {
        Text("Prueba otra palabra o elige otro tema.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    var firstCategory = true
    ReminderEducationCategory.entries.forEach { category ->
        val entries = filtered.filter { it.reminderCategory == category }
        if (entries.isNotEmpty()) {
            val topPadding = if (firstCategory) BitavidaSpacing.Small else BitavidaSpacing.Default
            firstCategory = false
            item("reminder_category_" + category.name) {
                Row(
                    Modifier.fillMaxWidth().padding(top = topPadding, bottom = BitavidaSpacing.ExtraSmall)
                        .semantics(mergeDescendants = true) { heading() },
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AiIcon(category.icon(), Modifier.size(20.dp))
                    Text(category.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(entries.size.toString(), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(entries, key = { it.topic.name }) { entry ->
                AiNavigationRow(
                    entry.title, null, category.icon(), motion,
                    modifier = Modifier.testTag("reminder_guide_" + entry.topic.name),
                ) { onAction(BitavidaAiAction.ReadTopic(entry.topic)) }
            }
        }
    }
}

private fun ReminderEducationCategory.icon(): Int = when (this) {
    ReminderEducationCategory.EVERYDAY, ReminderEducationCategory.APP -> R.drawable.ic_v2_clock
    ReminderEducationCategory.SHOPPING, ReminderEducationCategory.TASKS -> R.drawable.ic_v2_clipboard
    ReminderEducationCategory.DOCUMENTS -> R.drawable.ic_v2_file
    ReminderEducationCategory.FAMILY -> R.drawable.ic_v2_home
    ReminderEducationCategory.OUTINGS -> R.drawable.ic_v2_calendar
}

@Composable
private fun ReminderCategoryFilters(
    selected: ReminderEducationCategory?,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
) {
    val categories = remember { listOf<ReminderEducationCategory?>(null) + ReminderEducationCategory.entries }
    val index = categories.indexOf(selected).coerceAtLeast(0)
    val scroll = rememberLazyListState()
    LaunchedEffect(index, motion) {
        if (motion) scroll.animateScrollToItem(index) else scroll.scrollToItem(index)
    }
    LazyRow(state = scroll, modifier = Modifier.fillMaxWidth().selectableGroup()
        .testTag("reminder_ai_library_filters"),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        items(categories, key = { it?.name ?: "ALL" }) { category ->
            GlucoseCaptureChip(
                category?.label ?: "Todos", selected == category, true, motion,
                { onAction(BitavidaAiAction.SelectReminderCategory(category)) },
                modifier = Modifier.testTag("reminder_ai_filter_" + (category?.name ?: "ALL")),
            )
        }
    }
}

@Composable
internal fun ReminderHomeQuestions(
    topics: List<EducationTopic>,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
) {
    Column(Modifier.testTag("reminder_guide_initial_questions")) {
        topics.take(2).forEach { topic ->
            AiLibraryRow(topic, motion) { onAction(BitavidaAiAction.Ask(topic.question)) }
        }
    }
}

/** Learning stays independent from creation. Practical examples are read-only and secondary. */
@Composable
internal fun ReminderEducationalAnswer(
    answer: EducationResult.Answer,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
    modifier: Modifier,
    moreExpanded: Boolean,
    sourceExpanded: Boolean,
    relatedExpanded: Boolean,
    pageId: Long,
) {
    val entry = answer.entry
    val enter = if (motion) fadeIn(tween(BitavidaFluidMotion.Selection)) else EnterTransition.None
    val exit = if (motion) fadeOut(tween(BitavidaFluidMotion.Selection)) else ExitTransition.None
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        Column(
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Text(entry.title, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            AiPanel {
                entry.reminderCategory?.let { AiEyebrow(it.label) }
                Text(entry.explanation, style = MaterialTheme.typography.bodyLarge)
            }
        }
        AiPanel {
            AiDisclosure(
                "Información complementaria", "Contexto y ejemplos de organización",
                R.drawable.ic_v2_file, moreExpanded,
                { onAction(BitavidaAiAction.ToggleDetails(pageId)) }, motion,
            )
            AnimatedVisibility(moreExpanded, enter = enter, exit = exit) {
                Column(
                    Modifier.testTag("reminder_guide_complementary"),
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                ) {
                    entry.importance?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    entry.details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    entry.concepts.forEach { Text("• " + it, style = MaterialTheme.typography.bodyMedium) }
                    if (entry.reminderExamples.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        AiSectionHeading("Ejemplos cotidianos", small = true)
                        entry.reminderExamples.forEachIndexed { index, example ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                                Text(example.title, style = MaterialTheme.typography.titleSmall)
                                Text(example.explanation, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else entry.example?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (entry.related.isNotEmpty()) {
            AiPanel {
                AiDisclosure(
                    "Temas relacionados", "Seguir aprendiendo",
                    R.drawable.ic_v2_file, relatedExpanded,
                    { onAction(BitavidaAiAction.ToggleRelated(pageId)) }, motion,
                )
                AnimatedVisibility(relatedExpanded, enter = enter, exit = exit) {
                    Column(Modifier.testTag("reminder_guide_related")) {
                        entry.related.take(3).forEachIndexed { index, topic ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            AiLibraryRow(topic, motion) { onAction(BitavidaAiAction.ReadTopic(topic)) }
                        }
                    }
                }
            }
        }
        ReminderEducationalSources(entry, sourceExpanded, onAction, pageId, motion)
    }
}

@Composable
private fun ReminderEducationalSources(
    entry: EducationEntry,
    expanded: Boolean,
    onAction: (BitavidaAiAction) -> Unit,
    pageId: Long,
    motion: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    var unavailable by remember(entry.topic) { mutableStateOf(emptySet<String>()) }
    AiPanel {
        AiDisclosure(
            "Fuente y revisión", entry.source.title, R.drawable.ic_v2_file, expanded,
            { onAction(BitavidaAiAction.ToggleSource(pageId)) }, motion,
        )
        AnimatedVisibility(expanded,
            enter = if (motion) fadeIn(tween(BitavidaFluidMotion.Selection)) else EnterTransition.None,
            exit = if (motion) fadeOut(tween(BitavidaFluidMotion.Selection)) else ExitTransition.None) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
                (listOf(entry.source) + entry.additionalSources).forEachIndexed { index, source ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                        Text(source.title, style = MaterialTheme.typography.titleSmall)
                        Text(source.reference, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (source.reference.startsWith("https://")) {
                            CurrentDetailButton(
                                kind = DetailButtonKind.OUTLINED, strongOutline = true,
                                onClick = {
                                    if (runCatching { uriHandler.openUri(source.reference) }.isFailure) {
                                        unavailable = unavailable + source.reference
                                    } else unavailable = unavailable - source.reference
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Consultar referencia") }
                            if (source.reference in unavailable) {
                                Text("No hay una aplicación disponible para abrir este enlace.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("Consulta: ${source.consultedOn}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Ficha de organización v${entry.version} · Contenido editorial de Bitavida.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ReminderOrganizationNote() {
    Text("Orientación general para organizar el cuidado cotidiano.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
