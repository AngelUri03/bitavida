package com.bitavida.app.ui.notes

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.domain.model.Note
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

@Preview(name = "Claro", widthDp = 390, heightDp = 800)
@Preview(name = "Oscuro", widthDp = 390, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Compacto", widthDp = 320, heightDp = 800)
@Preview(name = "Texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class NotesPreviewModes

private val previewNow = LocalDateTime.of(2026, 11, 19, 10, 0)
private val previewNotes = listOf(
    Note("example-one", "example-person", "Para la salida familiar",
        "Llevar el libro, la libreta y los objetos personales que acordamos.", "landscape",
        previewNow, previewNow, 1),
    Note("example-two", "example-person", "Contacto de apoyo",
        "La familia confirmó que podemos coordinar la visita por la tarde.", null,
        previewNow, previewNow, 1),
    Note("example-three", "example-person", "Carpeta de documentos", null, "portrait",
        previewNow, previewNow, 1),
    Note("example-four", "example-person", "Una idea para compartir",
        "Preguntar qué actividad le gustaría hacer en nuestra siguiente reunión. " +
            "Podemos apuntar aquí las ideas que surjan y conservarlas para comentarlas después.", null,
        previewNow, previewNow, 1),
    Note("example-five", "example-person", "Una nota breve", "Su libro favorito.", null,
        previewNow, previewNow, 1),
)

/** Fictitious neutral illustration generated only in previews; no photos or patient information. */
@Composable
private fun previewImageReader(): suspend (String) -> ByteArray? {
    val scheme = MaterialTheme.colorScheme
    val images = remember(scheme.surfaceVariant, scheme.primary, scheme.outline) {
        listOf("landscape" to (480 to 300), "portrait" to (300 to 480)).associate { (id, size) ->
            val bitmap = createBitmap(size.first, size.second)
            val canvas = Canvas(bitmap)
            canvas.drawColor(scheme.surfaceVariant.toArgb())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scheme.primary.toArgb() }
            canvas.drawCircle(size.first * .7f, size.second * .3f, size.first * .12f, paint)
            paint.color = scheme.outline.toArgb()
            canvas.drawRoundRect(size.first * .12f, size.second * .5f, size.first * .78f,
                size.second * .8f, 16f, 16f, paint)
            val bytes = ByteArrayOutputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                it.toByteArray()
            }
            bitmap.recycle()
            id to bytes
        }
    }
    return { images[it]?.copyOf() }
}

@NotesPreviewModes
@Composable
private fun NotesMosaicPreview() {
    BitavidaTheme {
        val read = previewImageReader()
        Surface(color = MaterialTheme.colorScheme.background) {
            NotesScreen(NotesUiState(notes = previewNotes, loading = false), {}, {}, "Persona ficticia",
                readImage = read)
        }
    }
}

@NotesPreviewModes
@Composable
private fun NotesEmptyPreview() {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            NotesScreen(NotesUiState(loading = false), {}, {}, "Persona ficticia")
        }
    }
}

@Composable
private fun NotePreviewSheet(title: String, content: @Composable ColumnScope.() -> Unit) {
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

@NotesPreviewModes
@Composable
private fun NoteCreatePreview() = NotePreviewSheet("Nueva nota") {
    NoteFormContent(NoteForm("example-draft", "example-operation"), NotesUiState(loading = false),
        {}, { null }, false)
}

@NotesPreviewModes
@Composable
private fun NoteEditWithImagePreview() = NotePreviewSheet("Editar nota") {
    val note = previewNotes.first()
    val read = previewImageReader()
    NoteFormContent(NoteForm(note.id, "example-operation", note.title, note.content.orEmpty(),
        note.imageId, note.revision), NotesUiState(loading = false), {}, read, false)
}

@NotesPreviewModes
@Composable
private fun NoteDetailPreview() = NotePreviewSheet(previewNotes.first().title) {
    NoteDetailContent(NotesUiState(notes = previewNotes, loading = false,
        selectedId = previewNotes.first().id), {}, previewImageReader(), false)
}
