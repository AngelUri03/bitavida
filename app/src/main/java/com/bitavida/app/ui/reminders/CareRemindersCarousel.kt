package com.bitavida.app.ui.reminders

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.usecase.careRemindersForTodayCarousel
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaConnectedSurface
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaIconContainer
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDateTime

/** Kept below the medical journey; errands never participate in its clinical activity projection. */
@Composable
fun CareRemindersCarousel(
    records: List<CareReminder>,
    now: LocalDateTime,
    onOpen: (String) -> Unit,
    onAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = careRemindersForTodayCarousel(records, now)
    if (active.isEmpty()) return
    val motion = rememberBitavidaMotionAllowed(null)
    val duration = if (motion) BitavidaFluidMotion.StateTransition else 0
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Column(
        modifier = modifier.fillMaxWidth().testTag("care_reminders_carousel")
            .animateContentSize(tween(duration)),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        if (largeText) {
            Text("Recordatorios del cuidado", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            TextButton(onClick = onAll, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ver todos") }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                Text("Recordatorios del cuidado", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = onAll, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ver todos") }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = if (largeText) maxWidth else (maxWidth * 0.86f).coerceAtMost(340.dp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                contentPadding = PaddingValues(vertical = 2.dp)) {
                items(active, key = { it.id }) { reminder ->
                    BitavidaConnectedSurface(
                        modifier = Modifier.width(cardWidth).testTag("care_carousel_" + reminder.id)
                            .animateItem(
                                fadeInSpec = tween(duration), placementSpec = tween(duration),
                                fadeOutSpec = tween(duration)),
                        containerColor = MaterialTheme.colorScheme.surface,
                        onClick = { onOpen(reminder.id) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                            BitavidaIconContainer(
                                painter = painterResource(R.drawable.ic_v2_clock), contentDescription = null,
                                containerColor = MaterialTheme.bitavidaColors.paleBlue,
                            )
                            Text(reminder.title, style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(BitavidaSpacing.Small))
                        Text(reminderDateTimeLabel(reminder.scheduledAt),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(when {
                            reminder.hasPastSchedule(now) -> "Pendiente · Horario pasado"
                            reminder.scheduledAt.toLocalDate() == now.toLocalDate() -> "Pendiente · Para hoy"
                            else -> "Pendiente · Próximo"
                        },
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Preview(name = "Carrusel claro")
@Preview(name = "Carrusel oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Carrusel compacto", widthDp = 320)
@Preview(name = "Carrusel texto 200 %", widthDp = 320, fontScale = 2f)
@Composable
private fun CareRemindersCarouselPreview() {
    val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    BitavidaTheme {
        CareRemindersCarousel(
            listOf(CareReminder("preview-care", "preview-person", "Preparar documentos", null,
                now.plusHours(2), false, CareReminderStatus.PENDING, now, now, null, 1)),
            now, {}, {}, Modifier.padding(16.dp),
        )
    }
}
