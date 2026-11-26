package com.bitavida.app.ui.education

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.education.*
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors

/** Editorial response: sections breathe on the page rather than inside a giant card. */
@Composable
internal fun AiAnswerCard(
    answer: EducationResult.Answer,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
    modifier: Modifier = Modifier,
    developed: Boolean = false,
    sourceExpanded: Boolean = false,
    moreExpanded: Boolean = developed,
    pageId: Long = 0,
    relatedExpanded: Boolean = false,
) = BitavidaAiContentColors {
    val entry = answer.entry
    if (entry.reminderCategory != null) {
        ReminderEducationalAnswer(answer, onAction, motion, modifier, moreExpanded, sourceExpanded, relatedExpanded, pageId)
        return@BitavidaAiContentColors
    }
    val uriHandler = LocalUriHandler.current
    var unavailableSources by remember(entry.topic) { mutableStateOf(emptySet<String>()) }
    val duration = if (motion) BitavidaFluidMotion.Selection else 0
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiIcon(entry.topic.group.icon(), Modifier.size(20.dp))
            AiEyebrow(if (answer.fromLibrary) entry.topic.group.label.uppercase()
                else "TEMA IDENTIFICADO · ${entry.topic.group.label}")
        }
        Column(
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(entry.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(entry.explanation, style = MaterialTheme.typography.bodyLarge)
        }
        entry.importance?.let {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiSectionHeading("¿Por qué es importante?", small = true)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        entry.example?.let { example ->
            AiPanel(tint = MaterialTheme.bitavidaColors.mint) {
                AiEyebrow("EN LA VIDA COTIDIANA")
                Text(example, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (entry.details.isNotEmpty() || entry.concepts.isNotEmpty() ||
            entry.possibleMeanings.isNotEmpty() || entry.whatToObserve.isNotEmpty() ||
            entry.delayedAttention != null || entry.medicalGuidance != null
        ) {
            Column {
                AiDisclosure(
                    if (developed) "Para comprenderlo mejor" else "Un poco más sobre este tema",
                    if (moreExpanded) "Información complementaria" else "Conceptos y contexto",
                    R.drawable.ic_v2_file, moreExpanded, { onAction(BitavidaAiAction.ToggleDetails(pageId)) }, motion,
                )
                AnimatedVisibility(moreExpanded, enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        entry.details.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        if (entry.possibleMeanings.isNotEmpty()) {
                            AiSectionHeading("¿Qué podría significar?", small = true)
                            EducationBullets(entry.possibleMeanings)
                        }
                        entry.delayedAttention?.let {
                            AiSectionHeading("¿Qué puede pasar si se retrasa la atención?", small = true)
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (entry.whatToObserve.isNotEmpty()) {
                            AiSectionHeading("¿Qué conviene observar y registrar?", small = true)
                            EducationBullets(entry.whatToObserve)
                        }
                        entry.medicalGuidance?.let {
                            AiSectionHeading("¿Cuándo buscar orientación médica?", small = true)
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (entry.concepts.isNotEmpty()) {
                            AiSectionHeading("Ideas para recordar", small = true)
                            EducationBullets(entry.concepts)
                        }
                    }
                }
            }
        }
        if (!developed) {
            AiNavigationRow("Explorar el tema completo", "Más contexto, ejemplos y conceptos", R.drawable.ic_v2_file, motion) {
                onAction(BitavidaAiAction.ReadTopic(entry.topic))
            }
        }
        if (entry.related.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiSectionHeading("Preguntas relacionadas", small = true)
                entry.related.forEach { topic ->
                    AiLibraryRow(topic, motion) { onAction(BitavidaAiAction.ReadTopic(topic)) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column {
            AiDisclosure(
                "Fuente y revisión",
                if (entry.additionalSources.isEmpty()) entry.source.title
                else "${entry.source.title} y ${entry.additionalSources.size} más",
                R.drawable.ic_v2_file,
                sourceExpanded, { onAction(BitavidaAiAction.ToggleSource(pageId)) }, motion,
            )
            AnimatedVisibility(sourceExpanded, enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    (listOf(entry.source) + entry.additionalSources).forEach { source ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(source.title, style = MaterialTheme.typography.labelLarge)
                            Text(source.reference, style = MaterialTheme.typography.bodySmall)
                            if (source.reference.startsWith("https://")) {
                                TextButton(
                                    onClick = {
                                        val failed = runCatching { uriHandler.openUri(source.reference) }.isFailure
                                        unavailableSources = if (failed) unavailableSources + source.reference
                                        else unavailableSources - source.reference
                                    },
                                    modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
                                ) { Text("Consultar documento oficial") }
                                if (source.reference in unavailableSources) {
                                    Text(
                                        "No hay una aplicación disponible para abrir este enlace.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Text(
                                "Consulta: ${source.consultedOn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "Ficha v${entry.version} · Pendiente de revisión clínica humana. Las referencias no implican aprobación de la ficha por las instituciones citadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AiReviewNote()
    }
}

@Composable
private fun EducationBullets(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = MaterialTheme.colorScheme.primary)
                Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun AiFallbackCard(
    result: EducationResult.Fallback,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
    reminderContext: Boolean = false,
) = BitavidaAiContentColors {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AiPanel(tint = MaterialTheme.bitavidaColors.paleBlue) {
            AiIcon(R.drawable.ic_bitavida_ai, Modifier.size(28.dp))
            Text(
                when (result.reason) {
                    EducationFallback.UNCERTAIN -> "Acerquémonos a tu pregunta"
                    EducationFallback.OUTSIDE_SCOPE -> "Aprendamos de forma general"
                    EducationFallback.UNAVAILABLE -> "Puedes seguir explorando"
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            Text(
                when (result.reason) {
                    EducationFallback.UNCERTAIN ->
                        "No tengo suficiente certeza para elegir una explicación. Puedes precisar la pregunta o explorar uno de estos temas."
                    EducationFallback.OUTSIDE_SCOPE -> if (reminderContext)
                        "Aquí puedes organizar compras, documentos y apoyo cotidiano. Para medicamentos, mediciones, observaciones o citas, utiliza su módulo de Plan de cuidado."
                    else "El asistente explica conceptos, pero no valora cifras personales, síntomas ni tratamientos. Consulta a tu equipo de salud; si necesitas atención urgente, contacta a los servicios de emergencia de tu localidad."
                    EducationFallback.UNAVAILABLE ->
                        "No pude clasificar esta pregunta. La biblioteca continúa disponible sin conexión."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (result.suggestions.isNotEmpty()) {
            AiSectionHeading("Quizá te refieres a…", small = true)
            result.suggestions.take(3).forEach { topic ->
                AiLibraryRow(topic, motion) { onAction(BitavidaAiAction.ReadTopic(topic)) }
            }
        }
        AiNavigationRow("Explorar la biblioteca", "Elige un tema sin hacer una pregunta", R.drawable.ic_v2_file, motion) {
            onAction(BitavidaAiAction.OpenLibrary())
        }
    }
}
