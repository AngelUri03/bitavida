package com.bitavida.app.ui.measurements

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaConnectedSurface
import com.bitavida.app.ui.components.BitavidaIconContainer
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors

/** Same surfaces, shape tokens and interaction primitive used by the approved detail. */
@Composable
internal fun MeasurementPanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    borderAlpha: Float = 1f,
    contentPadding: androidx.compose.ui.unit.Dp = BitavidaSpacing.Default,
    contentSpacing: androidx.compose.ui.unit.Dp = BitavidaSpacing.Small,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val source = remember { MutableInteractionSource() }
    val motion = rememberBitavidaMotionAllowed(null)
    val interaction = if (onClick == null) Modifier else Modifier
        .bitavidaFluidInteraction(source, true, motion, shape = shape, restingElevationDp = 0f)
        .clickable(source, androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
    Surface(
        modifier = modifier.fillMaxWidth().then(interaction),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content,
        )
    }
}

internal enum class MeasurementButtonStyle { FILLED, OUTLINED, TEXT }

/** The approved medication button is file-private; reuse its public theme and motion primitives. */
@Composable
internal fun MeasurementButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MeasurementButtonStyle = MeasurementButtonStyle.FILLED,
    enabled: Boolean = true,
    icon: Int? = null,
    accessibleLabel: String = label,
) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    val motion = rememberBitavidaMotionAllowed(null)
    val response = modifier
        .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
        .semantics { contentDescription = accessibleLabel }
        .bitavidaFluidInteraction(source, enabled, motion, shape = shape, restingElevationDp = 0f)
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        icon?.let {
            MeasurementIcon(it, 20, LocalContentColor.current)
            Spacer(Modifier.width(BitavidaSpacing.Small))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
    when (style) {
        MeasurementButtonStyle.FILLED -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = response,
            interactionSource = source,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            content = content,
        )
        MeasurementButtonStyle.OUTLINED -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = response,
            interactionSource = source,
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            content = content,
        )
        MeasurementButtonStyle.TEXT -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = response,
            interactionSource = source,
            shape = shape,
            content = content,
        )
    }
}

@Composable
internal fun SectionTitle(text: String, small: Boolean = false) {
    Text(text, style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun InformationCard(title: String, body: String) {
    BitavidaConnectedSurface(containerColor = MaterialTheme.colorScheme.surface) {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            SectionTitle(title, small = true)
            SecondaryText(body)
        }
    }
}

@Composable
internal fun ActionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
}

@Composable
internal fun MeasurementIcon(
    @DrawableRes resource: Int,
    size: Int = 24,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = Modifier.size(size.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
internal fun MeasurementBadge(kind: MeasurementKind) {
    BitavidaIconContainer(
        painterResource(kind.icon()), null,
        containerColor = MaterialTheme.bitavidaColors.paleBlue,
    )
}

@Composable
internal fun MeasurementAction(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val motion = rememberBitavidaMotionAllowed(null)
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .bitavidaFluidInteraction(source, true, motion, shape = MaterialTheme.shapes.medium, restingElevationDp = 0f)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = BitavidaSpacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeasurementIcon(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            SecondaryText(description)
        }
        MeasurementIcon(R.drawable.ic_v2_chevron, 18)
    }
}
