package com.bitavida.app.ui.demo

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bitavida.app.R
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.theme.bitavidaColors

/** A single interactive surface; the ambient accent runs only while visible and foregrounded. */
@Composable
internal fun TodayAiCard(listState: LazyListState, motion: Boolean, onClick: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val visible by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "bitavida_ai" } }
    }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    val colors = MaterialTheme.bitavidaColors
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .bitavidaFluidInteraction(interaction, true, motion, shape = shape, restingElevationDp = 2f)
            .clickable(interaction, ripple(), role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.background(Brush.linearGradient(listOf(
                colors.paleBlue, MaterialTheme.colorScheme.surface, colors.mint,
            ))).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AiAccent(active = motion && visible && foreground)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Bitavida IA", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Tu asistente inteligente para aprender a cuidar",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Funciona sin Internet", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            MeasurementIcon(R.drawable.ic_v2_chevron, 20, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AiAccent(active: Boolean) {
    val phase = if (active) {
        val transition = rememberInfiniteTransition(label = "identidad IA")
        val value by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
            label = "brillo IA",
        )
        value
    } else 0f
    Box(
        Modifier.size(48.dp).graphicsLayer {
            translationY = -2.dp.toPx() * phase
            scaleX = 1f + 0.025f * phase
            scaleY = 1f + 0.025f * phase
        }.clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        MeasurementIcon(R.drawable.ic_bitavida_ai, 27, MaterialTheme.colorScheme.primary)
    }
}
