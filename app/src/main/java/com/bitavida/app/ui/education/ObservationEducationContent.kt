package com.bitavida.app.ui.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.bitavida.app.R
import com.bitavida.app.domain.education.EducationEntry
import com.bitavida.app.domain.model.ObservationCatalog
import com.bitavida.app.domain.model.ObservationCategory
import com.bitavida.app.ui.measurements.GlucoseCaptureChip
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.domain.validation.CaptureLimit

internal fun LazyListScope.observationLibraryItems(
    state: BitavidaAiUiState,
    catalog: List<EducationEntry>,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
) {
    val searchMatches = filterEducationLibrary(catalog, null, state.librarySearch, unicodeSearch = true)
    val filtered = searchMatches.filter { entry ->
        state.observationCategory == null ||
            entry.observationId?.let { ObservationCatalog.get(it).category } == state.observationCategory
    }
    item("observation_library_header") {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
            AiEyebrow("CONOCIMIENTO EN TU DISPOSITIVO")
            AiSectionHeading("Biblioteca de observaciones")
            Text(
                "Aprende sobre los 27 tipos de observaciones, incluso sin conexión.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BitavidaLimitedTextField(
                limit = CaptureLimit.SEARCH,
                value = state.librarySearch,
                onValueChange = { onAction(BitavidaAiAction.SearchLibrary(it)) },
                label = { Text("Buscar temas") },
                placeholder = { Text("Pies, sed, caída, visión…") },
                modifier = Modifier.fillMaxWidth().testTag("observation_ai_library_search"),
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            ObservationCategoryFilters(state.observationCategory, onAction, motion)
            Text(
                "${filtered.size} de ${catalog.size} guías",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (filtered.isEmpty()) {
        item("observation_library_empty") {
            AiPanel {
                AiSectionHeading("Probemos con otra palabra", small = true)
                Text(
                    "No encontré una guía con esa búsqueda. Prueba «pies», «sed», «caída» o elige otra categoría.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    ObservationCategory.entries.forEach { category ->
        val entries = filtered.filter { entry ->
            entry.observationId?.let { ObservationCatalog.get(it).category } == category
        }
        if (entries.isNotEmpty()) {
            item("observation_category_${category.name}") {
                AiSectionHeading(category.title, small = true)
            }
            items(entries, key = { it.topic.name }) { entry ->
                AiNavigationRow(
                    entry.title,
                    entry.explanation,
                    categoryIcon(category),
                    motion,
                ) { onAction(BitavidaAiAction.ReadTopic(entry.topic)) }
            }
        }
    }
    item("observation_review") { AiReviewNote() }
}

@Composable
private fun ObservationCategoryFilters(
    selected: ObservationCategory?,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
) {
    val categories = remember { listOf<ObservationCategory?>(null) + ObservationCategory.entries }
    val selectedIndex = categories.indexOf(selected).coerceAtLeast(0)
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewport = maxWidth
        LaunchedEffect(selectedIndex, motion, viewport, fontScale) {
            if (motion) scroll.animateScrollToItem(selectedIndex) else scroll.scrollToItem(selectedIndex)
        }
        LazyRow(
            state = scroll,
            modifier = Modifier.fillMaxWidth().selectableGroup()
                .testTag("observation_ai_library_filters"),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(categories, key = { it?.name ?: "ALL" }) { category ->
                GlucoseCaptureChip(
                    label = category?.shortTitle ?: "Todas",
                    selected = selected == category,
                    enabled = true,
                    motionAllowed = motion,
                    onClick = { onAction(BitavidaAiAction.SelectObservationCategory(category)) },
                    modifier = Modifier.testTag("observation_ai_filter_${category?.name ?: "ALL"}"),
                )
            }
        }
    }
}

private fun categoryIcon(category: ObservationCategory): Int = when (category) {
    ObservationCategory.FEET, ObservationCategory.GENERAL -> R.drawable.ic_v2_stethoscope
    ObservationCategory.DIGESTION -> R.drawable.ic_v2_droplet
    ObservationCategory.MOBILITY -> R.drawable.ic_v2_home
    ObservationCategory.OTHER -> R.drawable.ic_v2_file
    else -> R.drawable.ic_v2_clipboard
}
