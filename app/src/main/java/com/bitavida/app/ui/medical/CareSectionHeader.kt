package com.bitavida.app.ui.medical

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.bitavida.app.ui.components.BitavidaIconContainer
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors

/** Shared presentation for the two care section entry points; navigation stays with each host. */
@Composable
internal fun CareSectionHeader(
    title: String,
    person: String,
    @DrawableRes iconRes: Int,
    onBack: () -> Unit,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backEnabled: Boolean = true,
    iconScale: Float = 1f,
    backLabel: String = "Plan de cuidado",
    emphasizeSupportingText: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        CurrentMedicationBack(
            onBack = onBack,
            motionAllowed = motionAllowed,
            enabled = backEnabled,
            label = backLabel,
        )
        val identity: @Composable ColumnScope.() -> Unit = {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = if (emphasizeSupportingText) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Acompañando a ${person.trim().substringBefore(' ')}",
                style = if (emphasizeSupportingText) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val icon: @Composable () -> Unit = {
            BitavidaIconContainer(
                painter = painterResource(iconRes),
                contentDescription = null,
                containerColor = MaterialTheme.bitavidaColors.paleBlue,
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                    alpha = iconScale
                },
            )
        }
        if (LocalDensity.current.fontScale >= 1.5f) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                icon()
                Column(
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                    content = identity,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                    content = identity,
                )
            }
        }
    }
}
