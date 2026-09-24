package com.coparently.app.presentation.common

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The draft the child and pet forms keep in their ViewModels (docs/AUDIT-2026-10-design.md D-11):
 * seeded once per record, so a record arriving again — a sync write, or the same record after a
 * rotation — never overwrites what is being typed, and dirty only while the fields differ from
 * what they were seeded with.
 */
class FormDraftTest {

    private data class Fields(val name: String = "", val notes: String = "")

    @Test
    fun `a new form is blank and has nothing to lose`() {
        val draft = FormDraft(Fields())

        assertEquals(Fields(), draft.state.value.fields)
        assertFalse(draft.state.value.dirty)
    }

    @Test
    fun `an edit is dirty until the fields match the seed again`() {
        val draft = FormDraft(Fields())
        draft.seed("pet-1", Fields(name = "Rex"))

        draft.edit { it.copy(name = "Rexy") }
        assertTrue(draft.state.value.dirty)

        draft.edit { it.copy(name = "Rex") }
        assertFalse(draft.state.value.dirty)
    }

    @Test
    fun `the same record arriving again keeps what was typed`() {
        val draft = FormDraft(Fields())
        draft.seed("child-1", Fields(name = "Emma"))
        draft.edit { it.copy(notes = "Peanut allergy") }

        // A sync tick re-emits the record, or the editor seeds again after a rotation.
        draft.seed("child-1", Fields(name = "Emma", notes = "from the server"))

        assertEquals(Fields(name = "Emma", notes = "Peanut allergy"), draft.state.value.fields)
        assertTrue(draft.state.value.dirty)
    }

    @Test
    fun `another record replaces the fields and the seed`() {
        val draft = FormDraft(Fields())
        draft.seed("child-1", Fields(name = "Emma"))
        draft.edit { it.copy(notes = "typed") }

        draft.seed("child-2", Fields(name = "Leo"))

        assertEquals(Fields(name = "Leo"), draft.state.value.fields)
        assertEquals("child-2", draft.state.value.seededFor)
        assertFalse(draft.state.value.dirty)
    }
}
