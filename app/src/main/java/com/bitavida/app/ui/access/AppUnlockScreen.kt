package com.bitavida.app.ui.access

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.access.AppAccessSessionState
import com.bitavida.app.domain.access.LocalAuthenticationAvailability
import com.bitavida.app.ui.brand.AnimatedBitavidaMark
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors

@Composable
fun AppUnlockScreen(
    state: AppAccessUiState,
    onUnlock: () -> Unit,
    onOpenSecuritySetup: () -> Unit,
    modifier: Modifier = Modifier,
    motionEnabledOverride: Boolean? = null,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(motionEnabledOverride)
    val entrance = remember { Animatable(if (motionAllowed) 0f else 1f) }

    LaunchedEffect(motionAllowed) {
        if (!motionAllowed) {
            entrance.snapTo(1f)
        } else if (entrance.value < 1f) {
            entrance.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    BitavidaFluidBackground(
        modifier = modifier.fillMaxSize(),
        motionAllowed = motionAllowed,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    horizontal = BitavidaSpacing.ScreenHorizontal,
                    vertical = BitavidaSpacing.Default,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = entrance.value
                        translationY = (1f - entrance.value) * 22.dp.toPx()
                    },
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = BitavidaSpacing.Large,
                            vertical = BitavidaSpacing.ExtraLarge,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
                ) {
                    AnimatedBitavidaMark(
                        modifier = Modifier.size(92.dp),
                        motionEnabledOverride = motionAllowed,
                    )
                    Text(
                        text = "Bitavida",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.bitavidaColors.brand,
                    )
                    Text(
                        text = "Tu información, protegida",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Confirma tu identidad con el bloqueo seguro de este dispositivo. " +
                            "Bitavida no guarda tu PIN, patrón, contraseña ni biometría.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    UnlockLifeThread(
                        accessGranted =
                            state.sessionState == AppAccessSessionState.UNLOCKED,
                        motionAllowed = motionAllowed,
                    )

                    AccessStatus(
                        state = state,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val setupRequired =
                        state.availability == LocalAuthenticationAvailability.SETUP_REQUIRED
                    Button(
                        onClick = if (setupRequired) onOpenSecuritySetup else onUnlock,
                        enabled =
                            state.sessionState != AppAccessSessionState.AUTHENTICATING &&
                                state.sessionState != AppAccessSessionState.UNLOCKED &&
                                state.availability !=
                                LocalAuthenticationAvailability.CHECKING,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = BitavidaSpacing.PrimaryControlHeight),
                        contentPadding = PaddingValues(
                            horizontal = BitavidaSpacing.Large,
                            vertical = BitavidaSpacing.Medium,
                        ),
                    ) {
                        Text(
                            text = when {
                                setupRequired -> "Configurar bloqueo en Android"
                                state.sessionState == AppAccessSessionState.AUTHENTICATING ->
                                    "Esperando confirmación"
                                else -> "Desbloquear"
                            },
                            textAlign = TextAlign.Center,
                        )
                    }

                    Text(
                        text = "PIN, patrón, contraseña o biometría fuerte compatible. " +
                            "Usa Atrás para bloquear Bitavida al terminar.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessStatus(
    state: AppAccessUiState,
    modifier: Modifier = Modifier,
) {
    val message = when {
        state.sessionState == AppAccessSessionState.UNLOCKED ->
            "Acceso concedido. Abriendo tu espacio…"
        state.sessionState == AppAccessSessionState.AUTHENTICATING ->
            "Verifica tu identidad en el diálogo seguro de Android."
        state.availability == LocalAuthenticationAvailability.CHECKING ->
            "Comprobando la seguridad del dispositivo…"
        state.notice == AppAccessNotice.AUTHENTICATION_CANCELLED ->
            "La verificación se canceló. Bitavida sigue bloqueada."
        state.notice == AppAccessNotice.AUTHENTICATION_FAILED ->
            "No se pudo verificar tu identidad. Inténtalo de nuevo."
        state.notice == AppAccessNotice.AUTHENTICATION_LOCKED ->
            "Android bloqueó temporalmente los intentos. Usa la credencial del dispositivo."
        state.notice == AppAccessNotice.AUTHENTICATOR_TEMPORARILY_UNAVAILABLE ->
            "El acceso seguro no está disponible por el momento. Inténtalo de nuevo."
        state.notice == AppAccessNotice.AUTHENTICATOR_UNSUPPORTED ->
            "Este dispositivo no ofrece un método de acceso compatible."
        state.notice == AppAccessNotice.DEVICE_SECURITY_SETUP_REQUIRED ->
            "Configura un PIN, patrón o contraseña en Android para proteger Bitavida."
        state.notice == AppAccessNotice.SESSION_EXPIRED ->
            "Bitavida se bloqueó después de permanecer en segundo plano."
        else -> "Bitavida está bloqueada."
    }
    val isError = state.notice != null &&
        state.sessionState != AppAccessSessionState.UNLOCKED

    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Default,
                vertical = BitavidaSpacing.Medium,
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UnlockLifeThread(
    accessGranted: Boolean,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
) {
    val pointProgress = remember { Animatable(0f) }
    LaunchedEffect(accessGranted, motionAllowed) {
        when {
            !accessGranted -> pointProgress.snapTo(0f)
            !motionAllowed -> pointProgress.snapTo(1f)
            else -> {
                pointProgress.snapTo(0f)
                pointProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 380,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }

    val threadColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.bitavidaColors.gradientStart
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
    ) {
        val path = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.64f)
            cubicTo(
                size.width * 0.34f,
                size.height * 0.10f,
                size.width * 0.62f,
                size.height * 0.92f,
                size.width * 0.92f,
                size.height * 0.36f,
            )
        }
        drawPath(
            path = path,
            color = threadColor.copy(alpha = 0.34f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        if (accessGranted) {
            val measure = PathMeasure().apply { setPath(path, false) }
            val position: Offset = measure.getPosition(
                measure.length * pointProgress.value.coerceIn(0f, 1f),
            )
            drawCircle(
                color = pointColor.copy(alpha = 0.22f),
                radius = 9.dp.toPx(),
                center = position,
            )
            drawCircle(
                color = pointColor,
                radius = 4.dp.toPx(),
                center = position,
            )
        }
    }
}

private val PreviewLockedState = AppAccessUiState(
    availability = LocalAuthenticationAvailability.AVAILABLE,
)

@Preview(showBackground = true)
@Composable
private fun AppUnlockLightPreview() {
    BitavidaTheme {
        AppUnlockScreen(
            state = PreviewLockedState,
            onUnlock = {},
            onOpenSecuritySetup = {},
            motionEnabledOverride = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppUnlockDarkPreview() {
    BitavidaTheme(darkTheme = true) {
        AppUnlockScreen(
            state = PreviewLockedState,
            onUnlock = {},
            onOpenSecuritySetup = {},
            motionEnabledOverride = false,
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun AppUnlockCompactPreview() {
    BitavidaTheme {
        AppUnlockScreen(
            state = PreviewLockedState,
            onUnlock = {},
            onOpenSecuritySetup = {},
            motionEnabledOverride = false,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760, fontScale = 2f)
@Composable
private fun AppUnlockLargeTextPreview() {
    BitavidaTheme {
        AppUnlockScreen(
            state = PreviewLockedState.copy(
                availability = LocalAuthenticationAvailability.SETUP_REQUIRED,
                notice = AppAccessNotice.DEVICE_SECURITY_SETUP_REQUIRED,
            ),
            onUnlock = {},
            onOpenSecuritySetup = {},
            motionEnabledOverride = false,
        )
    }
}
