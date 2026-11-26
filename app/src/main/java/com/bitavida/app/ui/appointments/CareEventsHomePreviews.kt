package com.bitavida.app.ui.appointments

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventCategory
import com.bitavida.app.domain.model.CareEventDraft
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventStatus
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime

/** Example data exists only in previews and is never inserted into the repository. */
private fun previewAppointments(): List<CareEvent> {
    val now = LocalDateTime.now()
    return listOf(
        CareEvent("one", "example", CareEventDraft(
            kind = CareEventKind.APPOINTMENT, title = "Medicina familiar",
            status = CareEventStatus.CONFIRMED, scheduledAt = now.plusDays(2).withHour(10).withMinute(30),
            institution = "Unidad médica", office = "Consultorio 4",
            instructions = "Llevar identificación y libreta.",
        ), now, now, 1),
        CareEvent("two", "example", CareEventDraft(
            kind = CareEventKind.STUDY, category = CareEventCategory.LABORATORY,
            title = "Estudio de laboratorio", status = CareEventStatus.SCHEDULED,
            scheduledAt = now.plusDays(4).withHour(7).withMinute(0),
            institution = "Laboratorio",
        ), now, now, 1),
        CareEvent("three", "example", CareEventDraft(
            kind = CareEventKind.APPOINTMENT, category = CareEventCategory.SPECIALTY,
            title = "Consulta de especialidad", status = CareEventStatus.CONFIRMED,
            scheduledAt = now.plusDays(8).withHour(16).withMinute(0),
        ), now, now, 1),
    )
}

@Composable
private fun AppointmentHomePreview(dark: Boolean, populated: Boolean) {
    BitavidaTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CareEventsScreen(
                state = CareEventsState(events = if (populated) previewAppointments() else emptyList(), loading = false),
                onAction = {}, person = "Persona de ejemplo", onBack = {},
            )
        }
    }
}

@Preview(name = "Citas con eventos", showBackground = true, widthDp = 390, heightDp = 900)
@Composable private fun AppointmentHomePopulatedLight() { AppointmentHomePreview(false, true) }

@Preview(name = "Citas con eventos oscuro", showBackground = true, widthDp = 390, heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun AppointmentHomePopulatedDark() { AppointmentHomePreview(true, true) }

@Preview(name = "Citas vacías", showBackground = true, widthDp = 390, heightDp = 850)
@Composable private fun AppointmentHomeEmpty() { AppointmentHomePreview(false, false) }

@Preview(name = "Citas vacías oscuro", showBackground = true, widthDp = 390, heightDp = 850,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun AppointmentHomeEmptyDark() { AppointmentHomePreview(true, false) }

@Preview(name = "Citas vacías compactas", showBackground = true, widthDp = 320, heightDp = 720)
@Composable private fun AppointmentHomeEmptyCompact() { AppointmentHomePreview(false, false) }

@Preview(name = "Citas vacías texto 200%", showBackground = true, widthDp = 320, heightDp = 720, fontScale = 2f)
@Composable private fun AppointmentHomeEmptyLargeText() { AppointmentHomePreview(false, false) }

@Preview(name = "Citas compactas", showBackground = true, widthDp = 320, heightDp = 720)
@Composable private fun AppointmentHomeCompact() { AppointmentHomePreview(false, true) }

@Preview(name = "Citas texto 200%", showBackground = true, widthDp = 320, heightDp = 720, fontScale = 2f)
@Composable private fun AppointmentHomeLargeText() { AppointmentHomePreview(false, true) }

@Preview(name = "Historial claro", showBackground = true, widthDp = 390)
@Preview(name = "Historial oscuro", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Historial compacto", showBackground = true, widthDp = 320)
@Preview(name = "Historial texto 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun AppointmentHistoryCardPreview() {
    val event = previewAppointments().first()
    BitavidaTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
        CareEventHistoryCard(event, event.draft.scheduledAt!!.plusDays(1), true) {}
    }
}

/** A drawn fixture is confined to previews and never replaces a stored photograph. */
@Preview(name = "Siguiente cita con fotografía · claro", showBackground = true, widthDp = 390)
@Preview(name = "Siguiente cita con fotografía · oscuro", showBackground = true, widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Siguiente cita con fotografía · compacto", showBackground = true, widthDp = 320)
@Preview(name = "Siguiente cita con fotografía · texto 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun FeaturedCardWithPhotoPreview() {
    BitavidaTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
        FeaturedAppointmentContent(previewAppointments().first(), motion = false, hasPhoto = true, onOpen = {}) {
            val paper = MaterialTheme.colorScheme.surfaceVariant
            val ink = MaterialTheme.colorScheme.onSurfaceVariant
            FeaturedPhotoThumbnail(count = 3, onClick = {}) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawRect(paper)
                    for (line in 0..3) {
                        val y = size.height * (0.25f + line * 0.13f)
                        drawLine(ink, androidx.compose.ui.geometry.Offset(size.width * 0.18f, y),
                            androidx.compose.ui.geometry.Offset(size.width * 0.72f, y),
                            strokeWidth = size.width * 0.025f)
                    }
                }
            }
        }
    }
}

@Preview(name = "Siguiente cita sin fotografía · claro", showBackground = true, widthDp = 390)
@Preview(name = "Siguiente cita sin fotografía · oscuro", showBackground = true, widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun FeaturedCardWithoutPhotoPreview() {
    BitavidaTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
        FeaturedAppointmentContent(previewAppointments().first(), motion = false, hasPhoto = false, onOpen = {})
    }
}

@Preview(name = "Siguiente cita · nombre y ubicación extensos", showBackground = true, widthDp = 320)
@Preview(name = "Siguiente cita · nombre extenso al 200%", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun FeaturedCardLongTextPreview() {
    val sample = previewAppointments().first()
    val event = sample.copy(draft = sample.draft.copy(
        title = "Consulta de seguimiento con medicina familiar y endocrinología",
        institution = "Unidad médica de ejemplo con nombre extendido",
        reference = "Consultorio 12, planta baja, acceso principal del edificio de especialidades",
    ))
    BitavidaTheme {
        FeaturedAppointmentContent(event, motion = false, hasPhoto = true, onOpen = {}) {
            val ink = MaterialTheme.colorScheme.onSurfaceVariant
            FeaturedPhotoThumbnail(count = 1, onClick = {}) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawLine(ink, androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.4f),
                        androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.4f),
                        strokeWidth = size.width * 0.025f)
                }
            }
        }
    }
}
