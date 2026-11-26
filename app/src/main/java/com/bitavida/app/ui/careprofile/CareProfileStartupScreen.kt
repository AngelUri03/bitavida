package com.bitavida.app.ui.careprofile

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme

@Composable
fun CareProfileStartupScreen(
    state: CareProfileStartupState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = (state as? CareProfileStartupState.Error)?.reason
    BitavidaFluidBackground(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(BitavidaSpacing.ScreenHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bitavida_mark),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (error == null) {
                        "Preparando Bitavida…"
                    } else {
                        "No pudimos abrir tu perfil"
                    },
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = when (error) {
                        null ->
                            "Estamos comprobando el almacenamiento protegido."
                        CareProfileStartupError.KEY_UNAVAILABLE ->
                            "No se pudo acceder a la clave local. Tus datos no se borraron."
                        CareProfileStartupError.INCOMPLETE_STORAGE ->
                            "El perfil cifrado está incompleto. No se modificó ni se recreó."
                        CareProfileStartupError.STORAGE_UNAVAILABLE ->
                            "El almacenamiento cifrado no está disponible. Tus datos no se borraron."
                    },
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (error != null) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = BitavidaSpacing.PrimaryControlHeight),
                    ) {
                        Text("Reintentar")
                    }
                }
            }
        }
    }
}

@Composable
fun CareProfileRestrictionDialog(
    restrictions: Set<CareProfileRestriction>,
    onDismiss: () -> Unit,
) {
    if (restrictions.isEmpty()) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hay decisiones pendientes") },
        text = {
            Text(
                "El perfil permanece disponible, pero las operaciones que requieran " +
                    "autorización o aceptación vigente seguirán deshabilitadas. " +
                    "No se habilitó captura clínica.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(
                    min = BitavidaSpacing.MinimumTouchTarget,
                ),
            ) {
                Text("Entendido")
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun CareProfileStartupLightPreview() {
    BitavidaTheme {
        CareProfileStartupScreen(
            state = CareProfileStartupState.Loading,
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CareProfileStartupDarkPreview() {
    BitavidaTheme(darkTheme = true) {
        CareProfileStartupScreen(
            state = CareProfileStartupState.Error(
                CareProfileStartupError.STORAGE_UNAVAILABLE,
            ),
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun CareProfileStartupCompactPreview() {
    BitavidaTheme {
        CareProfileStartupScreen(
            state = CareProfileStartupState.Error(
                CareProfileStartupError.KEY_UNAVAILABLE,
            ),
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760, fontScale = 2f)
@Composable
private fun CareProfileStartupLargeTextPreview() {
    BitavidaTheme {
        CareProfileStartupScreen(
            state = CareProfileStartupState.Error(
                CareProfileStartupError.INCOMPLETE_STORAGE,
            ),
            onRetry = {},
        )
    }
}
