package com.bitavida.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors

data class BitavidaNavigationItem(
    val label: String,
    @param:DrawableRes val iconRes: Int,
)

@Composable
fun BitavidaStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean = false,
) {
    val colors = MaterialTheme.bitavidaColors
    val container = if (isPositive) colors.mint else colors.roseStrong
    val content = if (isPositive) colors.successText else colors.pendingText
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = BitavidaSpacing.Medium, vertical = BitavidaSpacing.Small),
    ) {
        Text(text = text, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun BitavidaIconContainer(
    painter: Painter,
    contentDescription: String?,
    containerColor: Color,
    modifier: Modifier = Modifier,
    tint: Color? = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            colorFilter = tint?.let(ColorFilter::tint),
        )
    }
}
