package com.bitavida.app.ui.settings

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.demo.TodayTypography
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.theme.BitavidaTheme

/** System permissions and channels remain under Android's control. */
@Composable
fun BitavidaSettingsScreen(
    caregiver: String,
    patient: String,
    protectionEnabled: Boolean,
    notificationsAllowed: Boolean,
    onBack: () -> Unit,
    onSystemNotificationSettings: () -> Unit,
    onEditNames: (() -> Unit)? = null,
    biometricBusy: Boolean = false,
    biometricMessage: String? = null,
    onBiometricChange: ((Boolean) -> Unit)? = null,
    notificationChannels: List<Pair<String, String>> = emptyList(),
    onNotificationChannelSettings: ((String) -> Unit)? = null,
    section: String = "menu",
    onSectionChange: (String) -> Unit = {},
    onDebugReportDiagnostic: (() -> Unit)? = null,
) = TodayTypography {
    val motion = rememberBitavidaMotionAllowed(override = null)
    val goBack = {
        if (!biometricBusy) {
            if (section == "menu") onBack() else onSectionChange("menu")
        }
    }
    BackHandler(onBack = goBack)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        AnimatedContent(
            targetState = section,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                val forward = targetState != "menu"
                val duration = if (motion) BitavidaFluidMotion.StateTransition else 0
                (fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { if (forward) it / 10 else -it / 10 })
                    .togetherWith(fadeOut(tween(duration)) + slideOutHorizontally(tween(duration)) { if (forward) -it / 10 else it / 10 })
            }, label = "sección de configuración",
        ) { current ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = goBack, enabled = !biometricBusy,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Atrás" }) {
                        MeasurementIcon(R.drawable.ic_v2_arrow_back, 24, MaterialTheme.colorScheme.primary)
                    }
                    Text(when (current) {
                        "security" -> "Seguridad y acceso"
                        "notifications" -> "Notificaciones"
                        else -> "Configuración"
                    }, Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                }
                when (current) {
                    "security" -> SettingSection("Protección de Bitavida") {
                        if (onBiometricChange != null) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Proteger acceso a Bitavida", style = MaterialTheme.typography.titleSmall)
                                    Text(if (protectionEnabled) "Protección activada" else "Protección desactivada",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Si está activada, confirma con la biometría o el código configurados en tu teléfono.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = protectionEnabled, onCheckedChange = onBiometricChange, enabled = !biometricBusy,
                                    modifier = Modifier.semantics { contentDescription = "Proteger acceso a Bitavida" },
                                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        uncheckedBorderColor = MaterialTheme.colorScheme.outline))
                            }
                            biometricMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    "notifications" -> {
                        SettingSection("Permisos y avisos") {
                            Text(if (notificationsAllowed) "Avisos permitidos por Android" else "Avisos desactivados o sin permiso",
                                style = MaterialTheme.typography.titleSmall)
                            Text("Los horarios de cada seguimiento se conservan en su módulo.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = onSystemNotificationSettings, modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                                Text("Ajustes de Android")
                            }
                        }
                        if (onNotificationChannelSettings != null && notificationChannels.isNotEmpty()) {
                            SettingSection("Canales de notificación") {
                                notificationChannels.forEach { (id, label) ->
                                    SettingRow(label, "Disponible en Android", R.drawable.ic_v2_bell,
                                        onClick = { onNotificationChannelSettings(id) })
                                }
                            }
                        }
                    }
                    else -> {
                        if (onEditNames != null) SettingRow("Datos personales",
                            "Información del cuidador y la persona acompañada", R.drawable.ic_v2_edit, onEditNames)
                        SettingRow("Seguridad y acceso", "Desbloqueo y protección de Bitavida",
                            R.drawable.ic_v2_settings) { onSectionChange("security") }
                        SettingRow("Notificaciones", "Avisos, permisos y preferencias",
                            R.drawable.ic_v2_bell) { onSectionChange("notifications") }
                        onDebugReportDiagnostic?.let { open ->
                            SettingRow("Diagnóstico de desarrollo", "Exportar una instantánea local para revisar relaciones",
                                R.drawable.ic_v2_settings, open)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 2.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    MeasurementIcon(icon, 21, MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MeasurementIcon(R.drawable.ic_v2_chevron, 18, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable private fun SettingsLightPreview() {
    BitavidaTheme { BitavidaSettingsScreen("Ana", "María", true, true, {}, {}, onEditNames = {}) }
}
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun SettingsDarkPreview() {
    BitavidaTheme(darkTheme = true) { BitavidaSettingsScreen("Ana", "María", true, false, {}, {}, onEditNames = {}) }
}
@Preview(showBackground = true, widthDp = 320)
@Composable private fun SettingsCompactPreview() {
    BitavidaTheme { BitavidaSettingsScreen("Ana", "María", true, true, {}, {}, onEditNames = {}) }
}
@Preview(showBackground = true, fontScale = 2f)
@Composable private fun SettingsLargeTextPreview() {
    BitavidaTheme { BitavidaSettingsScreen("Ana", "María", true, true, {}, {}, onEditNames = {}) }
}
