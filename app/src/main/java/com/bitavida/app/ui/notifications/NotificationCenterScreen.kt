package com.bitavida.app.ui.notifications

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.repository.NotificationHistoryItem
import com.bitavida.app.domain.repository.NotificationPublicationResult
import com.bitavida.app.ui.demo.TodayTypography
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotificationCenterScreen(
    state: NotificationCenterState,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    canOpen: (NotificationHistoryItem) -> Boolean,
    onOpen: (NotificationHistoryItem) -> Unit,
) = TodayTypography {
    BackHandler(onBack = onBack)
    val locale = LocalLocale.current.platformLocale
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        MeasurementIcon(R.drawable.ic_v2_arrow_back, 24, MaterialTheme.colorScheme.primary)
                    }
                    Text("Notificaciones", Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            item {
                Text("Solo se muestran intentos registrados por Bitavida en este dispositivo. Enviado a Android no significa que se haya leído.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
            }
            if (state.failed) item {
                Text("No fue posible consultar o actualizar el historial.", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (state.loading) item {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
            } else if (state.items.isEmpty()) item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Text("Sin notificaciones recientes", Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                if (state.unread > 0) item {
                    OutlinedButton(onClick = onMarkAllRead, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                        Text("Marcar todas como leídas")
                    }
                }
                val zone = ZoneId.systemDefault()
                val groups = state.items.groupBy {
                    Instant.ofEpochMilli(it.attemptedAtEpochMillis).atZone(zone).toLocalDate()
                }
                groups.forEach { (day, entries) ->
                    item("day:" + day) {
                        val label = if (day == LocalDate.now(zone)) "Hoy" else
                            day.format(DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", locale))
                        Text(label, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                    }
                    items(entries, key = { it.id }) { notice ->
                        NotificationCard(notice, { onMarkRead(notice.id) },
                            if (canOpen(notice)) ({ onOpen(notice) }) else null)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notice: NotificationHistoryItem, markRead: () -> Unit, open: (() -> Unit)?) {
    val locale = LocalLocale.current.platformLocale
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = MaterialTheme.bitavidaColors.paleBlue) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    MeasurementIcon(when (notice.module) {
                        "MEDICATIONS" -> R.drawable.ic_v2_pill
                        "OBSERVATIONS" -> R.drawable.ic_v2_stethoscope
                        "MEASUREMENTS" -> R.drawable.ic_v2_droplet
                        else -> R.drawable.ic_v2_clock
                    }, 24, MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(notice.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(notice.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val time = Instant.ofEpochMilli(notice.attemptedAtEpochMillis).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm", locale))
                val status = when (notice.result) {
                    NotificationPublicationResult.PUBLISHED -> "Enviada a Android"
                    NotificationPublicationResult.BLOCKED -> "No enviada: permisos o canal desactivado"
                    NotificationPublicationResult.FAILED -> "No se pudo enviar"
                }
                Text("$time · $status · " + if (notice.readAtEpochMillis == null) "No leída" else "Leída",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (notice.readAtEpochMillis == null) OutlinedButton(onClick = markRead,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                    Text("Marcar como leída")
                }
                if (open != null) OutlinedButton(onClick = open, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                    Text("Consultar")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable private fun NotificationCenterLightPreview() {
    BitavidaTheme { NotificationCenterScreen(NotificationCenterState(loading = false), {}, {}, {}, { false }, {}) }
}
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun NotificationCenterDarkPreview() {
    BitavidaTheme(darkTheme = true) { NotificationCenterScreen(NotificationCenterState(loading = false), {}, {}, {}, { false }, {}) }
}
@Preview(showBackground = true, widthDp = 320)
@Composable private fun NotificationCenterCompactPreview() {
    BitavidaTheme { NotificationCenterScreen(NotificationCenterState(loading = false), {}, {}, {}, { false }, {}) }
}
@Preview(showBackground = true, fontScale = 2f)
@Composable private fun NotificationCenterLargeTextPreview() {
    BitavidaTheme { NotificationCenterScreen(NotificationCenterState(loading = false), {}, {}, {}, { false }, {}) }
}
