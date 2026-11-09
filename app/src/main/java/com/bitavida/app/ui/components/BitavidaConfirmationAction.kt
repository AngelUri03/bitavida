package com.bitavida.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors

/**
 * One physical control that transforms between an available demo action and its stable,
 * non-persistent confirmation state.
 */
@Composable
fun BitavidaConfirmationAction(
    confirmed: Boolean,
    initialLabel: String,
    confirmedLabel: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    motionAllowed: Boolean,
) {
    val transition = updateTransition(targetState = confirmed, label = "demoConfirmation")
    val duration = if (motionAllowed) BitavidaFluidMotion.ConfirmationTransition else 0
    val confirmedContainer = MaterialTheme.bitavidaColors.paleBlue
    val initialContainer = MaterialTheme.colorScheme.primary
    val containerColor = transition.animateColor(
        transitionSpec = {
            tween(durationMillis = duration, easing = FastOutSlowInEasing)
        },
        label = "confirmationSurface",
    ) { done ->
        if (done) confirmedContainer else initialContainer
    }
    val shape = RoundedCornerShape(18.dp, 28.dp, 28.dp, 18.dp)
    val source = remember { MutableInteractionSource() }
    val response = Modifier.bitavidaFluidInteraction(
        source = source,
        enabled = !confirmed,
        motionAllowed = motionAllowed,
        shape = shape,
    )

    Surface(
        modifier = modifier
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val backgroundPath = when (outline) {
                    is Outline.Generic -> outline.path
                    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                }
                onDrawBehind {
                    val resolvedColor = if (!motionAllowed && confirmed) confirmedContainer else if (!motionAllowed) initialContainer else containerColor.value
                    drawPath(path = backgroundPath, color = resolvedColor)
                }
            }
            .then(response)
            .heightIn(min = 64.dp)
            .semantics {
                if (confirmed) disabled()
            }
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(),
                enabled = !confirmed,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (confirmed) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.primary,
        ),
    ) {
        if (!motionAllowed) {
            ConfirmationActionContent(
                done = confirmed,
                initialLabel = initialLabel,
                confirmedLabel = confirmedLabel,
                iconRes = iconRes,
                motionAllowed = false,
            )
        } else {
            transition.AnimatedContent(
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    (
                        fadeIn(tween(durationMillis = duration, delayMillis = 70)) +
                            slideInVertically(
                                animationSpec = tween(
                                    durationMillis = duration,
                                    easing = FastOutSlowInEasing,
                                ),
                            ) { fullHeight -> fullHeight / 10 }
                        ) togetherWith (
                        fadeOut(tween(durationMillis = duration / 2)) +
                            slideOutVertically(
                                animationSpec = tween(
                                    durationMillis = duration / 2,
                                    easing = FastOutSlowInEasing,
                                ),
                            ) { fullHeight -> -fullHeight / 10 }
                        ) using SizeTransform(
                        clip = false,
                        sizeAnimationSpec = { _, _ ->
                            tween(durationMillis = duration, easing = FastOutSlowInEasing)
                        },
                    )
                },
                contentKey = { it },
            ) { done ->
                ConfirmationActionContent(
                    done = done,
                    initialLabel = initialLabel,
                    confirmedLabel = confirmedLabel,
                    iconRes = iconRes,
                    motionAllowed = true,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationActionContent(
    done: Boolean,
    initialLabel: String,
    confirmedLabel: String,
    @DrawableRes iconRes: Int,
    motionAllowed: Boolean,
) {
    val foreground = if (done) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    Row(
        modifier = Modifier.padding(
            horizontal = BitavidaSpacing.Medium,
            vertical = BitavidaSpacing.Small,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = if (done) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                },
                content = {},
            )
            if (done) {
                val checkColor = MaterialTheme.colorScheme.primary
                Canvas(Modifier.size(21.dp).graphicsLayer {
                    translationY = if (motionAllowed) -1.dp.toPx() else 0f
                }) {
                    drawLine(
                        color = checkColor,
                        start = Offset(size.width * 0.18f, size.height * 0.54f),
                        end = Offset(size.width * 0.42f, size.height * 0.76f),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    drawLine(
                        color = checkColor,
                        start = Offset(size.width * 0.42f, size.height * 0.76f),
                        end = Offset(size.width * 0.84f, size.height * 0.24f),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            } else {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    colorFilter = ColorFilter.tint(foreground),
                )
            }
        }
        Text(
            text = if (done) confirmedLabel else initialLabel,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
        )
    }
}
