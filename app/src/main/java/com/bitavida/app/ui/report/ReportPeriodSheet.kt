package com.bitavida.app.ui.report

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.usecase.ConsultationIntervalKind
import com.bitavida.app.domain.usecase.ConsultationPeriodChoices
import com.bitavida.app.domain.usecase.FollowUpPeriod
import com.bitavida.app.domain.usecase.monthlyFollowUpPeriod
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaDatePicker
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportPeriodSheet(
    appliedMode: String,
    appliedPeriod: FollowUpPeriod,
    choices: ConsultationPeriodChoices,
    appliedIntervalIndex: Int,
    onDismiss: () -> Unit,
    onApply: (mode: String, period: FollowUpPeriod, intervalIndex: Int) -> Unit,
) {
    val motion = rememberBitavidaMotionAllowed(null)
    val locale = remember { Locale.forLanguageTag("es-MX") }
    val shortDate = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
    var mode by remember { mutableStateOf(appliedMode.takeIf { it in reportModes } ?: "month") }
    var calendarField by remember { mutableStateOf<String?>(null) }
    var draftStart by remember { mutableStateOf(appliedPeriod.start) }
    var draftEnd by remember { mutableStateOf(appliedPeriod.end) }
    var calendarDraft by remember { mutableStateOf(appliedPeriod.start) }
    var monthDraft by remember { mutableStateOf(YearMonth.from(appliedPeriod.start)) }
    var showOtherPeriods by remember { mutableStateOf(false) }
    var intervalIndex by remember {
        mutableIntStateOf(if (appliedMode == "consultations")
            appliedIntervalIndex.coerceIn(0, choices.intervals.lastIndex.coerceAtLeast(0)) else choices.selectedIndex)
    }
    val selectedInterval = choices.intervals.getOrNull(intervalIndex)
    val visible = remember(choices) { com.bitavida.app.domain.usecase.visibleConsultationPeriodIndices(choices) }
    val remaining = remember(choices, visible) { choices.intervals.indices.filter { it !in visible }.reversed() }
    androidx.activity.compose.BackHandler(enabled = calendarField != null) { calendarField = null }
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = screenHeight * 0.82f)
                .animateContentSize(animationSpec = if (motion) tween(200) else snap()),
        ) {
            Text("Elegir periodo", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge)
        AnimatedContent(
            targetState = calendarField,
            transitionSpec = {
                if (motion) {
                    val forward = targetState != null
                    (androidx.compose.animation.slideInHorizontally(tween(200)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(180)))
                        .togetherWith(androidx.compose.animation.slideOutHorizontally(tween(180)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(120)))
                } else EnterTransition.None togetherWith ExitTransition.None
            }, label = "Editor de periodo",
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        ) { field ->
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (field != null) {
                    key(field, draftStart, draftEnd) {
                        BitavidaDatePicker(
                            selectedDate = if (field == "start") draftStart else draftEnd,
                            onConfirm = {}, showConfirmAction = false,
                            onDateSelected = { calendarDraft = it },
                            onBack = { calendarField = null }, locale = locale,
                            rangePreviewOtherDate = if (field == "start") draftEnd else draftStart,
                            rangePreviewIsStart = field == "start",
                        )
                    }
                } else {
                    PeriodModeOption("Por mes", "Consulta los registros de un mes completo.", R.drawable.ic_v2_calendar,
                        mode == "month") { mode = "month" }
                    PeriodModeOption("Entre consultas", "Reúne la información entre consultas médicas.", R.drawable.ic_v2_stethoscope,
                        mode == "consultations") { mode = "consultations" }
                    PeriodModeOption("Personalizado", "Selecciona las fechas que necesites.", R.drawable.ic_v2_edit,
                        mode == "custom") { mode = "custom" }
                    AnimatedContent(targetState = mode,
                        transitionSpec = {
                            if (motion) fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                            else EnterTransition.None togetherWith ExitTransition.None
                        }, label = "Modalidad de periodo") { active ->
                        when (active) {
                            "month" -> key(appliedPeriod.start, appliedPeriod.end, "report-month") {
                                BitavidaDatePicker(selectedDate = monthDraft.atDay(1),
                                    onConfirm = {}, onBack = onDismiss, locale = locale, initialMonthYearVisible = true,
                                    monthSelectionOnly = true, showHeader = false, showConfirmAction = false,
                                    onDisplayedMonthChange = { monthDraft = it })
                            }
                            "consultations" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val displayed = if (showOtherPeriods) visible + remaining else visible
                                val intervalContent: @Composable (Int, Int) -> Unit = { position, index ->
                                        val item = choices.intervals[index]
                                        val inProgress = item.kind == ConsultationIntervalKind.IN_PROGRESS ||
                                            item.kind == ConsultationIntervalKind.FIRST_CONFIRMED
                                        val label = when {
                                            item.kind == ConsultationIntervalKind.NO_APPOINTMENTS -> "Desde el inicio del seguimiento"
                                            item.kind == ConsultationIntervalKind.CURRENT_MONTH -> "Mes actual"
                                            inProgress -> "Seguimiento en curso"
                                            position == 0 || (position == 1 && displayed.firstOrNull()?.let {
                                                choices.intervals[it].nextConsultationDate != null } == true) -> "Seguimiento anterior"
                                            else -> "Seguimiento previo"
                                        }
                                        Surface(onClick = { intervalIndex = index },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                                .semantics { selected = index == intervalIndex; role = Role.RadioButton },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (index == intervalIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            border = BorderStroke(1.dp, if (index == intervalIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(label, style = MaterialTheme.typography.titleSmall)
                                                    Text("${item.period.start.format(shortDate)} – ${item.period.end.format(shortDate)}",
                                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    val status = if (inProgress) {
                                                        if (item.nextConsultationDate != null) "En curso · Próxima consulta confirmada" else "En curso"
                                                    } else if (item.kind == ConsultationIntervalKind.FIRST_COMPLETED ||
                                                        item.kind == ConsultationIntervalKind.BETWEEN_COMPLETED) "Completado" else null
                                                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                                }
                                                RadioButton(selected = index == intervalIndex, onClick = null,
                                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary,
                                                        unselectedColor = MaterialTheme.colorScheme.outline))
                                            }
                                        }
                                }
                                if (showOtherPeriods) {
                                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(displayed, key = { _, index -> index }) { position, index ->
                                            intervalContent(position, index)
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        displayed.forEachIndexed { position, index -> intervalContent(position, index) }
                                    }
                                }
                                if (remaining.isNotEmpty()) OutlinedButton(onClick = { showOtherPeriods = !showOtherPeriods },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                                    Text(if (showOtherPeriods) "Ocultar otros periodos" else "Ver otros periodos")
                                }
                            }
                            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Selecciona las fechas", style = MaterialTheme.typography.titleMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DateField("Fecha inicial", draftStart.format(shortDate), Modifier.weight(1f)) { calendarDraft = draftStart; calendarField = "start" }
                                    DateField("Fecha final", draftEnd.format(shortDate), Modifier.weight(1f)) { calendarDraft = draftEnd; calendarField = "end" }
                                }
                                val valid = !draftEnd.isBefore(draftStart)
                                if (valid) Text("${draftStart.format(shortDate)} – ${draftEnd.format(shortDate)}",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else Text("La fecha final no puede ser anterior a la inicial.",
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(onClick = {
                val field = calendarField
                when {
                    field != null -> {
                        if (field == "start") draftStart = calendarDraft else draftEnd = calendarDraft
                        calendarField = null
                    }
                    mode == "month" -> { onApply("month", monthlyFollowUpPeriod(monthDraft), intervalIndex); onDismiss() }
                    mode == "consultations" -> selectedInterval?.let { onApply("consultations", it.period, intervalIndex); onDismiss() }
                    else -> { onApply("custom", FollowUpPeriod(draftStart, draftEnd), intervalIndex); onDismiss() }
                }
            }, enabled = calendarField != null || when (mode) {
                "consultations" -> selectedInterval != null
                "custom" -> !draftEnd.isBefore(draftStart)
                else -> true
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Text(if (calendarField != null) "Usar esta fecha" else if (mode == "month") "Usar este mes" else "Usar este periodo")
            }
            }
        }
    }
}

private val reportModes = setOf("month", "consultations", "custom")

@Composable
private fun PeriodModeOption(title: String, description: String, @DrawableRes icon: Int, isSelected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .semantics { selected = isSelected; role = Role.RadioButton },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = isSelected, onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline))
        }
    }
}

@Composable
private fun DateField(label: String, date: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
