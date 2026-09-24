package com.coparently.app.presentation.common

import androidx.compose.runtime.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.reflect.KProperty

/**
 * A form's fields, held by its ViewModel so a rotation keeps what was typed
 * (docs/AUDIT-2026-10-design.md D-11).
 *
 * The child and pet forms used to keep their fields in `remember`, which a configuration change
 * throws away: a parent who had typed out a medication and turned the phone lost it. Their nested
 * lists do not fit `rememberSaveable` without a serialisation of every domain type, and the
 * project's rule is that state lives in the ViewModel anyway, which outlives the rotation.
 *
 * It seeds **once per record**: the record a form edits is an observation that emits again on
 * every write to its row — a sync tick, the co-parent's edit — and copying each emission into the
 * fields is how an edit used to be overwritten mid-sentence. [Draft.dirty] compares the fields
 * with what they were seeded with, which is what the discard guard asks about.
 *
 * @param F The form's fields, a data class so two drafts compare by value
 * @param empty The fields of a blank form
 */
class FormDraft<F>(empty: F) {

    /**
     * What the form was seeded with, what it holds now, and which record it was seeded from.
     *
     * @property seed The fields as the record, or a blank form, had them
     * @property fields The fields as they are on screen
     * @property seededFor The id of the record [seed] came from, or null for a blank form
     */
    data class Draft<F>(val seed: F, val fields: F, val seededFor: String?) {
        /** Whether leaving now would drop an edit. */
        val dirty: Boolean get() = fields != seed
    }

    private val _state = MutableStateFlow(Draft(seed = empty, fields = empty, seededFor = null))

    /** The draft, for the form to render. */
    val state: StateFlow<Draft<F>> = _state.asStateFlow()

    /**
     * Seeds the form from the record [recordId], unless it already holds that record: a record
     * that arrives again leaves the fields, and whatever is being typed into them, alone.
     *
     * @param recordId The record's id
     * @param fields The record's values
     */
    fun seed(recordId: String, fields: F) {
        _state.update { draft -> if (draft.seededFor == recordId) draft else Draft(fields, fields, recordId) }
    }

    /**
     * Changes the fields.
     *
     * @param transform The new fields from the current ones
     */
    fun edit(transform: (F) -> F) {
        _state.update { draft -> draft.copy(fields = transform(draft.fields)) }
    }
}

/**
 * One field of a [FormDraft], as a local `var` a form reads and assigns: reading it subscribes
 * the composition to the draft, and assigning it edits the ViewModel's copy.
 *
 * @param draft The draft, collected as state by the form
 * @param read The field's value in the fields
 * @param write The fields with the field set to a new value
 */
class DraftField<F, T>(
    private val draft: State<FormDraft.Draft<F>>,
    private val read: (F) -> T,
    private val write: (T) -> Unit
) {
    /** The field's current value. */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = read(draft.value.fields)

    /** Edits the field. */
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = write(value)
}

/**
 * A [DraftField] over this collected draft, editing [form].
 *
 * @param form The draft the field belongs to
 * @param read The field's value in the fields
 * @param write The fields with the field set to a new value
 * @return A delegate for a local `var`
 */
fun <F, T> State<FormDraft.Draft<F>>.field(
    form: FormDraft<F>,
    read: (F) -> T,
    write: (F, T) -> F
): DraftField<F, T> = DraftField(this, read) { value -> form.edit { fields -> write(fields, value) } }
