package com.bitavida.app.ui.education

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.education.EducationGroup
import com.bitavida.app.domain.education.EducationTopic
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.measurements.GlucoseCaptureChip
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors

@Composable
internal fun AiPanel(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = tint,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
        shadowElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(BitavidaSpacing.Default),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            content = content,
        )
    }
}

@Composable
internal fun AiHero(
    subtitle: String = "Tu asistente inteligente para aprender a cuidar",
    deviceIndicator: String = "IA en tu dispositivo · Sin Internet",
    central: Boolean = false,
) {
    val colors = MaterialTheme.bitavidaColors
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 1.dp,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(colors.paleBlue, MaterialTheme.colorScheme.surface,
                    if (central) colors.mint else colors.lavender)))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val largeText = LocalDensity.current.fontScale >= 1.5f
            val title: @Composable () -> Unit = {
                Text("Bitavida IA", style = if (central) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            }
            if (largeText) {
                AiIcon(R.drawable.ic_bitavida_ai, Modifier.size(28.dp))
                title()
            } else Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) { AiIcon(R.drawable.ic_bitavida_ai, Modifier.size(27.dp)) }
                title()
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface)
            Text(deviceIndicator, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun AiEyebrow(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun AiNavigationRow(
    title: String,
    subtitle: String,
    @DrawableRes icon: Int,
    motion: Boolean,
    onClick: () -> Unit,
) = AiNavigationRow(title, subtitle, icon, motion, Modifier, onClick)

/** Same navigation surface, with optional supporting text for compact libraries. */
@Composable
internal fun AiNavigationRow(
    title: String,
    subtitle: String?,
    @DrawableRes icon: Int,
    motion: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .bitavidaFluidInteraction(source, true, motion, shape = shape, restingElevationDp = 1f)
            .clickable(source, androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick),
        shape = shape, color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AiIcon(icon, Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AiIcon(R.drawable.ic_v2_chevron, Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun AiSectionHeading(text: String, small: Boolean = false) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
internal fun AiIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Image(
        painterResource(icon), null, modifier.then(Modifier.size(22.dp)),
        colorFilter = ColorFilter.tint(tint),
    )
}

internal fun EducationGroup.icon(): Int = when (this) {
    EducationGroup.GLUCOSE -> R.drawable.ic_v2_droplet
    EducationGroup.PRESSURE -> R.drawable.ic_measurement_pressure
    EducationGroup.WEIGHT -> R.drawable.ic_measurement_weight
    EducationGroup.MEASUREMENTS -> R.drawable.ic_v2_clipboard
    EducationGroup.MEDICATIONS -> R.drawable.ic_v2_pill
    EducationGroup.OBSERVATIONS -> R.drawable.ic_v2_stethoscope
    EducationGroup.REMINDERS -> R.drawable.ic_v2_clock
}

@Composable
internal fun AiGroupFilters(
    selectedGroup: EducationGroup?,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
    includeAll: Boolean = false,
) {
    val groups = remember(includeAll) {
        listOf<EducationGroup?>(null) + EducationGroup.entries.filter {
            includeAll || (it != EducationGroup.MEDICATIONS && it != EducationGroup.OBSERVATIONS && it != EducationGroup.REMINDERS)
        }
    }
    val selectedIndex = groups.indexOf(selectedGroup).coerceAtLeast(0)
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewportWidth = maxWidth
        LaunchedEffect(selectedIndex, motion, viewportWidth, fontScale) {
            if (motion) scroll.animateScrollToItem(selectedIndex) else scroll.scrollToItem(selectedIndex)
        }
        LazyRow(
            state = scroll,
            modifier = Modifier.fillMaxWidth().selectableGroup().testTag("ai_library_filters"),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(groups, key = { it?.name ?: "ALL" }) { group ->
                GlucoseCaptureChip(
                    label = group?.label ?: "Todo",
                    selected = selectedGroup == group,
                    enabled = true,
                    motionAllowed = motion,
                    onClick = { onAction(BitavidaAiAction.SelectGroup(group)) },
                    modifier = Modifier.testTag("ai_library_filter_${group?.name ?: "ALL"}"),
                )
            }
        }
    }
}

@Composable
internal fun AiQuestionGrid(
    topics: List<EducationTopic>,
    onAction: (BitavidaAiAction) -> Unit,
    motion: Boolean,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        val columns = if (maxWidth >= 340.dp && fontScale < 1.3f) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            topics.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    row.forEach { topic ->
                        AiQuestionTile(topic, motion, Modifier.weight(1f)) {
                            onAction(BitavidaAiAction.Ask(topic.question))
                        }
                    }
                    if (row.size < columns) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AiQuestionTile(topic: EducationTopic, motion: Boolean, modifier: Modifier, onClick: () -> Unit) =
    AiQuestionCard(topic.question, topic.group.icon(), motion, modifier, onClick)

@Composable
internal fun AiQuestionCard(question: String, @DrawableRes icon: Int, motion: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier.heightIn(min = 72.dp)
            .bitavidaFluidInteraction(source, true, motion, shape = shape, restingElevationDp = 1f)
            .clickable(source, androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.padding(BitavidaSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            AiIcon(icon, Modifier.size(20.dp))
            Text(
                question,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun AiDisclosure(
    title: String,
    subtitle: String,
    @DrawableRes icon: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    motion: Boolean,
) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" }
            .bitavidaFluidInteraction(source, true, motion, shape = shape, restingElevationDp = 0f)
            .clickable(source, androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(vertical = BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        AiIcon(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AiIcon(
            R.drawable.ic_v2_chevron,
            Modifier.size(18.dp).graphicsLayer { rotationZ = if (expanded) 90f else 0f },
        )
    }
}

@Composable
internal fun AiLibraryRow(topic: EducationTopic, motion: Boolean, onClick: () -> Unit) {
    AiLibraryRow(topic.question, motion, onClick = onClick)
}

@Composable
internal fun AiLibraryRow(title: String, motion: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .bitavidaFluidInteraction(source, true, motion, shape = MaterialTheme.shapes.medium, restingElevationDp = 0f)
            .clickable(source, androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(vertical = BitavidaSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        AiIcon(R.drawable.ic_v2_chevron, Modifier.size(16.dp))
    }
}

@Composable
internal fun AiExplanationStep(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        Text(
            number, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
