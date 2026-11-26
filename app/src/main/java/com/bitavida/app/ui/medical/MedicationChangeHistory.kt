package com.bitavida.app.ui.medical

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationHourlyIntervalMode
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationReminderPreference
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class MedicationFieldChange(
    val field: String,
    val before: String?,
    val after: String?,
)

private data class InitialHistoryGroup(
    val title: String,
    val values: List<String>,
)

private val historyLocale = Locale.forLanguageTag("es-MX")
private val historyDayFormatter = DateTimeFormatter.ofPattern("d MMM uuuu", historyLocale)
private val historyTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", historyLocale)
private val historyMomentFormatter = DateTimeFormatter.ofPattern("d MMM uuuu · HH:mm", historyLocale)
private val historyDateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", historyLocale)

internal fun medicationFieldChanges(
    previous: MedicationPlanRevision?,
    current: MedicationPlanRevision,
): List<MedicationFieldChange> {
    if (previous == null) {
        return current.historyFields().map { MedicationFieldChange(it.key, null, it.value) }
    }
    val before = previous.historyFields()
    val after = current.historyFields()
    val scalar = (before.keys + after.keys).distinct().mapNotNull { field ->
        val old = before[field]
        val new = after[field]
        if (old == new) null else MedicationFieldChange(field, old, new)
    }
    return scalar + administrationChanges(previous, current)
}

internal data class MedicationHistoryEntry(
    val revision: MedicationPlanRevision,
    val previous: MedicationPlanRevision?,
    val periodNumber: Int,
)

internal fun medicationHistoryEntries(
    plans: List<MedicationPlan>,
): List<MedicationHistoryEntry> {
    val orderedPeriods = plans.sortedWith(
        compareBy<MedicationPlan> {
            it.currentRevision.configuration?.startsOn
                ?: it.currentRevision.startsOn
        }.thenBy(MedicationPlan::createdAtEpochMillis),
    )
    return orderedPeriods.flatMapIndexed { periodIndex, plan ->
        plan.revisions.mapIndexed { revisionIndex, revision ->
            MedicationHistoryEntry(
                revision = revision,
                previous = plan.revisions.getOrNull(revisionIndex - 1),
                periodNumber = periodIndex + 1,
            )
        }
    }.sortedByDescending { it.revision.capturedAtEpochMillis }
}

@Composable
internal fun MedicationChangesHistory(
    plans: List<MedicationPlan>,
    motionAllowed: Boolean,
) {
    val grouped = medicationHistoryEntries(plans)
        .groupBy { it.revision.capturedMoment().toLocalDate() }
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    Text(
        "Historial de cambios",
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
    grouped.forEach { (day, entries) ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                day.format(historyDayFormatter),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            entries.forEachIndexed { index, entry ->
                MedicationHistoryEvent(
                    revision = entry.revision,
                    previous = entry.previous,
                    periodNumber = entry.periodNumber,
                    isLastOfDay = index == entries.lastIndex,
                    duration = duration,
                )
            }
        }
    }
}

@Composable
internal fun MedicationChangesHistory(
    plan: MedicationPlan,
    motionAllowed: Boolean,
) {
    MedicationChangesHistory(listOf(plan), motionAllowed)
}

@Composable
private fun MedicationHistoryEvent(
    revision: MedicationPlanRevision,
    previous: MedicationPlanRevision?,
    periodNumber: Int,
    isLastOfDay: Boolean,
    duration: Int,
) {
    val initial = previous == null
    val changes = if (initial) emptyList() else medicationFieldChanges(previous, revision)
    var expanded by rememberSaveable(revision.id) {
        mutableStateOf(!initial && changes.size <= 1)
    }
    val threadColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier.fillMaxWidth().drawBehind {
            if (!isLastOfDay) {
                drawLine(
                    color = threadColor,
                    start = androidx.compose.ui.geometry.Offset(18.dp.toPx(), 30.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(18.dp.toPx(), size.height),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        },
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.Top,
    ) {
        HistoryMarker(revision.changeKind.historyIcon())
        Column(
            modifier = Modifier.weight(1f).padding(
                bottom = if (isLastOfDay) BitavidaSpacing.Default else BitavidaSpacing.Large,
            ),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                Text(
                    revision.capturedMoment().format(historyTimeFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HistoryKindLabel(
                    if (initial && periodNumber > 1) {
                        "Inicio de nuevo periodo"
                    } else {
                        revision.changeKind.historyPurpose()
                    },
                )
            }
            if (initial && periodNumber > 1) {
                Text(
                    "Periodo $periodNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (revision.changeKind == MedicationPlanChangeKind.SCHEDULE_CORRECTED) {
                Text(
                    "Registrado el · " + revision.capturedMoment().format(historyMomentFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Vigente desde · " + revision.startsOn.format(historyDateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (
                revision.changeKind in setOf(
                    MedicationPlanChangeKind.SUSPENDED,
                    MedicationPlanChangeKind.REACTIVATED,
                    MedicationPlanChangeKind.REACTIVATION_RESCHEDULED,
                    MedicationPlanChangeKind.REACTIVATION_CANCELLED,
                    MedicationPlanChangeKind.COMPLETED,
                    MedicationPlanChangeKind.REMOVED,
                ) &&
                revision.lifecycleEffectiveAtEpochMillis != null
            ) {
                Text(
                    "Registrado el · " +
                        revision.capturedMoment().format(
                            historyMomentFormatter,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    revision.changeKind.effectiveLabel() + " · " +
                        revision.lifecycleEffectiveMoment()
                            .format(historyMomentFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (initial) {
                InitialRevision(revision, expanded, duration) { expanded = !expanded }
            } else {
                RevisionChanges(changes, expanded, duration) { expanded = !expanded }
            }
            revision.changeReason?.takeIf(String::isNotBlank)?.let {
                Text(
                    "Motivo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun HistoryMarker(icon: Int) {
    Box(
        modifier = Modifier.width(36.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
internal fun HistoryKindLabel(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Small,
                vertical = BitavidaSpacing.ExtraSmall,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InitialRevision(
    revision: MedicationPlanRevision,
    expanded: Boolean,
    duration: Int,
    onToggle: () -> Unit,
) {
    Text(
        revision.initialSummary(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HistoryToggle(
        if (expanded) "Ocultar configuración inicial" else "Ver configuración inicial",
        expanded,
        onToggle,
    )
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            revision.initialGroups().forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    group.values.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RevisionChanges(
    changes: List<MedicationFieldChange>,
    expanded: Boolean,
    duration: Int,
    onToggle: () -> Unit,
) {
    if (changes.isEmpty()) {
        Text(
            "Esta revisión conserva los mismos valores comparables.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (changes.size > 1) {
        HistoryToggle(changes.size.toString() + " cambios", expanded, onToggle)
    }
    AnimatedVisibility(
        visible = changes.size == 1 || expanded,
        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            changes.forEachIndexed { index, change ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                ChangeComparison(change)
            }
        }
    }
}

@Composable
private fun HistoryToggle(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label + ", " +
                    if (expanded) "expandido" else "contraído"
            },
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Image(
            painterResource(R.drawable.ic_v2_chevron),
            contentDescription = null,
            modifier = Modifier.size(18.dp).graphicsLayer {
                rotationZ = if (expanded) 270f else 90f
            },
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun ChangeComparison(change: MedicationFieldChange) {
    val description = when {
        change.before == null ->
            change.field + ", añadido: " + change.after.orEmpty()
        change.after == null ->
            change.field + ", retirado: " + change.before
        else ->
            change.field + ", antes " + change.before + ", ahora " + change.after
    }
    Column(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = description
        },
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Text(
            change.field,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when {
            change.before == null ->
                ChangeValue("Añadido", change.after ?: "Sin detalle disponible", true)
            change.after == null ->
                ChangeValue("Retirado", change.before, false)
            else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.5f
                if (stacked) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    ) {
                        ChangeValue("Antes", change.before, false)
                        Text(
                            "↓",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ChangeValue("Ahora", change.after, true)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            ChangeValue("Antes", change.before, false)
                        }
                        Text(
                            "→",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Box(Modifier.weight(1f)) {
                            ChangeValue("Ahora", change.after, true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeValue(label: String, value: String, emphasized: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun MedicationPlanRevision.capturedMoment() =
    Instant.ofEpochMilli(capturedAtEpochMillis).atZone(ZoneId.systemDefault())

private fun MedicationPlanRevision.lifecycleEffectiveMoment() =
    Instant.ofEpochMilli(requireNotNull(lifecycleEffectiveAtEpochMillis))
        .atZone(ZoneId.systemDefault())

private fun MedicationPlanChangeKind.effectiveLabel(): String = when (this) {
    MedicationPlanChangeKind.SUSPENDED -> "Suspendido desde"
    MedicationPlanChangeKind.REACTIVATED -> "Reactivado desde"
    MedicationPlanChangeKind.REACTIVATION_RESCHEDULED -> "Reprogramado para"
    MedicationPlanChangeKind.REACTIVATION_CANCELLED -> "Cancelado el"
    MedicationPlanChangeKind.COMPLETED -> "Finalizado desde"
    MedicationPlanChangeKind.REMOVED -> "Retirado desde"
    else -> "Vigente desde"
}

private fun MedicationPlanChangeKind.historyPurpose(): String = when (this) {
    MedicationPlanChangeKind.CREATED -> "Registro inicial"
    MedicationPlanChangeKind.SCHEDULE_CORRECTED ->
        "Cambio de indicación médica"
    MedicationPlanChangeKind.SUSPENDED -> "Suspensión"
    MedicationPlanChangeKind.REACTIVATED -> "Reactivación"
    MedicationPlanChangeKind.REACTIVATION_RESCHEDULED ->
        "Reprogramación de reactivación"
    MedicationPlanChangeKind.REACTIVATION_CANCELLED ->
        "Cancelación de reactivación"
    MedicationPlanChangeKind.COMPLETED -> "Finalización"
    MedicationPlanChangeKind.REMOVED -> "Eliminación"
    else -> "Corrección de datos"
}

private fun MedicationPlanChangeKind.historyIcon(): Int = when (this) {
    MedicationPlanChangeKind.CREATED -> R.drawable.ic_v2_plus
    MedicationPlanChangeKind.SCHEDULE_CORRECTED -> R.drawable.ic_v2_stethoscope
    else -> R.drawable.ic_v2_edit
}

private fun MedicationPlanRevision.historyFields(): Map<String, String> = buildMap {
    put("Nombre", medicationName)
    put("Inicio registrado", startsOn.format(historyDateFormatter))
    put(
        "Finalización registrada",
        endsOn?.format(historyDateFormatter) ?: "Sin fecha de finalización",
    )
    originalAdditionalInstructions?.takeIf(String::isNotBlank)?.let {
        put("Notas originales", it)
    }
    val data = configuration
    if (data == null) {
        put("Dosis transcrita", doseAsIndicated)
        unitOrPresentation?.medicationPresentationDisplayName()?.let {
            put("Presentación transcrita", it)
        }
        frequencyAsIndicated?.takeIf(String::isNotBlank)?.let {
            put("Periodicidad transcrita", it)
        }
        if (scheduledDoses.isNotEmpty()) {
            put(
                "Horarios transcritos",
                scheduledDoses.joinToString(" · ") { it.time.historyTime() },
            )
        }
        return@buildMap
    }
    put("Forma", data.form.displayName())
    if (!data.concentrationValue.isNullOrBlank() && !data.concentrationUnit.isNullOrBlank()) {
        put("Concentración", data.concentrationValue + " " + data.concentrationUnit)
    }
    put("Cantidad por toma", data.baseAmount.value + " " + data.baseAmount.unit)
    put("Periodicidad", data.frequencySummary())
    when (data.frequency) {
        MedicationFrequencyKind.EVERY_N_DAYS -> data.everyNDaysReferenceDate?.let {
            put("Fecha de referencia", it.format(historyDateFormatter))
        }
        MedicationFrequencyKind.EVERY_N_HOURS -> {
            data.hourlyMode?.let {
                put(
                    "Modalidad horaria",
                    if (it == MedicationHourlyIntervalMode.CONTINUOUS) {
                        "Continua"
                    } else {
                        "Dentro de una ventana diaria"
                    },
                )
            }
            listOfNotNull(
                data.hourlyReferenceDate?.format(historyDateFormatter),
                data.hourlyReferenceTime?.historyTime(),
            ).joinToString(" · ").takeIf(String::isNotBlank)?.let {
                put("Referencia horaria", it)
            }
            if (data.hourlyWindowStart != null && data.hourlyWindowEnd != null) {
                put(
                    "Ventana horaria",
                    data.hourlyWindowStart.historyTime() + " – " +
                        data.hourlyWindowEnd.historyTime() +
                        if (data.hourlyWindowEndsNextDay) " · día siguiente" else "",
                )
            }
            data.hourlyFirstAdministration?.let {
                put("Primera administración", it.historyTime())
            }
        }
        MedicationFrequencyKind.CYCLE -> data.cycle?.let {
            put("Referencia del ciclo", it.referenceDate.format(historyDateFormatter))
            put(
                "Número de ciclos",
                it.numberOfCycles?.toString() ?: "Sin número de ciclos definido",
            )
        }
        MedicationFrequencyKind.AS_NEEDED -> {
            data.asNeededInstruction?.takeIf(String::isNotBlank)?.let {
                put("Indicación según necesidad", it)
            }
            data.asNeededRestrictions?.takeIf(String::isNotBlank)?.let {
                put("Restricciones según necesidad", it)
            }
        }
        else -> Unit
    }
    put("Avisos", data.reminderPreference.historyLabel())
    if (data.secondReminderEnabled) put("Segundo aviso", "Activado")
    put("Estado", data.status.historyLabel())
}

private fun administrationChanges(
    previous: MedicationPlanRevision,
    current: MedicationPlanRevision,
): List<MedicationFieldChange> {
    val before = previous.configuration?.administrations.orEmpty().toMutableList()
    val after = current.configuration?.administrations.orEmpty().toMutableList()
    val changes = mutableListOf<MedicationFieldChange>()

    // A stable identifier is authoritative for revisions saved after the identity fix.
    before.map { it.id }.intersect(after.map { it.id }.toSet()).forEach { id ->
        val old = before.first { it.id == id }
        val new = after.first { it.id == id }
        before.remove(old)
        after.remove(new)
        changes += old.fieldChangesTo(new)
    }

    // Older revisions regenerated every identifier. Remove fully equivalent entries first,
    // one by one, so reordering and duplicate administrations do not become changes.
    var beforeIndex = 0
    while (beforeIndex < before.size) {
        val afterIndex = after.indexOfFirst { before[beforeIndex].hasSameDataAs(it) }
        if (afterIndex >= 0) {
            before.removeAt(beforeIndex)
            after.removeAt(afterIndex)
        } else {
            beforeIndex += 1
        }
    }

    // A unique schedule anchor identifies a remaining administration even when a mutable
    // attribute such as its notice or amount changed.
    (before.map { it.correspondenceKey() } + after.map { it.correspondenceKey() })
        .distinct()
        .forEach { key ->
            val oldMatches = before.filter { it.correspondenceKey() == key }
            val newMatches = after.filter { it.correspondenceKey() == key }
            if (oldMatches.size == 1 && newMatches.size == 1) {
                val old = oldMatches.single()
                val new = newMatches.single()
                before.remove(old)
                after.remove(new)
                changes += old.fieldChangesTo(new)
            }
        }

    // With a single candidate on each side, the replacement is unambiguous even if its
    // schedule anchor changed (for example an exact time was corrected).
    if (before.size == 1 && after.size == 1) {
        changes += before.removeAt(0).fieldChangesTo(after.removeAt(0))
    }

    if (before.isNotEmpty() && after.isNotEmpty()) {
        // Legacy data can leave several equally plausible pairings. Preserve the fact that
        // the programming changed without inventing which entry replaced which.
        changes += MedicationFieldChange(
            field = "Programación modificada",
            before = before.joinToString(separator = "\n") { it.historyDescription() },
            after = after.joinToString(separator = "\n") { it.historyDescription() },
        )
    } else {
        changes += before.map {
            MedicationFieldChange("Administración retirada", it.historyDescription(), null)
        }
        changes += after.map {
            MedicationFieldChange("Administración añadida", null, it.historyDescription())
        }
    }
    return changes
}

private fun MedicationAdministration.fieldChangesTo(
    other: MedicationAdministration,
): List<MedicationFieldChange> {
    val oldFields = historyFields()
    val newFields = other.historyFields()
    return (oldFields.keys + newFields.keys).distinct().mapNotNull { field ->
        val oldValue = oldFields[field]
        val newValue = newFields[field]
        if (oldValue == newValue) null
        else MedicationFieldChange(field, oldValue, newValue)
    }
}

private fun MedicationAdministration.hasSameDataAs(
    other: MedicationAdministration,
): Boolean = copy(id = "_") == other.copy(id = "_")

private fun MedicationAdministration.correspondenceKey(): String = when (timing) {
    MedicationAdministrationTiming.EXACT_TIME ->
        "exact|$exactTime"
    MedicationAdministrationTiming.TIME_WINDOW ->
        "window|$windowStart|$windowEnd|$windowEndsNextDay"
    MedicationAdministrationTiming.ACTIVITY_ONLY ->
        "activity|$activityRelation|${customActivity.orEmpty()}"
}

private fun MedicationAdministration.historyFields(): Map<String, String> = buildMap {
    put(
        "Tipo de administración",
        when (timing) {
            MedicationAdministrationTiming.EXACT_TIME -> "Hora exacta"
            MedicationAdministrationTiming.TIME_WINDOW -> "Ventana horaria"
            MedicationAdministrationTiming.ACTIVITY_ONLY -> "Actividad"
        },
    )
    when (timing) {
        MedicationAdministrationTiming.EXACT_TIME -> {
            exactTime?.let { put("Hora de toma", it.historyTime()) }
            put(
                "Aviso de la hora de toma",
                if (reminderEnabled == false) "Desactivado" else "Activado",
            )
        }
        MedicationAdministrationTiming.TIME_WINDOW -> {
            if (windowStart != null && windowEnd != null) {
                put(
                    "Ventana horaria",
                    windowStart.historyTime() + " – " + windowEnd.historyTime() +
                        if (windowEndsNextDay) " · día siguiente" else "",
                )
            }
            noticeTime?.let {
                put(
                    "Hora de aviso de la ventana",
                    it.historyTime() + if (noticeNextDay) " · día siguiente" else "",
                )
            }
        }
        MedicationAdministrationTiming.ACTIVITY_ONLY -> {
            val activity = activityRelation?.displayName(customActivity)
                ?: "Actividad no registrada"
            put("Actividad asociada", activity)
            activityNoticeTime?.let {
                put("Hora del aviso", it.historyTime())
            }
        }
    }
    amountOverride?.let {
        put("Cantidad de esta administración", it.value + " " + it.unit)
    }
}

private fun MedicationAdministration.historyDescription(): String = buildString {
    when (timing) {
        MedicationAdministrationTiming.EXACT_TIME -> {
            append(exactTime?.historyTime() ?: "Hora no disponible")
        }
        MedicationAdministrationTiming.TIME_WINDOW -> {
            append("Ventana ")
            append(windowStart?.historyTime() ?: "inicio no disponible")
            append(" – ")
            append(windowEnd?.historyTime() ?: "fin no disponible")
            if (windowEndsNextDay) append(" · día siguiente")
            noticeTime?.let {
                append(" · aviso ")
                append(it.historyTime())
                if (noticeNextDay) append(" del día siguiente")
            }
        }
        MedicationAdministrationTiming.ACTIVITY_ONLY -> {
            val activity = activityRelation?.displayName(customActivity)
                ?: "Actividad no registrada"
            append(activity)
            activityNoticeTime?.let {
                append(" · aviso ")
                append(it.historyTime())
            }
        }
    }
    amountOverride?.let {
        append(" · cantidad ")
        append(it.value)
        append(" ")
        append(it.unit)
    }
}

private fun MedicationPlanRevision.initialSummary(): String {
    val data = configuration
    return if (data == null) {
        listOfNotNull(
            unitOrPresentation?.medicationPresentationDisplayName(),
            frequencyAsIndicated,
        ).joinToString(" · ").ifBlank { "Configuración inicial registrada" }
    } else {
        listOfNotNull(
            data.form.displayName(),
            if (!data.concentrationValue.isNullOrBlank() && !data.concentrationUnit.isNullOrBlank()) {
                data.concentrationValue + " " + data.concentrationUnit
            } else null,
            data.frequencySummary(),
        ).joinToString(" · ")
    }
}

private fun MedicationPlanRevision.initialGroups(): List<InitialHistoryGroup> {
    val data = configuration
    if (data == null) {
        return listOfNotNull(
            InitialHistoryGroup(
                "Presentación",
                listOfNotNull(unitOrPresentation?.medicationPresentationDisplayName()),
            ).takeIf { it.values.isNotEmpty() },
            InitialHistoryGroup("Cantidad", listOf(doseAsIndicated)),
            InitialHistoryGroup(
                "Periodicidad",
                listOfNotNull(
                    frequencyAsIndicated,
                    "Inicio: " + startsOn.format(historyDateFormatter),
                    endsOn?.let { "Finaliza: " + it.format(historyDateFormatter) }
                        ?: "Sin fecha de finalización",
                ),
            ),
            InitialHistoryGroup(
                "Avisos",
                scheduledDoses.map { "Hora de toma " + it.time.historyTime() },
            ).takeIf { it.values.isNotEmpty() },
        )
    }
    val presentation = buildList {
        add(data.form.displayName())
        if (!data.concentrationValue.isNullOrBlank() && !data.concentrationUnit.isNullOrBlank()) {
            add(data.concentrationValue + " " + data.concentrationUnit)
        }
    }
    val periodicity = buildList {
        add(data.frequencySummary())
        add("Inicio: " + startsOn.format(historyDateFormatter))
        add(
            endsOn?.let { "Finaliza: " + it.format(historyDateFormatter) }
                ?: "Sin fecha de finalización",
        )
        when (data.frequency) {
            MedicationFrequencyKind.EVERY_N_DAYS -> data.everyNDaysReferenceDate?.let {
                add("Referencia: " + it.format(historyDateFormatter))
            }
            MedicationFrequencyKind.EVERY_N_HOURS -> {
                data.hourlyReferenceDate?.let {
                    add("Referencia: " + it.format(historyDateFormatter))
                }
                data.hourlyReferenceTime?.let { add("Desde las " + it.historyTime()) }
                if (data.hourlyWindowStart != null && data.hourlyWindowEnd != null) {
                    add(
                        "Ventana " + data.hourlyWindowStart.historyTime() + " – " +
                            data.hourlyWindowEnd.historyTime() +
                            if (data.hourlyWindowEndsNextDay) " · día siguiente" else "",
                    )
                }
            }
            MedicationFrequencyKind.CYCLE -> data.cycle?.let {
                add("Referencia: " + it.referenceDate.format(historyDateFormatter))
                add(
                    it.numberOfCycles?.let { count -> count.toString() + " ciclos en total" }
                        ?: "Sin número de ciclos definido",
                )
            }
            MedicationFrequencyKind.AS_NEEDED -> {
                data.asNeededInstruction?.takeIf(String::isNotBlank)?.let(::add)
                data.asNeededRestrictions?.takeIf(String::isNotBlank)?.let(::add)
            }
            else -> Unit
        }
    }
    val reminders = buildList {
        add(data.reminderPreference.historyLabel())
        if (data.secondReminderEnabled) add("Segundo aviso activado")
        data.administrations.forEach { add(it.historyDescription()) }
    }
    return listOf(
        InitialHistoryGroup("Presentación", presentation),
        InitialHistoryGroup("Cantidad", listOf(data.baseAmount.value + " " + data.baseAmount.unit)),
        InitialHistoryGroup("Periodicidad", periodicity),
        InitialHistoryGroup("Avisos", reminders),
    )
}

private fun MedicationReminderPreference.historyLabel(): String = when (this) {
    MedicationReminderPreference.NONE -> "Avisos desactivados"
    MedicationReminderPreference.NORMAL -> "Avisos normales"
    MedicationReminderPreference.PROMINENT -> "Avisos destacados"
}

private fun MedicationTreatmentStatus.historyLabel(): String = when (this) {
    MedicationTreatmentStatus.ACTIVE -> "Vigente"
    MedicationTreatmentStatus.SUSPENDED -> "Suspendido"
    MedicationTreatmentStatus.COMPLETED -> "Finalizado"
    MedicationTreatmentStatus.REMOVED -> "Retirado del uso diario"
}

private fun LocalTime.historyTime(): String =
    format(DateTimeFormatter.ofPattern("HH:mm", historyLocale))
