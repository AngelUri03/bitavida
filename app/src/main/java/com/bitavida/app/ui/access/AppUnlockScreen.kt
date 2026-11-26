package com.bitavida.app.ui.access

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.access.AppAccessSessionState
import com.bitavida.app.domain.access.LocalAuthenticationAvailability
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme

@Composable
fun AppUnlockScreen(
    state: AppAccessUiState,
    onUnlock: () -> Unit,
    onOpenSecuritySetup: () -> Unit,
    modifier: Modifier = Modifier,
    motionEnabledOverride: Boolean? = null,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(motionEnabledOverride)
    var entered by remember { mutableStateOf(!motionAllowed) }
    LaunchedEffect(motionAllowed) { entered = true }
    val markAlpha by animateFloatAsState(
        targetValue = if (entered || !motionAllowed) 1f else 0f,
        animationSpec = tween(if (motionAllowed) 180 else 0),
        label = "unlock brand entrance",
    )
    val setupRequired =
        state.availability == LocalAuthenticationAvailability.SETUP_REQUIRED
    val authenticating =
        state.sessionState == AppAccessSessionState.AUTHENTICATING

    BitavidaFluidBackground(
        modifier = modifier.fillMaxSize(),
        motionAllowed = motionAllowed,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(BitavidaSpacing.ScreenHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bitavida_mark),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).graphicsLayer { alpha = markAlpha },
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Acceso protegido",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = accessMessage(state),
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                if (!authenticating) {
                    Button(
                        onClick = if (setupRequired) onOpenSecuritySetup else onUnlock,
                        enabled =
                            state.availability !=
                                LocalAuthenticationAvailability.CHECKING,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = BitavidaSpacing.PrimaryControlHeight),
                    ) {
                        Text(
                            if (setupRequired) {
                                "Configurar bloqueo"
                            } else {
                                "Reintentar"
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun accessMessage(state: AppAccessUiState): String = when {
    state.sessionState == AppAccessSessionState.AUTHENTICATING ->
        "Confirma tu identidad en Android."
    state.availability == LocalAuthenticationAvailability.CHECKING ->
        "Comprobando el bloqueo seguro del teléfono…"
    state.notice == AppAccessNotice.AUTHENTICATION_CANCELLED ->
        "La verificación se canceló. Puedes intentarlo de nuevo."
    state.notice == AppAccessNotice.AUTHENTICATION_FAILED ->
        "No se pudo verificar tu identidad. Inténtalo de nuevo."
    state.notice == AppAccessNotice.AUTHENTICATION_LOCKED ->
        "Android bloqueó temporalmente los intentos."
    state.notice == AppAccessNotice.AUTHENTICATOR_TEMPORARILY_UNAVAILABLE ->
        "El bloqueo seguro no está disponible por el momento."
    state.notice == AppAccessNotice.AUTHENTICATOR_UNSUPPORTED ->
        "Este teléfono no ofrece un bloqueo compatible."
    state.notice == AppAccessNotice.DEVICE_SECURITY_SETUP_REQUIRED ->
        "Configura un PIN, patrón o contraseña en Android."
    state.notice == AppAccessNotice.SESSION_EXPIRED ->
        "La sesión se protegió por inactividad."
    else -> "Confirma tu identidad para continuar."
}

private val PreviewLockedState = AppAccessUiState(
    sessionState = AppAccessSessionState.LOCKED,
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
            state = PreviewLockedState.copy(
                notice = AppAccessNotice.AUTHENTICATION_CANCELLED,
            ),
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
