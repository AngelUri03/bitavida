package com.bitavida.app.ui.reminders

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.domain.education.ReminderEducationCategory
import com.bitavida.app.domain.education.ReminderSuggestion
import com.bitavida.app.domain.education.ReminderTitleAssistance
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.usecase.ReminderIdeaGroup
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDateTime

@Preview(name = "Claro", widthDp = 390, heightDp = 720)
@Preview(name = "Oscuro", widthDp = 390, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Compacto", widthDp = 320, heightDp = 780)
@Preview(name = "Texto 200 %", widthDp = 320, heightDp = 1300, fontScale = 2f)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class CompactReminderModes

private val compactNow = LocalDateTime.of(2026, 11, 19, 10, 0)
private val compactForm = ReminderForm(
    "example-draft", "example-operation", "", "", compactNow.plusHours(2), false,
)
private val compactRecord = CareReminder(
    "example-reminder", "example-person", "Preparar documentos",
    "Reunir los documentos que ya acordamos llevar.", compactNow.plusHours(2), true,
    CareReminderStatus.PENDING, compactNow.minusDays(1), compactNow, null, 1,
)

@Composable
private fun CompactSheetPreview(title: String, content: @Composable ColumnScope.() -> Unit) {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(BitavidaSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
            }
        }
    }
}

@CompactReminderModes
@Composable
private fun ReminderCompactFormPreview() = CompactSheetPreview("Nuevo recordatorio") {
    ReminderFormContent(compactForm, RemindersUiState(loading = false, now = compactNow), {}, false)
}

@CompactReminderModes
@Composable
private fun ReminderInlineSuggestionPreview() = CompactSheetPreview("Nuevo recordatorio") {
    ReminderFormContent(
        compactForm.copy(title = "Pilas del glucometro", notificationsEnabled = true),
        RemindersUiState(loading = false, now = compactNow,
            assistance = ReminderTitleAssistance(suggestedTitle = "Comprar pilas para el glucómetro")),
        {}, false,
    )
}

@CompactReminderModes
@Composable
private fun ReminderAmbiguousTitlePreview() = CompactSheetPreview("Nuevo recordatorio") {
    ReminderFormContent(
        compactForm.copy(title = "Papeles"),
        RemindersUiState(loading = false, now = compactNow,
            assistance = ReminderTitleAssistance(clarifications = listOf(
                "Preparar documentos", "Recoger documentos", "Organizar documentos"))),
        {}, false,
    )
}

@CompactReminderModes
@Composable
private fun ReminderCompactPostponePreview() = CompactSheetPreview("Posponer") {
    ReminderPostponeContent(
        compactForm.copy(id = compactRecord.id, editingRevision = 1),
        RemindersUiState(records = listOf(compactRecord), selectedId = compactRecord.id,
            loading = false, now = compactNow), {}, false,
    )
}

@CompactReminderModes
@Composable
private fun ReminderCompactDetailPreview() = CompactSheetPreview(compactRecord.title) {
    ReminderDetailContent(
        compactRecord,
        RemindersUiState(records = listOf(compactRecord), loading = false,
            personUnderCareId = "example-person", now = compactNow), {}, false, showIdentity = false,
    )
}

@CompactReminderModes
@Composable
private fun ReminderClosedDetailPreview() = CompactSheetPreview(compactRecord.title) {
    val closed = compactRecord.copy(
        status = CareReminderStatus.DONE, closedAt = compactNow.plusHours(3), revision = 2,
    )
    ReminderDetailContent(
        closed, RemindersUiState(records = listOf(closed), loading = false,
            personUnderCareId = closed.personUnderCareId, now = compactNow.plusDays(1)),
        {}, false, showIdentity = false,
    )
}

@CompactReminderModes
@Composable
private fun ReminderDiscardedDetailPreview() = CompactSheetPreview(compactRecord.title) {
    val closed = compactRecord.copy(
        status = CareReminderStatus.DISCARDED, closedAt = compactNow.plusHours(3), revision = 2,
    )
    ReminderDetailContent(
        closed, RemindersUiState(records = listOf(closed), loading = false,
            personUnderCareId = closed.personUnderCareId, now = compactNow.plusDays(1)),
        {}, false, showIdentity = false,
    )
}

@CompactReminderModes
@Composable
private fun ReminderDetailWithoutNotesPreview() = CompactSheetPreview(compactRecord.title) {
    val record = compactRecord.copy(details = null, notificationsEnabled = false)
    ReminderDetailContent(
        record, RemindersUiState(records = listOf(record), loading = false,
            personUnderCareId = record.personUnderCareId, now = compactNow),
        {}, false, showIdentity = false,
    )
}

@CompactReminderModes
@Composable
private fun ReminderCompactIdeasPreview() = CompactSheetPreview("Ideas para tu recordatorio") {
    ReminderIdeasContent(
        RemindersUiState(loading = false, now = compactNow,
            ideaCategory = ReminderEducationCategory.SHOPPING,
            ideaCatalog = listOf(
                ReminderIdeaGroup(ReminderEducationCategory.SHOPPING, listOf(
                    ReminderSuggestion("example-one", "Revisar artículos faltantes", ""),
                    ReminderSuggestion("example-two", "Comprar artículos necesarios", ""),
                    ReminderSuggestion("example-three", "Recoger suministros", ""),
                )),
                ReminderIdeaGroup(ReminderEducationCategory.DOCUMENTS, listOf(
                    ReminderSuggestion("example-documents", "Preparar documentos", ""),
                )),
            )),
        {}, false,
    )
}
