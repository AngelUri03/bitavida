package com.bitavida.app.ui.demo

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.components.BitavidaFluidMotion
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.brand.BitavidaMark
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaConnectedSurface
import com.bitavida.app.ui.components.BitavidaConfirmationAction
import com.bitavida.app.ui.components.BitavidaEmptyJourney
import com.bitavida.app.ui.components.BitavidaHeaderFlow
import com.bitavida.app.ui.components.BitavidaIconContainer
import com.bitavida.app.ui.components.BitavidaNavigationItem
import com.bitavida.app.ui.components.BitavidaNodeStyle
import com.bitavida.app.ui.components.BitavidaProgressiveReveal
import com.bitavida.app.ui.components.BitavidaStatusPill
import com.bitavida.app.ui.components.BitavidaThreadAction
import com.bitavida.app.ui.components.BitavidaThreadFlowState
import com.bitavida.app.ui.components.BitavidaThreadLayout
import com.bitavida.app.ui.components.BitavidaThreadNavigation
import com.bitavida.app.ui.components.BitavidaThreadSection
import com.bitavida.app.ui.components.rememberBitavidaThreadFlowState
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors

enum class TodayDemoState {
    Normal,
    Empty,
}

@Composable
fun TodayDemoScreen(
    modifier: Modifier = Modifier,
    state: TodayDemoState = TodayDemoState.Normal,
    motionEnabledOverride: Boolean? = null,
    initialDemoConfirmed: Boolean = false,
    initialAppointmentExpanded: Boolean = false,
) {
    var isConfirmed by rememberSaveable { mutableStateOf(initialDemoConfirmed) }
    var appointmentExpanded by rememberSaveable {
        mutableStateOf(initialAppointmentExpanded)
    }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    val navigationItems = listOf(
        BitavidaNavigationItem(stringResource(R.string.nav_today), R.drawable.ic_v2_home),
        BitavidaNavigationItem(stringResource(R.string.nav_plan), R.drawable.ic_v2_clipboard),
        BitavidaNavigationItem(
            stringResource(R.string.nav_appointments),
            R.drawable.ic_v2_calendar,
        ),
        BitavidaNavigationItem(stringResource(R.string.nav_report), R.drawable.ic_v2_chart),
    )

    val scrollState = rememberScrollState()
    val motionAllowed = rememberBitavidaMotionAllowed(motionEnabledOverride)
    val threadFlowState = rememberBitavidaThreadFlowState(scrollState, motionAllowed)
    BitavidaFluidBackground(
        modifier = modifier.fillMaxSize(),
        scrollPixels = { scrollState.value.toFloat() },
        motionAllowed = motionAllowed,
    ) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("today_screen"),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            BitavidaThreadNavigation(
                items = navigationItems,
                selectedIndex = 0,
                motionEnabledOverride = motionEnabledOverride,
                currentSectionLabel = stringResource(
                    R.string.nav_current_section,
                    navigationItems.first().label,
                ),
                onSelected = { index ->
                    if (index != 0) feedback = navigationItems[index].label
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clipToBounds()
                .verticalScroll(scrollState)
                .testTag("today_scroll")
                .padding(horizontal = BitavidaSpacing.ScreenHorizontal),
        ) {
            Spacer(Modifier.height(BitavidaSpacing.Medium))
            EditorialHeader()
            HeaderThreadFlourish(motionAllowed = motionAllowed)
            Spacer(Modifier.height(BitavidaSpacing.Small))

            when (state) {
                TodayDemoState.Normal -> NormalJourney(
                    isConfirmed = isConfirmed,
                    appointmentExpanded = appointmentExpanded,
                    motionEnabledOverride = motionEnabledOverride,
                    motionAllowed = motionAllowed,
                    threadFlowState = threadFlowState,
                    onConfirm = {
                        if (!isConfirmed) isConfirmed = true
                    },
                    onToggleAppointment = {
                        appointmentExpanded = !appointmentExpanded
                    },
                    onUnavailableAction = { feedback = it },
                )

                TodayDemoState.Empty -> EmptyJourney(
                    motionEnabledOverride = motionEnabledOverride,
                    onUnavailableAction = { feedback = it },
                )
            }

            feedback?.let { action ->
                Spacer(Modifier.height(BitavidaSpacing.Default))
                Text(
                    text = stringResource(R.string.today_demo_feedback, action),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(BitavidaSpacing.Large))
        }
    }
}

}

@Composable
private fun EditorialHeader() {
    val largeText = LocalDensity.current.fontScale >= 1.5f

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackHeader = largeText || (maxWidth < 350.dp)
        if (stackHeader) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                BrandLockup()
                Text(
                    text = stringResource(R.string.today_date_context),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandLockup(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.today_date_context),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(Modifier.height(BitavidaSpacing.Large))
    Text(
        text = stringResource(R.string.today_greeting),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(BitavidaSpacing.ExtraSmall))
    Text(
        text = stringResource(R.string.today_patient_context),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(R.string.today_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BrandLockup(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        BitavidaMark(
            modifier = Modifier.size(34.dp),
            strokeWidth = 3.4.dp,
            eventRadius = 2.6.dp,
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.bitavidaColors.brand,
        )
    }
}

@Composable
private fun HeaderThreadFlourish(motionAllowed: Boolean) {
    BitavidaHeaderFlow(
        modifier = Modifier.testTag("header_flow"),
        motionAllowed = motionAllowed,
    )
}

@Composable
private fun NormalJourney(
    isConfirmed: Boolean,
    appointmentExpanded: Boolean,
    motionEnabledOverride: Boolean?,
    motionAllowed: Boolean,
    threadFlowState: BitavidaThreadFlowState,
    onConfirm: () -> Unit,
    onToggleAppointment: () -> Unit,
    onUnavailableAction: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.today_journey_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(BitavidaSpacing.Medium))

    BitavidaThreadLayout(
        flowState = threadFlowState,
    ) {
        BitavidaProgressiveReveal(
            order = 0,
            motionEnabledOverride = motionEnabledOverride,
        ) {
            BitavidaThreadSection(
                title = stringResource(R.string.today_demo_medication),
                eyebrow = stringResource(R.string.today_upcoming),
            ) {
                Spacer(Modifier.height(BitavidaSpacing.Small))
                MedicationEvent(
                    isConfirmed = isConfirmed,
                    onConfirm = onConfirm,
                    motionAllowed = motionAllowed,
                )
            }
        }

        BitavidaProgressiveReveal(
            order = 1,
            motionEnabledOverride = motionEnabledOverride,
        ) {
            BitavidaThreadSection(
                title = stringResource(R.string.today_day_title),
                eyebrow = stringResource(R.string.today_records_eyebrow),
            ) {
                Spacer(Modifier.height(BitavidaSpacing.Small))
                DailyRecords()
            }
        }

        BitavidaProgressiveReveal(
            order = 2,
            motionEnabledOverride = motionEnabledOverride,
        ) {
            BitavidaThreadSection(
                title = stringResource(R.string.today_appointments_title),
                eyebrow = stringResource(R.string.today_appointments_eyebrow),
                nodeStyle = BitavidaNodeStyle.Open,
            ) {
                Spacer(Modifier.height(BitavidaSpacing.Small))
                AppointmentEvent(
                    expanded = appointmentExpanded,
                    motionEnabledOverride = motionEnabledOverride,
                    onToggle = onToggleAppointment,
                )
            }
        }

        BitavidaProgressiveReveal(
            order = 3,
            motionEnabledOverride = motionEnabledOverride,
        ) {
            BitavidaThreadSection(
                title = stringResource(R.string.today_quick_title),
                eyebrow = stringResource(R.string.today_quick_eyebrow),
                nodeStyle = BitavidaNodeStyle.Quiet,
            ) {
                Spacer(Modifier.height(BitavidaSpacing.Small))
                QuickJourneyActions(
                    motionEnabledOverride = motionEnabledOverride,
                    onUnavailableAction = onUnavailableAction,
                )
            }
        }
    }
}

@Composable
private fun MedicationEvent(
    isConfirmed: Boolean,
    onConfirm: () -> Unit,
    motionAllowed: Boolean,
) {
    BitavidaConnectedSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("medication_card"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_v2_pill),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.today_medication_origin),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.today_demo_dose),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(BitavidaSpacing.Medium))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackStatus = (maxWidth < 280.dp) ||
                (LocalDensity.current.fontScale >= 1.5f)
            if (stackStatus) {
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    MedicationTime()
                    MedicationStatus(
                        isConfirmed = isConfirmed,
                        motionAllowed = motionAllowed,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MedicationTime()
                    MedicationStatus(
                        isConfirmed = isConfirmed,
                        motionAllowed = motionAllowed,
                    )
                }
            }
        }

        Spacer(Modifier.height(BitavidaSpacing.Medium))
        BitavidaConfirmationAction(
            confirmed = isConfirmed,
            initialLabel = stringResource(R.string.today_confirm_action),
            confirmedLabel = stringResource(R.string.today_confirmed_action),
            iconRes = R.drawable.ic_v2_chevron,
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("confirm_medication"),
            motionAllowed = motionAllowed,
        )
        if (!motionAllowed) {
            if (isConfirmed) ConfirmationAnnouncement()
        } else {
            AnimatedVisibility(
                visible = isConfirmed,
                enter = fadeIn(tween(BitavidaFluidMotion.ConfirmationTransition)) +
                    expandVertically(tween(BitavidaFluidMotion.ConfirmationTransition)),
                exit = fadeOut(tween(BitavidaFluidMotion.ConfirmationTransition / 2)) +
                    shrinkVertically(tween(BitavidaFluidMotion.ConfirmationTransition)),
            ) {
                ConfirmationAnnouncement()
            }
        }
    }
}

@Composable
private fun ConfirmationAnnouncement() {
    Column {
        Spacer(Modifier.height(BitavidaSpacing.Small))
        Text(
            text = stringResource(R.string.today_confirmation_announcement),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MedicationTime() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_v2_clock),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = stringResource(R.string.today_demo_time),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MedicationStatus(
    isConfirmed: Boolean,
    motionAllowed: Boolean,
) {
    if (!motionAllowed) {
        MedicationStatusPill(isConfirmed)
    } else {
        AnimatedContent(
            targetState = isConfirmed,
            transitionSpec = {
                fadeIn(tween(BitavidaFluidMotion.ConfirmationTransition)) togetherWith
                    fadeOut(tween(BitavidaFluidMotion.ConfirmationTransition / 2))
            },
        ) { confirmed ->
            MedicationStatusPill(confirmed)
        }
    }
}

@Composable
private fun MedicationStatusPill(isConfirmed: Boolean) {
    BitavidaStatusPill(
        text = stringResource(
            if (isConfirmed) R.string.today_confirmed
            else R.string.today_unconfirmed,
        ),
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = if (isConfirmed) {
                "Estado: toma ficticia confirmada en demostración"
            } else {
                "Estado: toma ficticia sin confirmar"
            }
        },
        isPositive = isConfirmed,
    )
}

@Composable
private fun DailyRecords() {
    Column {
        DailyRecord(
            icon = R.drawable.ic_v2_utensils,
            label = stringResource(R.string.today_meals),
            value = stringResource(R.string.today_meals_value),
            state = stringResource(R.string.today_meals_state),
            nodeStyle = BitavidaNodeStyle.Filled,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        DailyRecord(
            icon = R.drawable.ic_v2_droplet,
            label = stringResource(R.string.today_urination),
            value = stringResource(R.string.today_urination_value),
            state = stringResource(R.string.today_urination_state),
            nodeStyle = BitavidaNodeStyle.Filled,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        DailyRecord(
            icon = R.drawable.ic_v2_foot,
            label = stringResource(R.string.today_feet),
            value = stringResource(R.string.today_feet_value),
            state = stringResource(R.string.today_feet_state),
            nodeStyle = BitavidaNodeStyle.Open,
        )
    }
}

@Composable
private fun DailyRecord(
    icon: Int,
    label: String,
    value: String,
    state: String,
    nodeStyle: BitavidaNodeStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .padding(vertical = BitavidaSpacing.Small)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label. $value. $state"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        BitavidaIconContainer(
            painter = painterResource(icon),
            contentDescription = null,
            containerColor = MaterialTheme.bitavidaColors.mint,
            modifier = Modifier.size(42.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier.size(14.dp),
            shape = CircleShape,
            color = if (nodeStyle == BitavidaNodeStyle.Filled) {
                MaterialTheme.bitavidaColors.gradientStart
            } else {
                MaterialTheme.colorScheme.background
            },
            border = if (nodeStyle == BitavidaNodeStyle.Open) {
                androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.outline,
                )
            } else {
                null
            },
            content = {},
        )
    }
}

@Composable
private fun AppointmentEvent(
    expanded: Boolean,
    motionEnabledOverride: Boolean?,
    onToggle: () -> Unit,
) {
    val motionEnabled = rememberBitavidaMotionAllowed(motionEnabledOverride)
    val animatedModifier = if (motionEnabled) {
        Modifier.animateContentSize(animationSpec = tween(BitavidaFluidMotion.StateTransition))
    } else {
        Modifier
    }

    BitavidaConnectedSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("appointment_event"),
        containerColor = MaterialTheme.bitavidaColors.lavender,
        onClick = onToggle,
        motionEnabledOverride = motionEnabledOverride,
        contentModifier = animatedModifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "HOY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "16:30",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.today_appointment),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.today_appointment_status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_v2_chevron),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(BitavidaSpacing.Small))
        Text(
            text = stringResource(
                if (expanded) R.string.today_appointment_collapse
                else R.string.today_appointment_expand,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (expanded) {
            Spacer(Modifier.height(BitavidaSpacing.Small))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(BitavidaSpacing.Small))
            Text(
                text = stringResource(R.string.today_appointment_expanded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickJourneyActions(
    motionEnabledOverride: Boolean?,
    onUnavailableAction: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stack = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.5f
        val actions = listOf(
            Triple(
                stringResource(R.string.today_quick_consultation),
                R.drawable.ic_v2_stethoscope,
                false,
            ),
            Triple(
                stringResource(R.string.today_quick_medication),
                R.drawable.ic_v2_pill,
                false,
            ),
            Triple(
                stringResource(R.string.today_quick_glucose),
                R.drawable.ic_v2_droplet,
                false,
            ),
            Triple(
                stringResource(R.string.today_quick_note),
                R.drawable.ic_v2_clipboard,
                false,
            ),
        )

        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                actions.forEach { (label, icon, emphasized) ->
                    BitavidaThreadAction(
                        label = label,
                        iconRes = icon,
                        onClick = { onUnavailableAction(label) },
                        modifier = Modifier.fillMaxWidth(),
                        emphasized = emphasized,
                        motionEnabledOverride = motionEnabledOverride,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                actions.chunked(2).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    ) {
                        rowActions.forEach { (label, icon, emphasized) ->
                            BitavidaThreadAction(
                                label = label,
                                iconRes = icon,
                                onClick = { onUnavailableAction(label) },
                                modifier = Modifier.weight(1f),
                                emphasized = emphasized,
                                motionEnabledOverride = motionEnabledOverride,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyJourney(
    motionEnabledOverride: Boolean?,
    onUnavailableAction: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.today_empty_badge),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(BitavidaSpacing.Medium))

    val primary = stringResource(R.string.today_empty_primary)
    val secondary = stringResource(R.string.today_empty_secondary)
    BitavidaEmptyJourney(
        title = stringResource(R.string.today_empty_heading),
        body = stringResource(R.string.today_empty_body),
        primaryLabel = primary,
        secondaryLabel = secondary,
        onPrimary = { onUnavailableAction(primary) },
        onSecondary = { onUnavailableAction(secondary) },
        modifier = Modifier.testTag("empty_journey"),
        primaryModifier = Modifier.testTag("empty_primary_action"),
        motionEnabledOverride = motionEnabledOverride,
    )

    Spacer(Modifier.height(BitavidaSpacing.Default))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        BitavidaIconContainer(
            painter = painterResource(R.drawable.ic_v2_file),
            contentDescription = null,
            containerColor = MaterialTheme.bitavidaColors.paleBlue,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.today_empty_notice_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(BitavidaSpacing.ExtraSmall))
            Text(
                text = stringResource(R.string.today_empty_notice_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

}

@Preview(name = "Mi día 0.5 · Claro", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun TodayDemoLightPreview() {
    BitavidaTheme { TodayDemoScreen() }
}

@Preview(
    name = "Mi día 0.5 · Oscuro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun TodayDemoDarkPreview() {
    BitavidaTheme(darkTheme = true) { TodayDemoScreen() }
}

@Preview(name = "Mi día 0.5 · Compacto", showBackground = true, widthDp = 320, heightDp = 720)
@Composable
fun TodayDemoCompactPreview() {
    BitavidaTheme { TodayDemoScreen() }
}

@Preview(
    name = "Mi día 0.5 · Texto 200%",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
)
@Composable
fun TodayDemoLargeTextPreview() {
    BitavidaTheme { TodayDemoScreen() }
}

@Preview(name = "Mi día 0.5 · Vacío", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun TodayDemoEmptyPreview() {
    BitavidaTheme { TodayDemoScreen(state = TodayDemoState.Empty) }
}

@Preview(
    name = "Mi día 0.5 · Sin movimiento",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
fun TodayDemoReducedMotionPreview() {
    BitavidaTheme {
        TodayDemoScreen(motionEnabledOverride = false)
    }
}

@Preview(
    name = "Mi día 0.5 · Interactivo",
    showBackground = true,
    widthDp = 390,
    heightDp = 1_600,
)
@Composable
fun TodayDemoInteractivePreview() {
    BitavidaTheme {
        TodayDemoScreen(
            initialDemoConfirmed = true,
            initialAppointmentExpanded = true,
        )
    }
}
