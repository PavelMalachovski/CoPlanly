package com.coparently.app.presentation.journal

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.repository.JournalRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * The journal editor's state.
 *
 * @property loading True while an existing entry is being read; the form is not editable yet.
 * @property isNew Whether saving creates an entry rather than changing one.
 * @property entryDate The day the entry is about; today for a new one.
 * @property saved Set once the entry is in Room — the screen goes back when it sees it.
 * @property deleted Set once the entry is gone from Room — the screen goes back when it sees it.
 * @property error What went wrong with the last save or delete, for a snackbar; cleared once shown.
 */
data class JournalEditorState(
    val loading: Boolean,
    val isNew: Boolean,
    val entryDate: LocalDate,
    val text: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val error: UiText? = null
) {
    /** Whether Save does anything: there is text, and nothing is loading or saving. */
    val canSave: Boolean get() = !loading && !saving && text.isNotBlank()

    /** Whether the entry can be deleted from here: it exists, and nothing is loading or saving. */
    val canDelete: Boolean get() = !isNew && !loading && !saving
}

/**
 * Writes or edits one private journal entry (MON-22).
 *
 * An edit is a `copy()` of the loaded entry (CLAUDE.md "easy to get wrong" 2): the id, the time it
 * was first written and the family it was written in are kept, and only the day, the text and the
 * last-edited time change. A new entry is stamped with the family at create (item 18), read fresh
 * through [ParentsSource.coParentUid] — a save path never reads a shared flow's `.value` (item 17).
 */
@HiltViewModel
class JournalEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: JournalRepository,
    private val userRepository: UserRepository,
    private val parentsSource: ParentsSource
) : ViewModel() {

    private val entryId: String? = savedStateHandle.get<String>(ARG_ENTRY_ID)?.takeUnless { it == NEW_ENTRY }

    /** The entry as loaded, the base every edit is a copy of; null for a new one. */
    private var original: JournalEntry? = null

    private val _state = MutableStateFlow(
        JournalEditorState(loading = entryId != null, isNew = entryId == null, entryDate = LocalDate.now())
    )

    /** The form. */
    val state: StateFlow<JournalEditorState> = _state.asStateFlow()

    init {
        entryId?.let { id -> viewModelScope.launch { load(id) } }
    }

    private suspend fun load(id: String) {
        val uid = userRepository.getCurrentUserId()
        val entry = uid?.let { repository.getEntry(id, it) }
        original = entry
        _state.update { current ->
            if (entry == null) {
                // Gone since the list was drawn (deleted on another screen): the form opens empty,
                // and saving writes a new entry rather than resurrecting that one.
                current.copy(loading = false, isNew = true)
            } else {
                current.copy(loading = false, isNew = false, entryDate = entry.entryDate, text = entry.text)
            }
        }
    }

    /** Changes the day the entry is about. */
    fun setDate(date: LocalDate) = _state.update { it.copy(entryDate = date) }

    /** Changes the text. */
    fun setText(text: String) = _state.update { it.copy(text = text) }

    /** Clears the error once the screen has shown it. */
    fun errorShown() = _state.update { it.copy(error = null) }

    /** Saves the entry to this phone, and only this phone. */
    fun save() {
        val form = _state.value
        if (!form.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val uid = userRepository.getCurrentUserId()
                if (uid == null) {
                    _state.update { it.copy(error = UiText.Res(R.string.journal_error_signed_out)) }
                } else {
                    repository.save(entryFrom(form, uid))
                    _state.update { it.copy(saved = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "Journal entry not saved", e)
                _state.update { it.copy(error = UiText.Res(R.string.journal_error_save)) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    /**
     * Deletes the entry being edited, once the screen has confirmed it. The list's swipe was the
     * only way to delete one (docs/AUDIT-2026-10-design.md D-10); this is the visible route.
     */
    fun delete() {
        val entry = original ?: return
        if (!_state.value.canDelete) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.delete(entry.id, entry.createdByFirebaseUid)
                _state.update { it.copy(deleted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "Journal entry not deleted", e)
                _state.update { it.copy(error = UiText.Res(R.string.journal_error_delete)) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private suspend fun entryFrom(form: JournalEditorState, uid: String): JournalEntry {
        val now = System.currentTimeMillis()
        val text = form.text.trim()
        return original?.copy(entryDate = form.entryDate, text = text, updatedAtMillis = now)
            ?: JournalEntry(
                id = UUID.randomUUID().toString(),
                entryDate = form.entryDate,
                text = text,
                createdAtMillis = now,
                updatedAtMillis = now,
                familyId = FamilyKey.orNull(uid, parentsSource.coParentUid()),
                createdByFirebaseUid = uid
            )
    }

    /** The route argument, and the value it takes for a new entry. */
    companion object {
        /** The navigation argument naming the entry to edit. */
        const val ARG_ENTRY_ID = "entryId"

        /** The [ARG_ENTRY_ID] value that opens an empty form. */
        const val NEW_ENTRY = "new"

        private const val TAG = "JournalEditor"
    }
}
