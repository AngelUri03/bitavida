package com.bitavida.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.theme.bitavidaColors

/**
 * Bottom navigation for the agenda. Destination availability is supplied by the host so that
 * visual tabs never invent a destination. Labels and the selected state remain accessible.
 */
@Composable
fun TodayAgendaNavigation(
    items: List<BitavidaNavigationItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: (Int) -> Boolean = { true },
    motionEnabledOverride: Boolean? = null,
) {
    if (items.isEmpty()) return
    val colors = MaterialTheme.bitavidaColors
    val outline = MaterialTheme.colorScheme.outline
    val motionAllowed = rememberBitavidaMotionAllowed(motionEnabledOverride)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.navigationSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 5.dp,
    ) {
        Box(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                val segment = size.width / items.size
                for (index in 0 until items.lastIndex) {
                    val start = segment * (index + 0.5f) + 25.dp.toPx()
                    val end = segment * (index + 1.5f) - 25.dp.toPx()
                    if (end > start) drawLine(outline, Offset(start, 24.dp.toPx()),
                        Offset(end, 24.dp.toPx()), 1.5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val enabled = isEnabled(index)
                    val source = remember { MutableInteractionSource() }
                    val contentColor = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .clickable(enabled = enabled, role = Role.Tab, interactionSource = source,
                                indication = null) { onSelected(index) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = item.label
                                this.selected = selected
                            }.padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp)
                                .bitavidaFluidInteraction(source, enabled, motionAllowed,
                                    shape = CircleShape, restingElevationDp = 0f)
                                .clip(CircleShape)
                                .background(if (selected) colors.paleBlue else colors.navigationSurface, CircleShape)
                                .indication(source, androidx.compose.material3.ripple(
                                    color = MaterialTheme.colorScheme.primary, radius = 24.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(item.iconRes), contentDescription = null,
                                modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(contentColor))
                        }
                        Text(item.label, color = contentColor, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
            }
        }
    }
}
