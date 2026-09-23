package com.coparently.app.presentation.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.repository.FamilyDocumentRepository
import com.coparently.app.presentation.common.OpenedFile
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.sharedFileError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the vault screen can show in place of, or as, its list. */
sealed interface DocumentsList {
    /** Not answered yet. */
    data object Loading : DocumentsList

    /** No co-parent is linked: a vault belongs to a family, and there is none to file into. */
    data object NoFamily : DocumentsList

    /** The server could not be read — said in words, never drawn as an empty vault. */
    data object Unavailable : DocumentsList

    /** The family's live documents, grouped by the screen. */
    data class Loaded(val documents: List<FamilyDocument>) : DocumentsList
}

/**
 * The vault screen's state.
 *
 * @property myUid The signed-in uid; only its own documents offer Delete (the rule refuses others).
 * @property uploading True while a file is going up — the add actions wait for it.
 * @property error What went wrong last, for a snackbar; cleared once shown.
 */
data class DocumentsUiState(
    val list: DocumentsList = DocumentsList.Loading,
    val myUid: String = "",
    val uploading: Boolean = false,
    val error: UiText? = null
)

/**
 * The family document vault (MON-23): Settings → Family → Documents.
 *
 * The family is the one on screen when the vault opens (`SelectedFamilySource`, M-8), read fresh
 * rather than from a `WhileSubscribed` value (CLAUDE.md item 17), and every document added goes
 * to it. Nothing is private here and nothing is cached in Room: the list is a Firestore listener.
 */
@HiltViewModel
class FamilyDocumentsViewModel @Inject constructor(
    private val repository: FamilyDocumentRepository,
    private val selectedFamilySource: SelectedFamilySource,
    private val authService: FirebaseAuthService,
    parentsSource: ParentsSource
) : ViewModel() {

    /** The two parents, to name who added each document. */
    val parents: StateFlow<Parents> = parentsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Parents())

    private val _state = MutableStateFlow(DocumentsUiState())

    /** The screen's state. */
    val state: StateFlow<DocumentsUiState> = _state.asStateFlow()

    private val _opened = Channel<OpenedFile>(Channel.BUFFERED)

    /** Files ready to open, one event per tap. */
    val opened: Flow<OpenedFile> = _opened.receiveAsFlow()

    private var familyId: String? = null

    init {
        viewModelScope.launch {
            val family = selectedFamilySource.selected()
            _state.update { it.copy(myUid = authService.getCurrentUser()?.uid.orEmpty()) }
            if (family == null) {
                _state.update { it.copy(list = DocumentsList.NoFamily) }
                return@launch
            }
            familyId = family.familyId
            repository.observe(family.familyId).collect { documents ->
                _state.update {
                    it.copy(list = documents?.let(DocumentsList::Loaded) ?: DocumentsList.Unavailable)
                }
            }
        }
    }

    /** Uploads the file at [contentUri] as [title] under [category], for the family on screen. */
    fun add(title: String, category: DocumentCategory, contentUri: String) {
        val family = familyId ?: return
        if (_state.value.uploading) return
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            val result = repository.add(family, title, category, contentUri)
            _state.update {
                it.copy(
                    uploading = false,
                    error = result.exceptionOrNull()?.let { e -> sharedFileError(e, R.string.documents_error_upload) }
                )
            }
        }
    }

    /** Tombstones [document]; the listener then drops it from both parents' lists. */
    fun delete(document: FamilyDocument) {
        viewModelScope.launch {
            repository.delete(document).onFailure {
                _state.update { it.copy(error = UiText.Res(R.string.documents_error_delete)) }
            }
        }
    }

    /** Downloads and checks [document]'s file, then asks the screen to open it. */
    fun open(document: FamilyDocument) {
        viewModelScope.launch {
            repository.localCopy(document)
                .onSuccess { _opened.trySend(OpenedFile(it, document.contentType)) }
                .onFailure { e ->
                    _state.update { it.copy(error = sharedFileError(e, R.string.shared_file_error_open)) }
                }
        }
    }

    /** The screen could not find an app to open a file with. */
    fun noViewer() {
        _state.update { it.copy(error = UiText.Res(R.string.shared_file_no_viewer)) }
    }

    /** The snackbar has shown [DocumentsUiState.error]. */
    fun errorShown() {
        _state.update { it.copy(error = null) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
