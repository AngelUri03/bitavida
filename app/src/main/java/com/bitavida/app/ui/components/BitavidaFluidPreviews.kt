package com.bitavida.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.theme.BitavidaTheme

@Preview(name = "0.5 · Presionado", widthDp = 320)
@Composable
fun FluidPressedPreview() {
    BitavidaTheme {
        BitavidaThreadAction(
            label = "Ver contexto de demostración", iconRes = R.drawable.ic_v2_chevron,
            onClick = {}, emphasized = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            motionEnabledOverride = false, pressedOverride = true,
        )
    }
}

@Preview(name = "0.5 · Después de soltar", widthDp = 320)
@Composable
fun FluidReleasedPreview() {
    BitavidaTheme {
        BitavidaThreadAction(
            label = "Ver contexto de demostración", iconRes = R.drawable.ic_v2_chevron,
            onClick = {}, emphasized = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            motionEnabledOverride = false, pressedOverride = false,
        )
    }
}
