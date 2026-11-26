package com.bitavida.app.ui.reminders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.usecase.ReminderDatePreset
import com.bitavida.app.domain.usecase.ReminderPostponePreset
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaDatePickerConstraints
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.measurements.*
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One approved modal surface; detail, ideas and pickers replace content without nesting sheets. */
@Composable
fun ReminderSheets(state: RemindersUiState, onAction: (RemindersAction) -> Unit) {
    val sheet = state.sheet ?: return
    val motion = rememberBitavidaMotionAllowed(null)
    val title = when (sheet) {
        ReminderSheet.FORM -> if (state.form?.editingRevision != null) "Editar recordatorio" else "Nuevo recordatorio"
        ReminderSheet.POSTPONE -> "Posponer"
        ReminderSheet.DISCARD -> "¿Descartar recordatorio?"
        ReminderSheet.DETAIL -> state.selected?.title ?: "Recordatorio"
        ReminderSheet.SUGGESTIONS -> "Ideas para tu recordatorio"
    }
    val scheme = MaterialTheme.colorScheme
    // Keep every fallback surface local to Recordatorios within the approved neutral/turquoise family.
    MaterialTheme(colorScheme = scheme.copy(
        outlineVariant = scheme.outline,
        surfaceTint = scheme.primary,
        secondaryContainer = scheme.surfaceVariant,
        onSecondaryContainer = scheme.onSurfaceVariant,
        surfaceContainerLowest = scheme.surface,
        surfaceContainerLow = scheme.surface,
        surfaceContainer = scheme.surfaceVariant,
        surfaceContainerHigh = scheme.surfaceVariant,
        surfaceContainerHighest = scheme.surfaceVariant,
    )) {
        MedicationLifecycleSheetFrame(
            medication = null, title = title, saving = state.saving, motionAllowed = motion,
            contentHorizontalPadding = BitavidaSpacing.Large,
            onDismiss = { onAction(if (state.viewingSimilar) RemindersAction.ReturnToDraft else RemindersAction.DismissSheet) },
        ) {
            AnimatedContent(
                targetState = sheet,
                transitionSpec = {
                    if (motion) fadeIn(tween(BitavidaFluidMotion.Selection)) togetherWith fadeOut(tween(BitavidaFluidMotion.Selection / 2))
                    else EnterTransition.None togetherWith ExitTransition.None
                },
                label = "reminder_sheet_content",
            ) { rendered ->
                val dispatch: (RemindersAction) -> Unit = { if (rendered == state.sheet) onAction(it) }
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    when (rendered) {
                        ReminderSheet.FORM -> state.form?.let { ReminderFormContent(it, state, dispatch, motion) }
                        ReminderSheet.POSTPONE -> state.form?.let { ReminderPostponeContent(it, state, dispatch, motion) }
                        ReminderSheet.DISCARD -> {
                            state.selected?.let { ReminderIdentity(it) }
                            SecondaryText("Se conservará en Recordatorios anteriores.")
                            state.error?.let { CaptureError(it) }
                            MedicationLifecyclePrimaryAction(
                                "Descartar recordatorio", R.drawable.ic_v2_close, enabled = !state.saving,
                                saving = state.saving, destructive = true, motionAllowed = motion,
                                onClick = { dispatch(RemindersAction.ConfirmDiscard) },
                            )
                            MedicationLifecycleCancelAction(!state.saving) { dispatch(RemindersAction.BackToDetail) }
                        }
                        ReminderSheet.DETAIL -> state.selected?.let {
                            ReminderDetailContent(it, state, dispatch, motion, showIdentity = false)
                        }
                        ReminderSheet.SUGGESTIONS -> ReminderIdeasContent(state, dispatch, motion)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReminderFormContent(
    form: ReminderForm,
    state: RemindersUiState,
    onAction: (RemindersAction) -> Unit,
    motion: Boolean,
) {
    var titleHadFocus by remember(form.id) { mutableStateOf(false) }
    val duration = if (motion) BitavidaFluidMotion.Selection else 0
    Column(
        Modifier.fillMaxWidth().animateContentSize(tween(duration)),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
    ) {
        BitavidaLimitedTextField(
            value = form.title,
            onValueChange = { if (!state.saving) onAction(RemindersAction.ChangeForm(form.copy(title = it))) },
            limit = CaptureLimit.REMINDER_TITLE,
            label = { Text("¿Qué necesitas recordar?") },
            minLines = 1, maxLines = 3, shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("reminder_title").onFocusChanged { focus ->
                if (focus.isFocused) titleHadFocus = true
                else if (titleHadFocus) {
                    titleHadFocus = false
                    onAction(RemindersAction.TitleEditingFinished)
                }
            },
        )
        if (form.title.isNotBlank() && form.titleAssistanceEnabled) ReminderInlineAssistance(state, onAction, motion)
        ReminderScheduleFields(form, state.saving, onAction, form.editingRevision == null, motion)

        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                    .toggleable(form.detailsExpanded, enabled = !state.saving, role = Role.Button,
                        onValueChange = { onAction(RemindersAction.ChangeForm(form.copy(detailsExpanded = it))) })
                    .semantics { stateDescription = if (form.detailsExpanded) "Expandido" else "Contraído" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                MeasurementIcon(R.drawable.ic_v2_file, 18, MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Detalles adicionales", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                MeasurementIcon(if (form.detailsExpanded) R.drawable.ic_v2_close else R.drawable.ic_v2_plus, 16,
                    MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(
                form.detailsExpanded,
                enter = if (motion) fadeIn(tween(duration)) else EnterTransition.None,
                exit = if (motion) fadeOut(tween(duration)) else ExitTransition.None,
            ) {
                BitavidaLimitedTextField(
                    value = form.details,
                    onValueChange = { if (!state.saving) onAction(RemindersAction.ChangeForm(form.copy(details = it))) },
                    limit = CaptureLimit.REMINDER_DETAILS,
                    label = { Text("Detalles (opcional)") }, minLines = 2, maxLines = 4,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        state.similarReminders.firstOrNull()?.let { similar ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Ya tienes un recordatorio parecido.", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(similar.title, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        onClick = { onAction(RemindersAction.ViewSimilar(similar.id)) }, enabled = !state.saving,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("reminder_view_similar"),
                    ) { Text("Consultarlo") }
                }
            }
        }
        state.error?.let { CaptureError(it) }
        ReminderPrimary(
            if (state.similarReminders.isEmpty()) "Guardar recordatorio" else "Crear otro de todas maneras",
            R.drawable.ic_v2_file, state, motion,
        ) { onAction(if (state.similarReminders.isEmpty()) RemindersAction.Save else RemindersAction.SaveAnyway) }
    }
}

@Composable
private fun ReminderInlineAssistance(
    state: RemindersUiState,
    onAction: (RemindersAction) -> Unit,
    motion: Boolean,
) {
    val assistance = state.assistance
    val suggestion = assistance?.suggestedTitle
    val clarifications = assistance?.clarifications.orEmpty()
    val duration = if (motion) BitavidaFluidMotion.Selection else 0
    AnimatedContent(
        targetState = when {
            !suggestion.isNullOrBlank() -> "suggestion"
            clarifications.isNotEmpty() -> "clarify"
            else -> "action"
        },
        transitionSpec = {
            if (motion) fadeIn(tween(duration)) togetherWith fadeOut(tween(duration / 2))
            else EnterTransition.None togetherWith ExitTransition.None
        }, label = "reminder_title_assistance",
    ) { mode ->
        when (mode) {
            "suggestion" -> Surface(
                color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Sugerencia: " + suggestion.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                        TextButton(
                            { onAction(RemindersAction.UseSuggestedTitle) }, enabled = !state.saving,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("reminder_use_title"),
                        ) { Text("Usar sugerencia") }
                        TextButton(
                            { onAction(RemindersAction.IgnoreTitleSuggestion) }, enabled = !state.saving,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("reminder_ignore_title"),
                        ) { Text("Ignorar") }
                    }
                }
            }
            "clarify" -> Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                Text("¿Quieres preparar, recoger u organizar documentos?", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    clarifications.take(3).forEach { title ->
                        GlucoseCaptureChip(
                            title.substringBefore(' '), false, !state.saving, motion,
                            { onAction(RemindersAction.UseIdea(title)) },
                        )
                    }
                }
            }
            else -> Column {
                TextButton(
                    onClick = { onAction(RemindersAction.HelpOrganize) },
                    enabled = !state.saving && !state.assistanceLoading,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("reminder_context_help"),
                ) {
                    MeasurementIcon(R.drawable.ic_bitavida_ai, 18, MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(BitavidaSpacing.Small))
                    Text("Revisar título", style = MaterialTheme.typography.labelMedium)
                }
                state.assistanceMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReminderScheduleFields(
    form: ReminderForm,
    saving: Boolean,
    onAction: (RemindersAction) -> Unit,
    futureOnly: Boolean,
    motion: Boolean,
    showPresets: Boolean = true,
) {
    val picker = LocalBitavidaDateTimePickerController.current
    val locale = LocalLocale.current.platformLocale
    val fontScale = LocalDensity.current.fontScale
    val showDate: () -> Unit = {
        picker.showDate(form.scheduledAt.toLocalDate(),
            BitavidaDatePickerConstraints(minDate = if (futureOnly) java.time.LocalDate.now() else null)) {
            onAction(RemindersAction.ChangeForm(form.copy(scheduledAt = LocalDateTime.of(it, form.scheduledAt.toLocalTime()))))
        }
    }
    val date: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
            Text("Fecha", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReminderSecondaryButton(
                form.scheduledAt.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM uuuu", locale)),
                showDate, Modifier.fillMaxWidth(), enabled = !saving, icon = R.drawable.ic_v2_calendar,
                accessibleLabel = "Cambiar fecha del recordatorio",
            )
        }
    }
    val time: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
            Text("Hora", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReminderSecondaryButton(
                reminderTimeLabel(form.scheduledAt),
                { picker.showTime(form.scheduledAt.toLocalTime()) {
                    onAction(RemindersAction.ChangeForm(form.copy(scheduledAt = LocalDateTime.of(form.scheduledAt.toLocalDate(), it))))
                } }, Modifier.fillMaxWidth(), enabled = !saving, icon = R.drawable.ic_v2_clock,
                accessibleLabel = "Cambiar hora del recordatorio",
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 320.dp && fontScale < 1.5f) {
                Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    date(Modifier.weight(1f)); time(Modifier.weight(1f))
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                date(Modifier.fillMaxWidth()); time(Modifier.fillMaxWidth())
            }
        }
        if (showPresets) {
            val today = java.time.LocalDate.now()
            val choice: @Composable (String, ReminderDatePreset, Boolean, Modifier) -> Unit = { label, preset, selected, modifier ->
                CurrentDetailButton(
                    kind = DetailButtonKind.OUTLINED,
                    onClick = { onAction(RemindersAction.SetDatePreset(preset)) },
                    enabled = !saving, strongOutline = true, motionAllowedOverride = motion,
                    modifier = modifier.semantics { this.selected = selected },
                ) { Text(if (selected) "✓ $label" else label) }
            }
            val todayChoice: @Composable (Modifier) -> Unit = { modifier ->
                choice("Hoy", ReminderDatePreset.TODAY, form.scheduledAt.toLocalDate() == today, modifier)
            }
            val tomorrowChoice: @Composable (Modifier) -> Unit = { modifier ->
                choice("Mañana", ReminderDatePreset.TOMORROW, form.scheduledAt.toLocalDate() == today.plusDays(1), modifier)
            }
            if (fontScale >= 1.5f) {
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    todayChoice(Modifier.fillMaxWidth())
                    tomorrowChoice(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    todayChoice(Modifier.weight(1f).fillMaxHeight())
                    tomorrowChoice(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
internal fun ReminderPostponeContent(
    form: ReminderForm,
    state: RemindersUiState,
    onAction: (RemindersAction) -> Unit,
    motion: Boolean,
) {
    var custom by remember(form.id) { mutableStateOf(false) }
    val picker = LocalBitavidaDateTimePickerController.current
    val fontScale = LocalDensity.current.fontScale
    state.selected?.let {
        Text(it.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val tile: @Composable (String, Int, Modifier, () -> Unit) -> Unit = { label, icon, modifier, action ->
        CurrentDetailButton(
            kind = DetailButtonKind.OUTLINED, onClick = action,
            modifier = modifier.heightIn(min = 72.dp), enabled = !state.saving,
            strongOutline = true, motionAllowedOverride = motion,
            contentPadding = PaddingValues(horizontal = BitavidaSpacing.Small, vertical = BitavidaSpacing.Medium),
        ) {
            if (fontScale >= 1.5f) {
                Column(
                    Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    MeasurementIcon(icon, 18, LocalContentColor.current)
                    Text(label, textAlign = TextAlign.Center)
                }
            } else {
                MeasurementIcon(icon, 18, LocalContentColor.current)
                Spacer(Modifier.width(BitavidaSpacing.Small))
                Text(label, Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
    }
    val fifteenMinutes: @Composable (Modifier) -> Unit = { modifier ->
        tile("En 15 minutos", R.drawable.ic_v2_clock, modifier.testTag("postpone_FIFTEEN_MINUTES")) {
            onAction(RemindersAction.ApplyPostponePreset(ReminderPostponePreset.FIFTEEN_MINUTES))
        }
    }
    val oneHour: @Composable (Modifier) -> Unit = { modifier ->
        tile("En una hora", R.drawable.ic_v2_clock, modifier.testTag("postpone_ONE_HOUR")) {
            onAction(RemindersAction.ApplyPostponePreset(ReminderPostponePreset.ONE_HOUR))
        }
    }
    val tomorrow: @Composable (Modifier) -> Unit = { modifier ->
        tile("Mañana", R.drawable.ic_v2_calendar, modifier.testTag("postpone_TOMORROW")) {
            onAction(RemindersAction.ApplyPostponePreset(ReminderPostponePreset.TOMORROW))
        }
    }
    val chooseDate: @Composable (Modifier) -> Unit = { modifier ->
        tile("Elegir fecha y hora", R.drawable.ic_v2_calendar, modifier.testTag("postpone_custom")) {
            custom = true
            picker.showDate(form.scheduledAt.toLocalDate(), BitavidaDatePickerConstraints(minDate = state.now.toLocalDate())) {
                onAction(RemindersAction.ChangeForm(form.copy(scheduledAt = LocalDateTime.of(it, form.scheduledAt.toLocalTime()))))
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        Column(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            ) {
                fifteenMinutes(Modifier.weight(1f).fillMaxHeight())
                oneHour(Modifier.weight(1f).fillMaxHeight())
            }
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            ) {
                tomorrow(Modifier.weight(1f).fillMaxHeight())
                chooseDate(Modifier.weight(1f).fillMaxHeight())
            }
        }
        AnimatedVisibility(custom) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
                ReminderScheduleFields(form, state.saving, onAction, true, motion, showPresets = false)
                ReminderPrimary("Guardar nueva fecha", R.drawable.ic_v2_calendar, state, motion) {
                    onAction(RemindersAction.SavePostponement)
                }
            }
        }
        state.error?.let { CaptureError(it) }
    }
}

@Composable
internal fun ReminderIdeasContent(
    state: RemindersUiState,
    onAction: (RemindersAction) -> Unit,
    motion: Boolean,
) {
    val category = state.ideaCategory
    var visibleCount by remember(category, state.ideaSearch) { mutableIntStateOf(4) }
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        TextButton(
            { onAction(RemindersAction.DismissSheet) }, modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            MeasurementIcon(R.drawable.ic_v2_arrow_back, 18, MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(BitavidaSpacing.Small)); Text("Volver al recordatorio")
        }
        BitavidaLimitedTextField(
            state.ideaSearch, { onAction(RemindersAction.SetIdeaSearch(it)) }, CaptureLimit.SEARCH,
            label = { Text("Buscar una idea") }, placeholder = { Text("Compras, documentos, ayuda…") },
            maxLines = 2, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().testTag("reminder_idea_search"),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            state.ideaCatalog.forEach { group ->
                GlucoseCaptureChip(
                    group.category.label, group.category == category, !state.saving, motion,
                    { onAction(RemindersAction.SelectIdeaCategory(group.category)) },
                )
            }
        }
        val rows = state.ideaRows
        if (rows.isEmpty()) {
            SecondaryText("Describe una acción concreta, por ejemplo: Comprar pilas.")
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                Column {
                    rows.take(visibleCount).forEachIndexed { index, idea ->
                        if (index > 0) HorizontalDivider(
                            Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        ReminderIdeaRow(idea.title, !state.saving) { onAction(RemindersAction.UseIdea(idea.title)) }
                    }
                }
            }
            if (rows.size > visibleCount) TextButton(
                { visibleCount += 4 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Ver más ideas") }
        }
    }
}

@Composable
private fun ReminderIdeaRow(title: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(BitavidaSpacing.Small))
        MeasurementIcon(R.drawable.ic_v2_plus, 18, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReminderIdentity(reminder: CareReminder) {
    Text(reminder.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun ReminderDetailContent(
    reminder: CareReminder,
    state: RemindersUiState,
    onAction: (RemindersAction) -> Unit,
    motion: Boolean,
    showIdentity: Boolean = true,
) {
    val pending = reminder.status == CareReminderStatus.PENDING
    Column(
        modifier = Modifier.fillMaxWidth().testTag("reminder_detail_content"),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        if (showIdentity) {
            Text(reminder.title, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ReminderStatusLabel(reminder.status)
                if (reminder.hasPastSchedule(state.now)) {
                    Text("Horario pasado", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                Text(if (pending) "Fecha y hora" else "Programado originalmente",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    reminderDateTimeLabel(if (pending) reminder.scheduledAt else reminder.originallyScheduledAt),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
            }
            reminder.details?.takeIf(String::isNotBlank)?.let { details ->
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                    Text("Detalles adicionales", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(details, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            reminder.closedAt?.let { closedAt ->
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                    Text("Fecha de cierre", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(reminderDateTimeLabel(closedAt), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.error?.let { CaptureError(it) }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = BitavidaSpacing.ExtraSmall),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        )
        if (state.viewingSimilar) {
            ReminderSecondaryButton(
                "Volver al nuevo recordatorio", { onAction(RemindersAction.ReturnToDraft) },
                Modifier.fillMaxWidth(), !state.saving, R.drawable.ic_v2_arrow_back,
            )
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            if (pending) {
                CurrentDetailButton(
                    kind = DetailButtonKind.FILLED, onClick = { onAction(RemindersAction.Edit) },
                    modifier = Modifier.fillMaxWidth(), enabled = !state.saving, motionAllowedOverride = motion,
                ) {
                    MeasurementIcon(R.drawable.ic_v2_edit, 18, LocalContentColor.current)
                    Spacer(Modifier.width(BitavidaSpacing.Small))
                    Text("Editar")
                }
            }
            ReminderSecondaryButton(
                "Crear otro igual", { onAction(RemindersAction.CreateAnother) },
                Modifier.fillMaxWidth(), !state.saving && !state.atLimit, R.drawable.ic_v2_plus,
            )
            if (state.atLimit) {
                Text("Tienes 10 activos. Completa o descarta uno para crear otro.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (pending) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ReminderSecondaryButton(
                "Descartar recordatorio", { onAction(RemindersAction.RequestDiscard) },
                Modifier.fillMaxWidth(), !state.saving, R.drawable.ic_v2_close, destructive = true,
            )
        }
    }
}

/** Discreet status treatment shared with the compact reminder history. */
@Composable
internal fun ReminderStatusLabel(status: CareReminderStatus, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            status.label(),
            modifier = Modifier.padding(horizontal = BitavidaSpacing.Small, vertical = BitavidaSpacing.ExtraSmall),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ReminderSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int? = null,
    accessibleLabel: String = label,
    destructive: Boolean = false,
) {
    CurrentDetailButton(
        kind = DetailButtonKind.OUTLINED, onClick = onClick, enabled = enabled,
        strongOutline = true, destructive = destructive,
        modifier = modifier.semantics { contentDescription = accessibleLabel },
        contentPadding = PaddingValues(horizontal = BitavidaSpacing.Medium, vertical = BitavidaSpacing.Small),
    ) {
        icon?.let {
            MeasurementIcon(it, 18, LocalContentColor.current)
            Spacer(Modifier.width(BitavidaSpacing.Small))
        }
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReminderPrimary(label: String, icon: Int, state: RemindersUiState, motion: Boolean, onClick: () -> Unit) {
    MedicationLifecyclePrimaryAction(label, icon, !state.saving, state.saving, false, motion, onClick)
}
