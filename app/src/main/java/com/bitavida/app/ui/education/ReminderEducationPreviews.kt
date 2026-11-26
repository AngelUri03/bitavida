package com.bitavida.app.ui.education

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.data.education.ReminderEducationLibrary
import com.bitavida.app.domain.education.*
import com.bitavida.app.ui.theme.BitavidaTheme

@Preview(name = "Claro", widthDp = 390, heightDp = 850)
@Preview(name = "Oscuro", widthDp = 390, heightDp = 850, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Compacto", widthDp = 320, heightDp = 850)
@Preview(name = "Texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class ReminderAiModes

@ReminderAiModes
@Composable
private fun ReminderAiHomePreview() = ReminderAiPreviewPage(AiPage.HOME)

@ReminderAiModes
@Composable
private fun ReminderAiLibraryPreview() = ReminderAiPreviewPage(AiPage.LIBRARY)

@ReminderAiModes
@Composable
private fun ReminderAiAnswerPreview() = ReminderAiPreviewPage(AiPage.ANSWER)

@ReminderAiModes
@Composable
private fun ReminderAiDetailPreview() = ReminderAiPreviewPage(AiPage.DETAIL)

@Composable
private fun ReminderAiPreviewPage(page: AiPage) {
    val example = EducationResult.Answer(ReminderEducationLibrary.entries.values.first())
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiPageList(
                AiPageEntry(1, page, example, if (page == AiPage.ANSWER) "¿Cómo preparo los documentos?" else ""),
                BitavidaAiUiState(navigationContext = EducationGroup.REMINDERS, group = EducationGroup.REMINDERS),
                ReminderEducationLibrary.entries.values.toList(), {}, rememberLazyListState(), false,
            )
        }
    }
}
