package com.bitavida.app.ui.access

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.access.LocalAuthenticationAvailability
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.theme.BitavidaSpacing

@Composable
fun AppProtectionPreference(
    checked: Boolean,
    availability: LocalAuthenticationAvailability,
    onCheckedChange: (Boolean) -> Unit,
    onOpenSecuritySetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = availability == LocalAuthenticationAvailability.AVAILABLE
    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(BitavidaFluidMotion.StateTransition),
        label = "app_protection_preference_color",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(BitavidaSpacing.Default),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                ) {
                    Text(
                        text = "Proteger Bitavida",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Usa el bloqueo de tu teléfono para proteger el acceso a Bitavida.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = checked || available,
                    modifier = Modifier.semantics {
                        contentDescription = "Proteger Bitavida"
                    },
                )
            }

            if (!available) {
                Text(
                    text = when (availability) {
                        LocalAuthenticationAvailability.CHECKING ->
                            "Comprobando el bloqueo seguro del teléfono…"
                        LocalAuthenticationAvailability.SETUP_REQUIRED ->
                            "Configura un PIN, patrón o contraseña en Android antes de activarlo."
                        LocalAuthenticationAvailability.TEMPORARILY_UNAVAILABLE ->
                            "El bloqueo seguro no está disponible por el momento."
                        LocalAuthenticationAvailability.UNSUPPORTED ->
                            "Este teléfono no ofrece un bloqueo compatible."
                        LocalAuthenticationAvailability.AVAILABLE -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (availability == LocalAuthenticationAvailability.SETUP_REQUIRED) {
                    TextButton(
                        onClick = onOpenSecuritySetup,
                        modifier = Modifier.heightIn(
                            min = BitavidaSpacing.MinimumTouchTarget,
                        ),
                    ) {
                        Text("Configurar en Android")
                    }
                }
            }
        }
    }
}
