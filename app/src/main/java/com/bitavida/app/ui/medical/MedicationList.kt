package com.bitavida.app.ui.medical

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

// Presentation-only snapshots survive the detail destination, never write medication data.
internal class MedicationListMemory {
    var groups: List<Pair<MedicationPlanDisplayState, List<MedicationPlan>>>? = null
}

internal val LocalMedicationListMemory = staticCompositionLocalOf<MedicationListMemory?> { null }

internal data class MedicationIconTransition(
    val shared: SharedTransitionScope,
    val destination: AnimatedVisibilityScope,
)

internal val LocalMedicationIconTransition =
    staticCompositionLocalOf<MedicationIconTransition?> { null }

@Composable
internal fun Modifier.medicationIconTransition(planId: String): Modifier {
    val (shared, destination) = LocalMedicationIconTransition.current ?: return this
    return with(shared) {
        this@medicationIconTransition.sharedElement(
            sharedContentState = rememberSharedContentState("medication-icon-$planId"),
            animatedVisibilityScope = destination,
            boundsTransform = { _, _ -> tween(BitavidaFluidMotion.StateTransition) },
        )
    }
}

@Composable
internal fun MedicationModularList(
    groups: List<Pair<MedicationPlanDisplayState, List<MedicationPlan>>>,
    motionAllowed: Boolean,
    onOpen: (MedicationPlan) -> Unit,
) {
    val memory = LocalMedicationListMemory.current
    var previousGroups by remember { mutableStateOf(memory?.groups ?: groups) }
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    SideEffect { memory?.groups = groups }
    LaunchedEffect(groups, duration) {
        // Keep outgoing rows until their exit finishes. Unchanged rows never replay entrance.
        if (previousGroups != groups && duration > 0) delay(duration.milliseconds)
        previousGroups = groups
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Large),
    ) {
        groups.forEach { (displayState, plans) ->
            key(displayState) {
                val previous = previousGroups.singleOrNull { it.first == displayState }
                    ?.second.orEmpty()
                MedicationStatusModule(displayState, plans, previous, duration, motionAllowed, onOpen)
            }
        }
    }
}

@Composable
private fun MedicationStatusModule(
    displayState: MedicationPlanDisplayState,
    plans: List<MedicationPlan>,
    previous: List<MedicationPlan>,
    duration: Int,
    motionAllowed: Boolean,
    onOpen: (MedicationPlan) -> Unit,
) {
    val compact = LocalDensity.current.fontScale >= 1.5f
    val container = when (displayState) {
        MedicationPlanDisplayState.CURRENT -> MaterialTheme.bitavidaColors.mint
        MedicationPlanDisplayState.UPCOMING -> MaterialTheme.bitavidaColors.paleBlue
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(BitavidaSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
                horizontalArrangement = Arrangement.spacedBy(
                    if (compact) BitavidaSpacing.ExtraSmall else BitavidaSpacing.Small,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (displayState == MedicationPlanDisplayState.SUSPENDED) {
                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                    Canvas(Modifier.size(if (compact) 16.dp else 20.dp)) {
                        listOf(0.35f, 0.65f).forEach { x ->
                            drawLine(
                                color, Offset(size.width * x, size.height * 0.22f),
                                Offset(size.width * x, size.height * 0.78f),
                                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                            )
                        }
                    }
                } else {
                    Image(
                        painterResource(
                            if (displayState == MedicationPlanDisplayState.CURRENT)
                                R.drawable.ic_v2_pill else R.drawable.ic_v2_calendar,
                        ),
                        null, Modifier.size(if (compact) 16.dp else 20.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    displayState.groupTitle,
                    modifier = Modifier.weight(1f),
                    style = if (compact) MaterialTheme.typography.labelLarge
                        else MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        plans.size.toString(),
                        modifier = Modifier
                            .clearAndSetSemantics {
                                contentDescription = "${plans.size} " +
                                    if (plans.size == 1) "medicamento" else "medicamentos"
                                liveRegion = LiveRegionMode.Polite
                            }
                            .padding(horizontal = BitavidaSpacing.Small, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val rows = (plans + previous).distinctBy(MedicationPlan::id)
            rows.forEach { plan ->
                key(plan.id) {
                    val present = plans.any { it.id == plan.id }
                    val visibility = remember {
                        MutableTransitionState(previous.any { it.id == plan.id })
                    }
                    visibility.targetState = present
                    AnimatedVisibility(
                        visibleState = visibility,
                        enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
                        exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
                    ) {
                        MedicationListRow(
                            plan, motionAllowed, present,
                            onClick = { if (present) onOpen(plan) },
                        )
                    }
                }
            }
            if (plans.isEmpty() && rows.isEmpty()) {
                Text(
                    "Sin medicamentos en este grupo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = BitavidaSpacing.Small),
                )
            }
        }
    }
}

@Composable
private fun MedicationListRow(
    plan: MedicationPlan,
    motionAllowed: Boolean,
    present: Boolean,
    onClick: () -> Unit,
) {
    val revision = plan.currentRevision
    val configuration = revision.configuration
    val form = configuration?.form ?: MedicationProductForm.entries
        .singleOrNull { it.name == revision.unitOrPresentation }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val duration = if (motionAllowed) BitavidaFluidMotion.Press else 0
    val surface = MaterialTheme.colorScheme.surface
    val color by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f).compositeOver(surface)
        else surface,
        tween(duration), label = "medication_row_press",
    )
    val chevronOffset by animateDpAsState(
        if (pressed && motionAllowed) 3.dp else 0.dp,
        tween(duration), label = "medication_row_chevron",
    )
    val shape = MaterialTheme.shapes.medium
    val compact = LocalDensity.current.fontScale >= 1.5f
    Surface(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .clickable(
                enabled = present,
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = "Ver detalle del medicamento",
                onClick = onClick,
            ),
        shape = shape, color = color,
        shadowElevation = 0.dp, tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                if (compact) BitavidaSpacing.Small else BitavidaSpacing.Medium,
            ),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                MedicationListProductIcon(
                    form,
                    modifier = Modifier.padding(4.dp)
                        .then(if (present) Modifier.medicationIconTransition(plan.id) else Modifier)
                        .size(if (compact) 28.dp else 32.dp)
                        .semantics { contentDescription = form?.displayName() ?: "Presentación registrada" },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                MedicationListIdentity(
                    name = revision.medicationName,
                    concentration = configuration?.let {
                        listOfNotNull(it.concentrationValue, it.concentrationUnit)
                            .joinToString(" ").takeIf(String::isNotBlank)
                    },
                    largeText = compact,
                )
                MedicationListAmount(configuration, revision.doseAsIndicated)
                MedicationFrequencyChip(configuration, revision.frequencyAsIndicated)
                if (configuration?.status == MedicationTreatmentStatus.REMOVED) {
                    Text(
                        "Retiro programado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Image(
                painterResource(R.drawable.ic_v2_chevron), null,
                Modifier.align(Alignment.CenterVertically).offset { IntOffset(chevronOffset.roundToPx(), 0) }.size(12.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun MedicationListIdentity(
    name: String,
    concentration: String?,
    largeText: Boolean,
) {
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.titleMedium
    val concentrationStyle = MaterialTheme.typography.bodySmall
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = with(LocalDensity.current) { maxWidth.toPx() }
        val fitsBesideName = !largeText && concentration != null &&
            measurer.measure(AnnotatedString(name), style = nameStyle, softWrap = false).size.width +
            measurer.measure(
                AnnotatedString(" · $concentration"),
                style = concentrationStyle,
                softWrap = false,
            ).size.width + 2 <= available
        if (fitsBesideName) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MedicationListName(name, largeText, Modifier.weight(1f))
                Text(
                    " · $concentration",
                    style = concentrationStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                MedicationListName(name, largeText, Modifier.fillMaxWidth())
                if (concentration != null) {
                    Text(
                        concentration,
                        modifier = Modifier.fillMaxWidth(),
                        softWrap = true,
                        style = concentrationStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationListName(name: String, largeText: Boolean, modifier: Modifier) {
    Text(
        name,
        modifier = modifier.clearAndSetSemantics { contentDescription = name },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (largeText) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun MedicationListProductIcon(form: MedicationProductForm?, modifier: Modifier) {
    if (form != null) MedicationProductIcon(form, modifier)
    else Image(
        painterResource(R.drawable.ic_v2_pill), null, modifier,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun MedicationListAmount(configuration: MedicationConfiguration?, fallback: String) {
    val effective = configuration?.let { config ->
        config.administrations.map { it.amountOverride ?: config.baseAmount }
            .ifEmpty { listOf(config.baseAmount) }.distinct()
    }.orEmpty()
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val text = buildAnnotatedString {
        fun amount(value: MedicationAmount) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value.value) }
            append(" ${value.unit}")
        }
        when {
            configuration == null -> append(fallback)
            effective.size == 1 -> {
                amount(effective.single())
                withStyle(MaterialTheme.typography.bodySmall.toSpanStyle().copy(color = secondary)) {
                    append(" por toma")
                }
            }
            else -> configuration.administrations.forEachIndexed { index, administration ->
                if (index > 0) append("\n")
                amount(administration.amountOverride ?: configuration.baseAmount)
                withStyle(MaterialTheme.typography.bodySmall.toSpanStyle().copy(color = secondary)) {
                    append(" · ${administration.listTiming()}")
                }
            }
        }
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        softWrap = true,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun MedicationAdministration.listTiming(): String {
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    return when (timing) {
        MedicationAdministrationTiming.EXACT_TIME -> exactTime?.format(clock).orEmpty()
        MedicationAdministrationTiming.TIME_WINDOW ->
            "${windowStart?.format(clock)}–${windowEnd?.format(clock)}" +
                if (windowEndsNextDay) " (día siguiente)" else ""
        MedicationAdministrationTiming.ACTIVITY_ONLY ->
            activityRelation?.displayName(customActivity) ?: "Actividad registrada"
    }
}

@Composable
private fun MedicationFrequencyChip(configuration: MedicationConfiguration?, fallback: String?) {
    val full = configuration?.frequencySummary() ?: fallback ?: "Programación registrada"
    val weekdays = configuration?.takeIf { it.frequency == MedicationFrequencyKind.SPECIFIC_DAYS }
        ?.weekdays?.sortedBy(DayOfWeek::getValue).orEmpty()
    val shortDays = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    val compact = weekdays.map { shortDays[it.value - 1] }.let { days ->
        when (days.size) {
            0 -> full
            1 -> days.single()
            else -> days.dropLast(1).joinToString(", ") + " y " + days.last()
        }
    }
    val cycles = configuration?.cycle?.numberOfCycles?.let { " · $it ciclos" }.orEmpty()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = full + cycles },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BitavidaSpacing.Small, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            verticalAlignment = Alignment.Top,
        ) {
            Image(
                painterResource(
                    if (configuration?.frequency == MedicationFrequencyKind.EVERY_N_HOURS)
                        R.drawable.ic_v2_clock else R.drawable.ic_v2_calendar,
                ),
                null, Modifier.padding(top = 2.dp).size(16.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )
            Text(
                compact + cycles,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
