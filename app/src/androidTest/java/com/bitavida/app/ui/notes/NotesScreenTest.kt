package com.bitavida.app.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.model.Note
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Prepared semantic/interaction coverage, with fictitious text and no camera or private database. */
class NotesScreenTest {
    @get:Rule val rule = createComposeRule()
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)

    @Test fun emptyMosaicCreatesThroughTheSameEntryPointInBothThemes() {
        val dark = mutableStateOf(false)
        var dispatched: NotesAction? = null
        rule.setContent {
            BitavidaTheme(darkTheme = dark.value) {
                NotesScreen(NotesUiState(loading = false), { dispatched = it }, {}, "Persona ficticia")
            }
        }
        rule.onNodeWithText("Un espacio para tus apuntes").assertIsDisplayed()
        rule.onNodeWithTag("notes_create").assertHeightIsAtLeast(48.dp).performClick()
        rule.runOnIdle {
            assertEquals(NotesAction.Create, dispatched)
            dark.value = true
        }
        rule.onNodeWithTag("notes_create").assertIsDisplayed()
        rule.onNodeWithText("Completar").assertDoesNotExist()
        rule.onNodeWithText("Posponer").assertDoesNotExist()
    }

    @Test fun readableCompactLargeTextCardsOpenTheRequestedNote() {
        val first = Note("first", "person-example", "Un título extenso para una nota ficticia",
            "Contenido que se muestra en la vista previa y permanece disponible al abrirla.", null, now, now, 1)
        var dispatched: NotesAction? = null
        rule.setContent {
            BitavidaTheme(darkTheme = true) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                    Box(Modifier.width(320.dp)) {
                        NotesScreen(NotesUiState(notes = listOf(first), loading = false),
                            { dispatched = it }, {}, "Persona ficticia")
                    }
                }
            }
        }
        rule.onNodeWithTag("note_first").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.runOnIdle { assertEquals(NotesAction.Open("first"), dispatched) }
    }

    @Test fun compactFormRetainsRealButtonsAndTheSaveActionAtTwoHundredPercentText() {
        val form = mutableStateOf(NoteForm("draft-example", "operation-example"))
        val actions = mutableListOf<NotesAction>()
        rule.setContent {
            BitavidaTheme {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                    Box(Modifier.width(320.dp).fillMaxSize()) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(BitavidaSpacing.Large)) {
                            NoteFormContent(form.value, NotesUiState(loading = false), {
                                actions += it
                                when (it) {
                                    is NotesAction.ChangeTitle -> form.value = form.value.copy(title = it.value)
                                    is NotesAction.ChangeContent -> form.value = form.value.copy(content = it.value)
                                    else -> Unit
                                }
                            }, { null }, false)
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("note_title").performTextInput("Nota ficticia")
        rule.onNodeWithText("Tomar foto").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Elegir imagen").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Guardar nota").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.runOnIdle {
            assertTrue(actions.contains(NotesAction.RequestImage(NotesPhotoSource.CAMERA)))
            assertTrue(actions.contains(NotesAction.RequestImage(NotesPhotoSource.GALLERY)))
            assertEquals(NotesAction.Save, actions.last())
            assertEquals("Nota ficticia", form.value.title)
        }
    }

    @Test fun detailShowsCompleteTextAndRequiresTheDeleteConfirmationAction() {
        val content = "Apunte ficticio completo que permanece en el detalle."
        val note = Note("one", "person-example", "Nota ficticia", content, null, now, now, 1)
        var action: NotesAction? = null
        rule.setContent {
            BitavidaTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(BitavidaSpacing.Large)) {
                    NoteDetailContent(NotesUiState(notes = listOf(note), loading = false,
                        selectedId = note.id), { action = it }, { null }, false)
                }
            }
        }
        rule.onNodeWithText(content).assertIsDisplayed()
        rule.onNodeWithText("Editar").performScrollTo().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Eliminar nota").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.runOnIdle { assertEquals(NotesAction.RequestDelete, action) }
        rule.onNodeWithText("Completar").assertDoesNotExist()
        rule.onNodeWithText("Posponer").assertDoesNotExist()
    }
}
