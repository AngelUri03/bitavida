package com.bitavida.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.model.Note
import com.bitavida.app.domain.model.NoteDraft
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.repository.NoteRepository
import com.bitavida.app.domain.usecase.GetNotes
import com.bitavida.app.domain.usecase.ManageNotes
import com.bitavida.app.domain.usecase.NoteCommand
import com.bitavida.app.domain.usecase.NoteProblem
import com.bitavida.app.domain.usecase.NoteValidationException
import com.bitavida.app.domain.usecase.visibleNotes
import com.bitavida.app.domain.validation.CaptureLimit
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NotesSheet { FORM, DETAIL, DELETE }

data class NoteForm(
    val id: String,
    val operationId: String,
    val title: String = "",
    val content: String = "",
    val imageId: String? = null,
    val editingRevision: Int? = null,
)

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val imagePreparing: Boolean = false,
    val sheet: NotesSheet? = null,
    val selectedId: String? = null,
    val form: NoteForm? = null,
    val error: String? = null,
    val titleError: Boolean = false,
) {
    val selected: Note? get() = notes.singleOrNull { it.id == selectedId }
}

sealed interface NotesAction {
    data object Reload : NotesAction
    data object Create : NotesAction
    data class Open(val id: String) : NotesAction
    data object Edit : NotesAction
    data object DismissSheet : NotesAction
    data class ChangeTitle(val value: String) : NotesAction
    data class ChangeContent(val value: String) : NotesAction
    data object Save : NotesAction
    data object RequestDelete : NotesAction
    data object ConfirmDelete : NotesAction
    data object CancelDelete : NotesAction
    data class RequestImage(val source: NotesPhotoSource) : NotesAction
    data object RemoveImage : NotesAction
}

/** One retained draft and one modal host. User text never enters SavedState or external Intents. */
class NotesViewModel(
    repository: NoteRepository,
    val actor: NoteActor,
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
    private val scopeOverride: CoroutineScope? = null,
    private val readImage: suspend (String) -> ByteArray? = { null },
    private val discardImage: suspend (String) -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow(NotesUiState())
    val state = mutableState.asStateFlow()
    private val getNotes = GetNotes(repository)
    private val manageNotes = ManageNotes(repository)
    private val scope get() = scopeOverride ?: viewModelScope
    private val imageChannel = Channel<NotesPhotoRequest>(Channel.BUFFERED)
    val photoRequests = imageChannel.receiveAsFlow()
    private val stagedImages = linkedSetOf<String>()
    private var pendingImage: NotesPhotoRequest? = null
    private var loadInFlight = false
    private var loadEpoch = 0L
    private var cleared = false

    init { reload() }

    fun onAction(action: NotesAction) {
        if (cleared || mutableState.value.saving) return
        val current = mutableState.value
        when (action) {
            NotesAction.Reload -> reload()
            NotesAction.Create -> openForm(null)
            is NotesAction.Open -> current.notes.firstOrNull { it.id == action.id }?.let {
                discardDraft()
                mutableState.update { state -> state.copy(sheet = NotesSheet.DETAIL, selectedId = it.id,
                    form = null, error = null, titleError = false) }
            }
            NotesAction.Edit -> current.selected?.let(::openForm)
            NotesAction.DismissSheet -> {
                discardDraft()
                mutableState.update { it.copy(sheet = null, form = null, selectedId = null,
                    error = null, titleError = false, imagePreparing = false) }
            }
            is NotesAction.ChangeTitle -> current.form?.let { form ->
                if (action.value != form.title && !CaptureLimit.NOTE_TITLE.acceptsEdit(form.title, action.value)) return
                mutableState.update { it.copy(form = form.copy(title = action.value), error = null, titleError = false) }
            }
            is NotesAction.ChangeContent -> current.form?.let { form ->
                if (action.value != form.content && !CaptureLimit.NOTE_CONTENT.acceptsEdit(form.content, action.value)) return
                mutableState.update { it.copy(form = form.copy(content = action.value), error = null) }
            }
            NotesAction.Save -> save()
            NotesAction.RequestDelete -> if (current.selected != null) {
                mutableState.update { it.copy(sheet = NotesSheet.DELETE, error = null) }
            }
            NotesAction.CancelDelete -> mutableState.update { it.copy(sheet = NotesSheet.DETAIL, error = null) }
            NotesAction.ConfirmDelete -> current.selected?.let {
                persist(NoteCommand.Delete(UUID.randomUUID().toString(), it.id, it.revision))
            }
            is NotesAction.RequestImage -> requestImage(action.source)
            NotesAction.RemoveImage -> current.form?.let { form ->
                pendingImage = null
                mutableState.update { it.copy(form = form.copy(imageId = null), imagePreparing = false, error = null) }
                form.imageId?.takeIf { it in stagedImages }?.let { discardImages(setOf(it)) }
            }
        }
    }

    private fun openForm(note: Note?) {
        discardDraft()
        val form = NoteForm(
            id = note?.id ?: UUID.randomUUID().toString(),
            operationId = UUID.randomUUID().toString(),
            title = note?.title.orEmpty(), content = note?.content.orEmpty(),
            imageId = note?.imageId, editingRevision = note?.revision,
        )
        mutableState.update { it.copy(form = form, sheet = NotesSheet.FORM, selectedId = note?.id,
            error = null, titleError = false, imagePreparing = false) }
    }

    private fun save() {
        val current = mutableState.value
        val form = current.form ?: return
        if (current.imagePreparing) return
        if (form.title.isBlank()) {
            mutableState.update { it.copy(titleError = true, error = "Escribe un título para la nota.") }
            return
        }
        val draft = NoteDraft(form.title, form.content.takeIf(String::isNotEmpty), form.imageId)
        val command = form.editingRevision?.let {
            NoteCommand.Edit(form.operationId, form.id, it, draft)
        } ?: NoteCommand.Create(form.operationId, form.id, draft)
        persist(command)
    }

    private fun persist(command: NoteCommand) {
        mutableState.update { it.copy(saving = true, error = null, titleError = false) }
        scope.launch {
            try {
                val saved = manageNotes(actor, command, nowProvider())
                loadEpoch++
                val oldStaged = stagedImages.toSet()
                stagedImages.clear()
                pendingImage = null
                mutableState.update {
                    it.copy(notes = visibleNotes(it.notes.filterNot { note -> note.id == saved.id } + saved,
                        actor.personUnderCareId), loading = false, loadFailed = false, saving = false,
                        sheet = null, selectedId = null, form = null, imagePreparing = false, error = null)
                }
                discardImages(oldStaged - setOfNotNull(saved.imageId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (validation: NoteValidationException) {
                mutableState.update { it.copy(saving = false, error = noteProblemMessage(validation.problem),
                    titleError = validation.problem in setOf(NoteProblem.TITLE_REQUIRED, NoteProblem.TITLE_TOO_LONG)) }
            } catch (_: Exception) {
                mutableState.update { it.copy(saving = false,
                    error = "No pudimos guardar el cambio. Tu información anterior se conserva. Intenta de nuevo.") }
            }
        }
    }

    private fun requestImage(source: NotesPhotoSource) {
        val form = mutableState.value.form ?: return
        if (pendingImage != null || mutableState.value.imagePreparing) return
        val request = NotesPhotoRequest(UUID.randomUUID().toString(), source, form.id,
            form.operationId, form.imageId)
        pendingImage = request
        mutableState.update { it.copy(imagePreparing = true, error = null) }
        if (imageChannel.trySend(request).isFailure) finishImageRequest(request, "No se pudo iniciar la selección de imagen.")
    }

    /** Called after a bounded private import. A late result is never attached to a different draft. */
    fun acceptImportedImage(request: NotesPhotoRequest, id: String): Boolean {
        val form = mutableState.value.form ?: return false
        if (cleared || mutableState.value.sheet != NotesSheet.FORM || pendingImage?.requestId != request.requestId ||
            form.id != request.draftId || form.operationId != request.operationId ||
            form.imageId != request.replacingImageId || id.isBlank()) return false
        stagedImages += id
        pendingImage = null
        mutableState.update { it.copy(form = form.copy(imageId = id), imagePreparing = false, error = null) }
        request.replacingImageId?.takeIf { it in stagedImages && it != id }?.let { discardImages(setOf(it)) }
        return true
    }

    fun finishImageRequest(request: NotesPhotoRequest, error: String? = null) {
        if (cleared || pendingImage?.requestId != request.requestId) return
        pendingImage = null
        mutableState.update { it.copy(imagePreparing = false, error = error) }
    }

    fun isImageRequestCurrent(request: NotesPhotoRequest): Boolean {
        val form = mutableState.value.form
        return !cleared && pendingImage?.requestId == request.requestId && form?.id == request.draftId &&
            form.operationId == request.operationId && mutableState.value.sheet == NotesSheet.FORM
    }

    suspend fun imageBytes(id: String): ByteArray? = readImage(id)

    /** Finite resource work must finish cleanup even if its original screen is dismissed. */
    fun launchPhotoWork(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { block() }
        }
    }

    private fun discardDraft() {
        pendingImage = null
        val stale = stagedImages.toSet()
        stagedImages.clear()
        discardImages(stale)
    }

    private fun discardImages(ids: Set<String>) {
        if (ids.isEmpty()) return
        launchPhotoWork {
            ids.forEach { id ->
                try {
                    discardImage(id)
                    stagedImages.remove(id)
                } catch (_: Exception) { /* The private store never removes a referenced image. */ }
            }
        }
    }

    private fun reload() {
        if (loadInFlight) return
        loadInFlight = true
        val epoch = ++loadEpoch
        mutableState.update { it.copy(loading = true, loadFailed = false, error = null) }
        scope.launch {
            try {
                val loaded = getNotes(actor)
                if (epoch == loadEpoch) mutableState.update { it.copy(notes = loaded, loading = false, loadFailed = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (epoch == loadEpoch) mutableState.update { it.copy(loading = false, loadFailed = true,
                    error = "No pudimos cargar las notas. Intenta de nuevo.") }
            } finally { loadInFlight = false }
        }
    }

    override fun onCleared() {
        cleared = true
        discardDraft()
        mutableState.update { it.copy(form = null, selectedId = null, sheet = null, imagePreparing = false) }
        imageChannel.close()
    }
}

private fun noteProblemMessage(problem: NoteProblem): String = when (problem) {
    NoteProblem.TITLE_REQUIRED -> "Escribe un título para la nota."
    NoteProblem.TITLE_TOO_LONG -> "El título admite hasta 100 caracteres."
    NoteProblem.CONTENT_TOO_LONG -> "El contenido admite hasta 2000 caracteres."
    NoteProblem.CONFLICT -> "La nota cambió. Cierra este formulario y vuelve a abrirla antes de editar."
    NoteProblem.DELETED, NoteProblem.NOT_FOUND -> "Esta nota ya no está disponible."
    NoteProblem.INVALID_IMAGE -> "La imagen no está disponible. Puedes reemplazarla o quitarla."
    NoteProblem.INVALID_OPERATION -> "No pudimos guardar el cambio. Intenta de nuevo."
}
