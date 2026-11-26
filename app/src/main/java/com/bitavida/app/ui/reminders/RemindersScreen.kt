package com.bitavida.app.ui.reminders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.usecase.CareReminderTemporalGroup
import com.bitavida.app.domain.usecase.ReminderDatePreset
import com.bitavida.app.domain.usecase.ReminderPostponePreset
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaDatePickerConstraints
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.measurements.*
import com.bitavida.app.ui.medical.CareSectionHeader
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Uses Plan's scaffold; a single Activity host presents the same sheets from Plan and Mi día. */
@Composable
fun RemindersRoute(
    viewModel: RemindersViewModel,
    patientName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHelp: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val back = {
        if (!state.saving) when {
            state.viewingSimilar -> viewModel.onAction(RemindersAction.ReturnToDraft)
            state.sheet != null -> viewModel.onAction(RemindersAction.DismissSheet)
            state.page == RemindersPage.PREVIOUS -> viewModel.onAction(RemindersAction.OpenMain)
            else -> onBack()
        }
    }
    BackHandler { back() }
    RemindersScreen(state, viewModel::onAction, back, patientName, modifier, onOpenHelp)
}

@Composable
fun RemindersScreen(
    state: RemindersUiState,
    onAction: (RemindersAction) -> Unit,
    onBack: () -> Unit,
    patientName: String,
    modifier: Modifier = Modifier,
    onOpenHelp: () -> Unit = {},
) {
    val mainScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()
    val motion = rememberBitavidaMotionAllowed(null)
    val duration = if (motion) BitavidaFluidMotion.StateTransition else 0
    AnimatedContent(
        targetState = state.page,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            if (motion) fadeIn(tween(duration)) togetherWith fadeOut(tween(duration / 2))
            else EnterTransition.None togetherWith ExitTransition.None
        },
        label = "reminders_navigation",
    ) { page ->
        val activePage = page == state.page
        val dispatch: (RemindersAction) -> Unit = { if (activePage) onAction(it) }
        LazyColumn(
            state = if (page == RemindersPage.MAIN) mainScroll else historyScroll,
            modifier = Modifier.fillMaxSize().testTag("reminders_" + page.name),
            contentPadding = PaddingValues(
                horizontal = BitavidaSpacing.ScreenHorizontal,
                vertical = BitavidaSpacing.Default,
            ),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            item("header") {
                CareSectionHeader(
                    title = if (page == RemindersPage.MAIN) "Recordatorios" else "Recordatorios anteriores",
                    person = patientName,
                    iconRes = R.drawable.ic_v2_clock,
                    onBack = { if (activePage) onBack() },
                    motionAllowed = motion,
                    subtitle = if (page == RemindersPage.MAIN) "Pendientes del cuidado cotidiano" else "Hechos y descartados",
                    backEnabled = !state.saving,
                    backLabel = if (page == RemindersPage.PREVIOUS) "Recordatorios" else "Plan de cuidado",
                    emphasizeSupportingText = page == RemindersPage.MAIN,
                )
            }
            if (state.loading) item("loading") { SecondaryText("Cargando recordatorios…") }
            state.error?.takeIf { state.sheet == null }?.let { error ->
                item("error") {
                    CaptureError(error)
                    if (state.loadFailed) MeasurementButton(
                        "Reintentar carga", { dispatch(RemindersAction.Reload) },
                        style = MeasurementButtonStyle.OUTLINED,
                    )
                }
            }
            if (!state.loading && !state.loadFailed) {
                if (page == RemindersPage.MAIN) {
                    if (state.active.isEmpty()) item("empty") {
                        MeasurementPanel {
                            SectionTitle("¿Algo que necesites recordar?", small = true)
                            SecondaryText("Organiza pendientes que te ayuden a cuidar, como comprar insumos, preparar documentos o coordinar ayuda")
                            MeasurementButton("Crear primer recordatorio", { dispatch(RemindersAction.Create) },
                                modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_v2_plus)
                        }
                    }
                    state.groups.forEachIndexed { groupIndex, group ->
                        item("group_" + group.kind.name) {
                            ReminderGroupHeader(
                                group.kind, group.records.size,
                                Modifier.padding(
                                    top = if (groupIndex == 0) BitavidaSpacing.ExtraSmall else BitavidaSpacing.Medium,
                                    bottom = BitavidaSpacing.ExtraSmall,
                                ),
                            )
                        }
                        items(group.records, key = { it.id }) { reminder ->
                            ReminderCard(
                                reminder, state.now, { dispatch(RemindersAction.Open(reminder.id)) },
                                Modifier.animateItem(
                                    fadeInSpec = tween(duration), placementSpec = tween(duration), fadeOutSpec = tween(duration)),
                                quickActions = {
                                    ReminderQuickActions(reminder, !state.saving, dispatch)
                                },
                                )
                        }
                    }
                    if (state.atLimit) item("limit") {
                        SecondaryText("Tienes 10 recordatorios activos. Marca uno como hecho o descártalo para crear otro.")
                    }
                    item("actions") {
                        Column(Modifier.padding(top = BitavidaSpacing.Default)) {
                        MeasurementGuideActions(
                            "Nuevo recordatorio",
                            { dispatch(RemindersAction.Create) },
                            { dispatch(RemindersAction.OpenPrevious) },
                            helpLabel = "Recordatorios anteriores",
                            thirdLabel = "Guías y ayuda",
                            onThird = { if (activePage) onOpenHelp() },
                        )
                        }
                    }
                } else {
                    if (state.previous.isEmpty()) item("empty_history") {
                        MeasurementPanel {
                            SectionTitle("Aún no hay recordatorios anteriores", small = true)
                            SecondaryText("Aquí podrás consultar los que marques como hechos o descartes.")
                        }
                    }
                    items(state.previous, key = { it.id }) { reminder ->
                        ReminderCard(reminder, state.now, { dispatch(RemindersAction.Open(reminder.id)) })
                    }
                }
            }
            item("bottom") { Spacer(Modifier.height(BitavidaSpacing.Small)) }
        }
    }
}

private fun CareReminderTemporalGroup.label(): String = when (this) {
    CareReminderTemporalGroup.TODAY -> "Hoy"
    CareReminderTemporalGroup.UPCOMING -> "Próximos"
    CareReminderTemporalGroup.PAST -> "Pendientes anteriores"
}

/** Informational chips follow the same surfaces and typography as the existing temporal labels. */
@Composable
private fun ReminderGroupHeader(kind: CareReminderTemporalGroup, count: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val today = kind == CareReminderTemporalGroup.TODAY
    val past = kind == CareReminderTemporalGroup.PAST
    val container = when {
        today -> colors.primaryContainer
        past -> colors.surfaceVariant
        else -> MaterialTheme.bitavidaColors.paleBlue
    }
    val foreground = when {
        today -> colors.primary
        else -> colors.onSurface
    }
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        color = container, contentColor = foreground, shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            Modifier.padding(horizontal = BitavidaSpacing.Medium, vertical = BitavidaSpacing.ExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeasurementIcon(
                when (kind) {
                    CareReminderTemporalGroup.TODAY -> R.drawable.ic_v2_clock
                    CareReminderTemporalGroup.UPCOMING -> R.drawable.ic_v2_calendar
                    CareReminderTemporalGroup.PAST -> R.drawable.ic_v2_history
                },
                16, foreground,
            )
            Text(kind.label(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
            Surface(
                color = colors.surface, contentColor = colors.onSurfaceVariant, shape = MaterialTheme.shapes.small,
            ) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = BitavidaSpacing.Small, vertical = BitavidaSpacing.ExtraSmall))
            }
        }
    }
}

@Composable
internal fun ReminderCard(
    reminder: CareReminder,
    now: LocalDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quickActions: (@Composable () -> Unit)? = null,
) {
    val active = reminder.status == CareReminderStatus.PENDING
    MeasurementPanel(
        modifier = modifier.testTag("reminder_" + reminder.id),
        onClick = onClick,
        borderAlpha = 0.5f,
        contentPadding = BitavidaSpacing.Medium,
        contentSpacing = BitavidaSpacing.ExtraSmall,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            MeasurementIcon(if (active) R.drawable.ic_v2_clock else R.drawable.ic_v2_history,
                16, MaterialTheme.colorScheme.onSurfaceVariant)
            Text(reminder.title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            MeasurementIcon(R.drawable.ic_v2_chevron, 14, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = BitavidaSpacing.Large),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (active) reminderCompactDateTimeLabel(reminder.scheduledAt, now)
                else "Programado: " + reminderCompactDateTimeLabel(reminder.originallyScheduledAt, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (!active) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = BitavidaSpacing.Large),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ReminderStatusLabel(reminder.status)
                reminder.closedAt?.let {
                    Text("Cierre: " + reminderCompactDateTimeLabel(it, now),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        quickActions?.invoke()
    }
}

@Composable
private fun ReminderQuickActions(reminder: CareReminder, enabled: Boolean, onAction: (RemindersAction) -> Unit) {
    val complete: @Composable (Modifier) -> Unit = { modifier ->
        CurrentDetailButton(
            kind = DetailButtonKind.FILLED,
            enabled = enabled,
            modifier = modifier.testTag("reminder_complete_" + reminder.id)
                .semantics { contentDescription = "Marcar como hecho: " + reminder.title },
            onClick = { onAction(RemindersAction.QuickComplete(reminder.id)) },
        ) { Text("Completar") }
    }
    val postpone: @Composable (Modifier) -> Unit = { modifier ->
        CurrentDetailButton(
            kind = DetailButtonKind.OUTLINED,
            strongOutline = true,
            enabled = enabled,
            modifier = modifier.testTag("reminder_postpone_" + reminder.id)
                .semantics { contentDescription = "Posponer: " + reminder.title },
            onClick = { onAction(RemindersAction.QuickPostpone(reminder.id)) },
        ) { Text("Posponer") }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = BitavidaSpacing.Small)) {
        if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 256.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                complete(Modifier.fillMaxWidth())
                postpone(Modifier.fillMaxWidth())
            }
        } else Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            complete(Modifier.weight(1f).fillMaxHeight())
            postpone(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun reminderCompactDateTimeLabel(value: LocalDateTime, now: LocalDateTime): String =
    value.format(DateTimeFormatter.ofPattern(
        if (value.year == now.year) "d MMM" else "d MMM uuuu", LocalLocale.current.platformLocale)) +
        " · " + reminderTimeLabel(value)

fun CareReminderStatus.label(): String = when (this) {
    CareReminderStatus.PENDING -> "Pendiente"
    CareReminderStatus.DONE -> "Hecho"
    CareReminderStatus.DISCARDED -> "Descartado"
}

@Composable
fun reminderTimeLabel(value: LocalDateTime): String {
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    return value.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", LocalLocale.current.platformLocale))
}

@Composable
fun reminderDateTimeLabel(value: LocalDateTime): String =
    value.format(DateTimeFormatter.ofPattern("d MMM uuuu", LocalLocale.current.platformLocale)) + " · " + reminderTimeLabel(value)
