package com.bitavida.app.ui.education

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import com.bitavida.app.ui.measurements.GlucoseCaptureChip
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.education.*
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaThreadAction
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaSpacing

@Composable
fun BitavidaAiRoute(
    viewModel: BitavidaAiViewModel,
    navigationContext: EducationGroup?,
    modifier: Modifier = Modifier,
    onExit: () -> Unit = {},
    exitLabel: String = "Mediciones",
    central: Boolean = false,
    active: Boolean = true,
    procedureContext: Boolean = false,
    procedureInitialIds: List<String> = emptyList(),
) {
    val state by viewModel.state.collectAsState()
    val motion = rememberBitavidaMotionAllowed(null)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val back = {
        if (active) {
            if (state.canGoBack) viewModel.onAction(BitavidaAiAction.Back) else onExit()
        }
    }
    BackHandler(enabled = active, onBack = back)
    LaunchedEffect(viewModel, navigationContext, central, procedureContext, procedureInitialIds) {
        if (procedureContext) viewModel.onAction(BitavidaAiAction.StartProcedureSession(procedureInitialIds))
        else {
            viewModel.onAction(BitavidaAiAction.NavigationContext(navigationContext))
            viewModel.onAction(BitavidaAiAction.CentralContext(central))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            keyboard?.hide()
            focus.clearFocus()
        }
    }
    if (procedureContext && !state.procedureSessionActive) return
    val entrance = remember { Animatable(if (central && motion) 0f else 1f) }
    LaunchedEffect(central, motion) {
        if (central && motion) entrance.animateTo(1f, tween(BitavidaFluidMotion.StateTransition))
        else entrance.snapTo(1f)
    }
    val safeModifier = if (central) Modifier.safeDrawingPadding() else Modifier
    Column(modifier.fillMaxSize().then(safeModifier).graphicsLayer { alpha = entrance.value }
        .testTag("bitavida_ai")) {
        if (central) AiCentralHeader(back) else CurrentMedicationBack(
            onBack = back,
            label = if (state.canGoBack) when (state.pages[state.pages.lastIndex - 1].page) {
                AiPage.LIBRARY -> "Biblioteca"
                AiPage.DETAIL -> "Tema anterior"
                AiPage.ANSWER -> "Respuesta"
                else -> "Bitavida IA"
            } else exitLabel,
            motionAllowed = motion,
            enabled = true,
        )
        val duration = if (motion) BitavidaFluidMotion.StateTransition else 0
        AnimatedContent(
            targetState = state.current,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentKey = { it.id },
            transitionSpec = {
                val forward = targetState.id > initialState.id
                (fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) {
                    if (!motion) 0 else if (forward) it / 16 else -it / 16
                }).togetherWith(fadeOut(tween(duration / 2)) + slideOutHorizontally(tween(duration)) {
                    if (!motion) 0 else if (forward) -it / 24 else it / 24
                })
            },
            label = "ai_destination",
        ) { entry ->
            val scroll = remember(entry.id) {
                LazyListState(viewModel.scrollIndex(entry.id), viewModel.scrollOffset(entry.id))
            }
            LaunchedEffect(entry.id, scroll) {
                snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }
                    .collect { (index, offset) -> viewModel.rememberScroll(entry.id, index, offset) }
            }
            DisposableEffect(entry.id, scroll) {
                onDispose {
                    viewModel.rememberScroll(entry.id, scroll.firstVisibleItemIndex, scroll.firstVisibleItemScrollOffset)
                }
            }
            AiPageList(entry, state, viewModel.catalog, viewModel::onAction, scroll, motion, viewModel.drugs, central, viewModel)
        }
    }
}

@Composable
internal fun AiCentralHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = BitavidaSpacing.ScreenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Icon(painterResource(R.drawable.ic_v2_arrow_back), contentDescription = "Atrás",
                tint = MaterialTheme.colorScheme.primary)
        }
        Text("Bitavida IA", modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
internal fun AiPageList(
    entry: AiPageEntry,
    state: BitavidaAiUiState,
    catalog: List<EducationEntry>,
    onAction: (BitavidaAiAction) -> Unit,
    scroll: LazyListState,
    motion: Boolean,
    drugs: List<DrugEducation> = emptyList(),
    central: Boolean = false,
    viewModel: BitavidaAiViewModel? = null,
) = BitavidaAiContentColors {
    val visibleCatalog = remember(catalog, state.medicationContext, state.observationContext, state.reminderContext, central) {
        catalog.filter { entry ->
            when {
                central -> true
                state.reminderContext -> entry.topic.group == EducationGroup.REMINDERS
                state.medicationContext -> entry.topic.group == EducationGroup.MEDICATIONS
                state.observationContext -> entry.observationId != null
                else -> entry.topic.group != EducationGroup.MEDICATIONS &&
                    entry.topic.group != EducationGroup.OBSERVATIONS && entry.topic.group != EducationGroup.REMINDERS
            }
        }
    }
    val filtered = remember(visibleCatalog, state.libraryGroup, state.librarySearch) {
        filterEducationLibrary(visibleCatalog, state.libraryGroup, state.librarySearch, unicodeSearch = state.careCaptureContext)
    }
    val filteredDrugs = remember(drugs, state.libraryGroup, state.librarySearch, central) {
        if (central && (state.libraryGroup == null || state.libraryGroup == EducationGroup.MEDICATIONS))
            filterDrugLibrary(drugs, null, state.librarySearch) else emptyList()
    }
    val filteredProcedures = remember(viewModel, state.libraryGroup, state.librarySearch, central) {
        if (central && state.libraryGroup == null && state.librarySearch.isNotBlank())
            viewModel?.searchProcedureGuides(state.librarySearch).orEmpty() else emptyList()
    }
    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (central) BitavidaSpacing.ScreenHorizontal else 0.dp,
            end = if (central) BitavidaSpacing.ScreenHorizontal else 0.dp,
            top = 8.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(
            if (state.reminderContext && entry.page == AiPage.LIBRARY) BitavidaSpacing.Small else BitavidaSpacing.Default,
        ),
    ) {
        when (entry.page) {
            AiPage.HOME -> {
                item("home") { BitavidaAiContent(state, onAction, motionOverride = motion, central = central) }
                if (central) item("central_categories") {
                    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                        AiSectionHeading("Explora por tema", small = true)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                            items(EducationGroup.entries, key = { it.name }) { group ->
                                GlucoseCaptureChip(
                                    label = if (group == EducationGroup.PRESSURE) "Presión arterial" else group.label,
                                    selected = false, enabled = true, motionAllowed = motion,
                                    onClick = { onAction(BitavidaAiAction.OpenLibrary(group)) })
                            }
                            item("appointments") {
                                GlucoseCaptureChip(label = "Citas y estudios", selected = false, enabled = true,
                                    motionAllowed = motion, onClick = { onAction(BitavidaAiAction.OpenProcedureLibrary) })
                            }
                            item("daily_care") {
                                GlucoseCaptureChip(label = "Cuidado cotidiano", selected = false, enabled = true,
                                    motionAllowed = motion,
                                    onClick = { onAction(BitavidaAiAction.OpenLibrary(EducationGroup.REMINDERS)) })
                            }
                        }
                    }
                }
            }
            AiPage.ANSWER -> {
                if (entry.submittedQuestion.isNotBlank() && !state.reminderContext) item("question") {
                    AiEyebrow("TU PREGUNTA")
                    Text(entry.submittedQuestion, style = MaterialTheme.typography.titleMedium)
                }
                item("answer") {
                    when (val result = entry.answer) {
                        is EducationResult.Answer -> AiAnswerCard(result, onAction, motion,
                            sourceExpanded = state.sourceVisibility[entry.id] ?: false,
                            moreExpanded = state.detailsVisibility[entry.id] ?: false, pageId = entry.id,
                            relatedExpanded = state.relatedVisibility[entry.id] ?: false)
                        is EducationResult.Fallback -> AiFallbackCard(result, onAction, motion, state.reminderContext)
                        is EducationResult.DrugAnswer -> AiDrugAnswer(result,
                            state.drugSections[entry.id].orEmpty(), state.sourceVisibility[entry.id] ?: false,
                            entry.id, onAction, motion, drugs)
                        is EducationResult.DrugOptions -> AiDrugOptions(result, onAction, motion)
                        null -> Unit
                    }
                }
                item("new") {
                    BitavidaThreadAction(
                        "Hacer otra pregunta", R.drawable.ic_bitavida_ai,
                        { onAction(BitavidaAiAction.NewQuestion) },
                        Modifier.fillMaxWidth(), emphasized = true, motionEnabledOverride = motion,
                    )
                }
            }
            AiPage.DETAIL -> {
                val answer = entry.answer as? EducationResult.Answer
                if (answer != null) item("detail") {
                    AiAnswerCard(answer, onAction, motion, developed = true,
                        sourceExpanded = state.sourceVisibility[entry.id] ?: false,
                        moreExpanded = state.detailsVisibility[entry.id] ?: !state.reminderContext, pageId = entry.id,
                        relatedExpanded = state.relatedVisibility[entry.id] ?: false)
                }
                val drugAnswer = entry.answer as? EducationResult.DrugAnswer
                if (drugAnswer != null) item("drug_detail") {
                    AiDrugAnswer(drugAnswer, state.drugSections[entry.id].orEmpty(),
                        state.sourceVisibility[entry.id] ?: false, entry.id, onAction, motion, drugs)
                }
                item("new") {
                    AiNavigationRow("Pregúntale a Bitavida IA", "Explora otra duda", R.drawable.ic_bitavida_ai, motion) {
                        onAction(BitavidaAiAction.NewQuestion)
                    }
                }
            }
            AiPage.LIBRARY -> if (state.reminderContext) {
                reminderLibraryItems(state, visibleCatalog, onAction, motion)
            } else if (state.medicationContext) {
                medicationLibraryItems(state, drugs, visibleCatalog, onAction, motion)
            } else if (state.observationContext) {
                observationLibraryItems(state, visibleCatalog, onAction, motion)
            } else {
                item("library_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AiEyebrow("CONOCIMIENTO EN TU DISPOSITIVO")
                        AiSectionHeading("Biblioteca educativa")
                        Text(
                            "Aprende a tu ritmo, incluso sin conexión.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BitavidaLimitedTextField(
                            limit = CaptureLimit.SEARCH,
                            value = state.librarySearch,
                            onValueChange = { onAction(BitavidaAiAction.SearchLibrary(it)) },
                            label = { Text("Buscar contenido", style = MaterialTheme.typography.bodyMedium) },
                            placeholder = { Text("Ayunas, presión, gráficas…", style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().testTag("ai_library_search"),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        )
                        AiGroupFilters(state.libraryGroup, onAction, motion, includeAll = central)
                        Text(
                            if (central) "${filtered.size} temas · ${filteredDrugs.size} fichas de medicamentos · ${if (state.librarySearch.isBlank()) viewModel?.procedureGuides?.size ?: 0 else filteredProcedures.size} guías de citas"
                            else "${filtered.size} de ${visibleCatalog.size} temas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                if (filtered.isEmpty() && filteredDrugs.isEmpty() && filteredProcedures.isEmpty() && !(central && state.librarySearch.isBlank() && state.libraryGroup == null)) item("empty") {
                    AiPanel {
                        AiSectionHeading("Probemos con otra palabra", small = true)
                        Text("No encontré temas con esa búsqueda. Prueba «glucosa» o elige otra categoría.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                EducationGroup.entries.forEach { group ->
                    val entries = filtered.filter { it.topic.group == group }
                    if (entries.isNotEmpty()) {
                        item("group_${group.name}") { AiSectionHeading(group.label, small = true) }
                        items(entries, key = { it.topic.name }) { topic ->
                            AiNavigationRow(topic.title, topic.topic.question, group.icon(), motion) {
                                onAction(BitavidaAiAction.ReadTopic(topic.topic))
                            }
                        }
                    }
                }
                if (filteredDrugs.isNotEmpty()) {
                    item("central_drugs_heading") { AiSectionHeading("Fichas de medicamentos", small = true) }
                    items(filteredDrugs, key = { "central_drug_${it.id}" }) { drug ->
                        AiNavigationRow(drug.name, drug.summary, R.drawable.ic_v2_pill, motion) {
                            onAction(BitavidaAiAction.ReadDrug(drug.id))
                        }
                    }
                }
                if (central && state.libraryGroup == null && state.librarySearch.isBlank()) {
                    item("central_procedures") {
                        AiNavigationRow("Citas y estudios",
                            "Consultas, análisis, procedimientos y tratamientos especializados.",
                            R.drawable.ic_v2_calendar, motion) { onAction(BitavidaAiAction.OpenProcedureLibrary) }
                    }
                }
                if (filteredProcedures.isNotEmpty()) {
                    item("central_procedures_heading") { AiSectionHeading("Citas y estudios", small = true) }
                    items(filteredProcedures, key = { "central_procedure_${it.id}" }) { guide ->
                        AiNavigationRow(guide.title, guide.summary ?: guide.description, R.drawable.ic_v2_file, motion) {
                            onAction(BitavidaAiAction.OpenProcedureGuide(guide.id))
                        }
                    }
                }
                item("review") { AiReviewNote() }
            }
            AiPage.PROCEDURE_HOME -> item("procedure_home") {
                ProcedureHomeContent(state.question,
                    { onAction(BitavidaAiAction.QuestionChanged(it)) },
                    { onAction(BitavidaAiAction.AskProcedure(it)) },
                    { onAction(BitavidaAiAction.OpenProcedureLibrary) },
                    { onAction(BitavidaAiAction.OpenExplanation) }, motion)
            }
            AiPage.PROCEDURE_LIBRARY -> {
                if (viewModel != null) procedureLibraryItems(viewModel, state.procedureSearch,
                    { onAction(BitavidaAiAction.SearchProcedure(it)) }, state.procedureCategory,
                    { onAction(BitavidaAiAction.SelectProcedureCategory(it)) },
                    { onAction(BitavidaAiAction.OpenProcedureGuide(it)) }, motion)
            }
            AiPage.PROCEDURE_DETAIL -> {
                val guide = viewModel?.procedureGuides?.firstOrNull { it.id == entry.procedureId }
                if (guide != null) item("procedure_detail") {
                    ProcedureGuideContent(guide, motion, state.procedureSections[entry.id].orEmpty(),
                        { onAction(BitavidaAiAction.ToggleProcedureSection(entry.id, it)) },
                        state.sourceVisibility[entry.id] ?: false,
                        { onAction(BitavidaAiAction.ToggleSource(entry.id)) },
                        { onAction(BitavidaAiAction.ReadProcedureSection(guide.id, it)) },
                        {
                            onAction(BitavidaAiAction.QuestionChanged(guide.title))
                            onAction(BitavidaAiAction.OpenProcedureHome)
                        })
                }
            }
            AiPage.PROCEDURE_ANSWER -> {
                if (entry.submittedQuestion.isNotBlank()) item("procedure_question") {
                    AiEyebrow("TU PREGUNTA")
                    Text(entry.submittedQuestion, style = MaterialTheme.typography.titleMedium)
                }
                when (val result = entry.procedureResult) {
                    is ProcedureQuestionResult.Match -> item("procedure_answer") {
                        val normalized = EducationTokenizer.normalize(entry.submittedQuestion)
                        AiPanel {
                            Text(result.guide.title, style = MaterialTheme.typography.titleMedium)
                            val octSection = if (result.guide.id == "oct") {
                                entry.procedureSection ?: octFollowupSection(entry.submittedQuestion)
                            } else null
                            val octText = octSection?.let { procedureSectionText(result.guide, it) }
                            when {
                                octText != null -> Text(octText, style = MaterialTheme.typography.bodyLarge)
                                entry.procedureSection == "preparation" || "preparacion" in normalized -> Text(result.guide.preparation, style = MaterialTheme.typography.bodyLarge)
                                entry.procedureSection == "questions" || "preguntar" in normalized -> result.guide.questions.forEach {
                                    Text("• $it", style = MaterialTheme.typography.bodyMedium)
                                }
                                entry.procedureSection == null && listOf("revisan", "sucede", "durante", "hacen", "como es").any { it in normalized } ->
                                    Text(result.guide.during, style = MaterialTheme.typography.bodyLarge)
                                else -> {
                                    Text(result.guide.description, style = MaterialTheme.typography.bodyLarge)
                                    Text(result.guide.purpose, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Text("Información de una ficha local; no es texto generado.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AiNavigationRow("Leer guía y fuentes", result.guide.source.title,
                                R.drawable.ic_v2_file, motion) {
                                onAction(BitavidaAiAction.OpenProcedureGuide(result.guide.id))
                            }
                        }
                    }
                    is ProcedureQuestionResult.Choices -> {
                        item("procedure_choices") { Text("Elige el tema que buscas.", style = MaterialTheme.typography.bodyMedium) }
                        items(result.guides, key = { "procedure_choice_${it.id}" }) { guide ->
                            AiNavigationRow(guide.title, guide.summary ?: guide.description, R.drawable.ic_v2_file, motion) {
                                onAction(BitavidaAiAction.OpenProcedureGuide(guide.id))
                            }
                        }
                    }
                    ProcedureQuestionResult.Urgent -> item("procedure_urgent") {
                        AiPanel { Text("Si puede tratarse de una emergencia, busca atención profesional urgente de inmediato. No esperes a completar esta consulta.", style = MaterialTheme.typography.bodyLarge) }
                    }
                    ProcedureQuestionResult.OutsideScope -> item("procedure_outside") {
                        AiPanel { Text("No puedo interpretar resultados ni tomar decisiones sobre atención personal. Consulta al equipo médico.", style = MaterialTheme.typography.bodyLarge) }
                    }
                    ProcedureQuestionResult.Unknown, null -> item("procedure_unknown") {
                        AiPanel { Text("No tengo información suficiente sobre ese procedimiento. Puedes consultar al equipo médico para conocer sus características.", style = MaterialTheme.typography.bodyLarge) }
                    }
                }
                item("procedure_answer_review") { AiReviewNote() }
                item("procedure_another") {
                    BitavidaThreadAction(
                        "Hacer otra pregunta", R.drawable.ic_bitavida_ai,
                        {
                            if (state.procedureSessionActive) {
                                onAction(BitavidaAiAction.QuestionChanged(""))
                                onAction(BitavidaAiAction.OpenProcedureHome)
                            } else onAction(BitavidaAiAction.NewQuestion)
                        },
                        Modifier.fillMaxWidth(), emphasized = true, motionEnabledOverride = motion,
                    )
                }
            }
            AiPage.ABOUT -> item("about") { AiAboutContent(state.reminderContext, state.pages.any { it.page == AiPage.PROCEDURE_HOME }) }
        }
    }
}

/** Compact home only. Complete answers and the library belong to their own destinations. */
@Composable
fun BitavidaAiContent(
    state: BitavidaAiUiState,
    onAction: (BitavidaAiAction) -> Unit,
    modifier: Modifier = Modifier,
    motionOverride: Boolean? = null,
    central: Boolean = false,
) = BitavidaAiContentColors {
    val motion = rememberBitavidaMotionAllowed(motionOverride)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AiHero(
            subtitle = if (state.reminderContext)
                "Tu asistente inteligente para organizar los pendientes del cuidado"
            else if (state.observationContext)
                "Aprende a reconocer cambios importantes y acompañar mejor a quien cuidas"
            else "Tu asistente inteligente para aprender a cuidar",
            deviceIndicator = if (state.observationContext)
                "Inteligencia en tu dispositivo · Sin Internet"
            else "IA en tu dispositivo · Sin Internet",
            central = central,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiSectionHeading(if (state.reminderContext) "¿Qué te gustaría organizar?" else "¿Qué te gustaría aprender hoy?", small = true)
            if (state.careCaptureContext) BitavidaLimitedTextField(
                value = state.question,
                onValueChange = { onAction(BitavidaAiAction.QuestionChanged(it)) },
                limit = CaptureLimit.CARE_QUESTION,
                modifier = Modifier.fillMaxWidth().testTag("bitavida_ai_question"),
                label = { Text("Pregúntale a Bitavida IA", style = MaterialTheme.typography.bodySmall) },
                placeholder = { Text(if (state.reminderContext) "Compras, documentos, ayuda familiar…" else "Escribe una duda sobre el cuidado…", style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = MaterialTheme.shapes.large,
                minLines = 1, maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onAction(BitavidaAiAction.Ask()) }),
                supportingText = { Text("Sin nombres ni valores personales.", style = MaterialTheme.typography.bodySmall) },
            ) else OutlinedTextField(
                value = state.question,
                onValueChange = { onAction(BitavidaAiAction.QuestionChanged(it)) },
                modifier = Modifier.fillMaxWidth().testTag("bitavida_ai_question"),
                label = { Text("Pregúntale a Bitavida IA", style = MaterialTheme.typography.bodySmall) },
                placeholder = { Text(if (state.reminderContext) "Compras, documentos, ayuda familiar…" else "Escribe una duda sobre el cuidado…", style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = MaterialTheme.shapes.large,
                minLines = 1, maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onAction(BitavidaAiAction.Ask()) }),
                isError = state.inputLimitReached,
                supportingText = {
                    Text(
                        if (state.inputLimitReached) "Máximo 500 caracteres." else "Sin nombres ni valores personales.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            BitavidaThreadAction(
                if (state.analyzing) "Clasificando en tu dispositivo…" else "Encontrar respuesta",
                R.drawable.ic_bitavida_ai, { onAction(BitavidaAiAction.Ask()) },
                Modifier.fillMaxWidth().testTag("bitavida_ai_ask"),
                emphasized = true, enabled = state.question.isNotBlank() && !state.analyzing,
                motionEnabledOverride = motion,
            )
            if (state.analyzing) AiAnalysis(motion)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiEyebrow(state.navigationContext?.takeIf { it == state.group }?.let { "IDEAS DESDE ${it.label.uppercase()}" } ?: "IDEAS PARA EMPEZAR")
            if (state.medicationContext) AiMedicationSuggestions(onAction, motion)
            else if (state.reminderContext) ReminderHomeQuestions(state.suggestions, onAction, motion)
            else AiQuestionGrid(state.suggestions, onAction, motion)
        }
        AiNavigationRow(
            when {
                state.reminderContext -> "Biblioteca de Recordatorios"
                state.medicationContext -> "Biblioteca de medicamentos"
                state.observationContext -> "Biblioteca de observaciones"
                else -> "Biblioteca educativa"
            },
            when {
                state.reminderContext -> "Organización cotidiana y uso de Recordatorios"
                state.medicationContext -> "Principios activos, efectos y precauciones"
                state.observationContext -> "Aprende sobre los 27 tipos de observaciones"
                central -> "Medicamentos, mediciones, observaciones, recordatorios y citas"
                else -> "30 temas para aprender a cuidar"
            },
            R.drawable.ic_v2_file, motion,
        ) { onAction(BitavidaAiAction.OpenLibrary()) }
        AiNavigationRow("¿Cómo funciona Bitavida IA?", "Conoce su inteligencia local", R.drawable.ic_bitavida_ai, motion) {
            onAction(BitavidaAiAction.OpenExplanation)
        }
    }
}

@Composable
internal fun AiAboutContent(reminderContext: Boolean = false, procedureContext: Boolean = false) = BitavidaAiContentColors {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        AiHero()
        AiSectionHeading("Inteligencia local, información clara")
        Text(
            "Bitavida utiliza inteligencia artificial directamente en tu dispositivo para comprender tus preguntas y encontrar información educativa relacionada. No necesita Internet.",
            style = MaterialTheme.typography.bodyLarge,
        )
        AiExplanationStep("01", if (procedureContext) "Busca guías documentadas" else "El modelo reconoce una intención",
            if (procedureContext) "En Citas, la app busca nombres y sinónimos en el catálogo local. Puede pedirte elegir entre temas cercanos; no usa un modelo generativo."
            else "Multinomial Naive Bayes usa patrones aprendidos de preguntas en español. Tolera variaciones conocidas; puede equivocarse o pedirte elegir entre temas cercanos.")
        AiExplanationStep("02", "La biblioteca contiene la explicación",
            if (reminderContext) "Las respuestas y las ideas de organización están incluidas en la app. El clasificador busca un tema; algunas propuestas de redacción usan plantillas. No genera contenido nuevo ni decisiones médicas."
            else if (procedureContext) "Las fichas de consultas y procedimientos están incluidas en la app y requieren revisión clínica. No generan decisiones, interpretan cifras ni realizan diagnósticos."
            else "Las respuestas están escritas e incluidas en la app. La IA no genera contenido, interpreta cifras, fotografías ni realiza diagnósticos. En Medicamentos reconoce la intención y busca principios activos documentados; en Observaciones recupera fichas educativas verificadas.")
        AiExplanationStep("03", "Tú decides qué explorar",
            if (reminderContext) "Las preguntas permanecen en memoria y no se usan para entrenar. Para advertir semejanzas, Recordatorios compara solo títulos activos. La ayuda no consulta datos clínicos ni fotografías, y solo tú confirmas qué guardar."
            else "Las preguntas y la búsqueda permanecen en memoria durante la sesión. No se guardan ni se usan para entrenar. El asistente no consulta registros personales ni fotografías.")
        if (reminderContext) ReminderOrganizationNote() else AiReviewNote()
    }
}

@Composable
internal fun AiReviewNote() {
    Text(
        "Educación general · Pendiente de revisión clínica. Las decisiones personales corresponden a tu equipo de salud.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AiAnalysis(motion: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (motion) CircularProgressIndicator(
            modifier = Modifier.size(16.dp).clearAndSetSemantics {},
            strokeWidth = 2.dp,
        ) else AiIcon(R.drawable.ic_bitavida_ai, Modifier.size(16.dp))
        Text(
            "Comprendiendo tu pregunta…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
