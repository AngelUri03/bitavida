package com.bitavida.app.ui.brand

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.theme.BitavidaTheme

@Preview(name = "Marca · Claro", showBackground = true, widthDp = 180, heightDp = 180)
@Composable
fun BitavidaMarkLightPreview() {
    BitavidaTheme {
        MarkPreviewSurface {
            BitavidaMark(modifier = Modifier.size(120.dp))
        }
    }
}

@Preview(
    name = "Marca · Oscuro",
    showBackground = true,
    widthDp = 180,
    heightDp = 180,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun BitavidaMarkDarkPreview() {
    BitavidaTheme(darkTheme = true) {
        MarkPreviewSurface {
            BitavidaMark(modifier = Modifier.size(120.dp))
        }
    }
}

@Preview(name = "Marca · Pequeña", showBackground = true, widthDp = 72, heightDp = 72)
@Composable
fun BitavidaMarkSmallPreview() {
    BitavidaTheme {
        MarkPreviewSurface {
            BitavidaMark(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                eventRadius = 2.5.dp,
            )
        }
    }
}

@Preview(name = "Marca · Animada", showBackground = true, widthDp = 180, heightDp = 180)
@Composable
fun AnimatedBitavidaMarkPreview() {
    BitavidaTheme {
        MarkPreviewSurface {
            // Preview renders the stable end state; Android Studio Interactive Mode
            // exercises the real one-shot animation without leaving a blank first frame.
            AnimatedBitavidaMark(modifier = Modifier.size(120.dp))
        }
    }
}

@Composable
private fun MarkPreviewSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
