package com.bitavida.app.ui.reminders

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.education.ReminderTitleAssistance
import com.bitavida.app.domain.education.ReminderSuggestion
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime

@Preview(name = "Claro", widthDp = 390, heightDp = 780)
@Preview(name = "Oscuro", widthDp = 390, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Compacto", widthDp = 320, heightDp = 700)
@Preview(name = "Texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class ReminderPreviewModes

private val previewNow = LocalDateTime.of(2026, 11, 19, 10, 0)
private val example = CareReminder(
    "reminder-example", "patient-example", "Comprar pilas para el glucómetro",
    "Revisar el tamaño de las pilas antes de salir.", previewNow.plusHours(2),
    false, CareReminderStatus.PENDING, previewNow.minusDays(1), previewNow.minusDays(1), null, 1,
)

@ReminderPreviewModes
@Composable
private fun RemindersHomePreview() {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RemindersScreen(RemindersUiState(records = listOf(example, example.copy(
                id = "reminder-past", title = "Preparar documentos", scheduledAt = previewNow.minusHours(1)), example.copy(
                id = "reminder-next", title = "Coordinar ayuda para una salida", scheduledAt = previewNow.plusDays(1),
                notificationsEnabled = true)),
                personUnderCareId = "patient-example", loading = false, now = previewNow),
                {}, {}, "Elena (ejemplo ficticio)")
        }
    }
}

@ReminderPreviewModes
@Composable
private fun PreviousRemindersPreview() {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RemindersScreen(RemindersUiState(records = listOf(example.copy(
                status = CareReminderStatus.DONE, closedAt = previewNow, revision = 2)),
                page = RemindersPage.PREVIOUS, personUnderCareId = "patient-example",
                loading = false, now = previewNow), {}, {}, "Elena (ejemplo ficticio)")
        }
    }
}

@ReminderPreviewModes
@Composable
private fun RemindersEmptyPreview() {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RemindersScreen(RemindersUiState(personUnderCareId = "patient-example", loading = false),
                {}, {}, "Elena (ejemplo ficticio)")
        }
    }
}

@ReminderPreviewModes
@Composable
private fun ReminderIdeasPreview() {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ReminderSheets(RemindersUiState(
                personUnderCareId = "patient-example", loading = false,
                sheet = ReminderSheet.SUGGESTIONS, now = previewNow,
                form = ReminderForm("draft-ideas", "operation-ideas", "", "", previewNow.plusHours(1), false),
                ideaCategory = com.bitavida.app.domain.education.ReminderEducationCategory.DOCUMENTS,
                ideaCatalog = listOf(com.bitavida.app.domain.usecase.ReminderIdeaGroup(
                    com.bitavida.app.domain.education.ReminderEducationCategory.DOCUMENTS,
                    listOf(
                        ReminderSuggestion("idea-example", "Preparar documentos", "Reúne los documentos que tú hayas decidido llevar."),
                        ReminderSuggestion("idea-folder", "Revisar una carpeta", "Consulta los documentos que ya tienes."),
                    ),
                )),
            ), {})
        }
    }
}

@ReminderPreviewModes
@Composable
private fun ReminderDuplicatePreview() {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(BitavidaSpacing.ScreenHorizontal)) {
                ReminderFormContent(
                    ReminderForm("draft-duplicate", "operation-duplicate", "Comprar baterías para el glucómetro",
                        "", previewNow.plusHours(1), false),
                    RemindersUiState(records = listOf(example), personUnderCareId = "patient-example",
                        loading = false, similarReminders = listOf(example), now = previewNow), {}, false,
                )
            }
        }
    }
}

/** The actual form content; its shared modal frame is covered by the existing design-system previews. */
@ReminderPreviewModes
@Composable
private fun ReminderFormPreview() {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
                ReminderFormContent(
                    ReminderForm("draft-example", "operation-example", "", "", previewNow.plusHours(1), false),
                    RemindersUiState(loading = false), {}, motion = false,
                )
            }
        }
    }
}
