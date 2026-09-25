package com.coparently.app.domain.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shape of the chat's message templates (`docs/TEMPLATES-AUDIT-2026-09.md`).
 *
 * The wording lives in the five `chat_strings.xml` files and is reviewed there; what a JVM test
 * can hold is the structure the sheet relies on: one resource per title and body, categories in
 * contiguous runs (the sheet groups in first-seen order, so a template out of its run would
 * repeat a heading), and a set small enough to scan.
 */
class MessageTemplatesTest {

    private val templates = DefaultMessageTemplates.getAll()

    @Test
    fun `ids are unique`() {
        assertEquals(templates.size, templates.map { it.id }.toSet().size)
    }

    @Test
    fun `every title and body is its own resource`() {
        val resources = templates.flatMap { listOf(it.titleRes, it.contentRes) }
        assertEquals(resources.size, resources.toSet().size)
    }

    @Test
    fun `each category is one run, in the enum's order, and none is empty`() {
        val runs = templates.map { it.category }.fold(emptyList<TemplateCategory>()) { acc, category ->
            if (acc.lastOrNull() == category) acc else acc + category
        }
        assertEquals(TemplateCategory.entries.toList(), runs)
    }

    @Test
    fun `the set stays small enough to scan`() {
        assertTrue(templates.size in MIN_TEMPLATES..MAX_TEMPLATES, "${templates.size} templates")
    }

    @Test
    fun `every template names what the parent fills in`() {
        assertTrue(templates.all { it.placeholders.isNotEmpty() })
    }

    private companion object {
        const val MIN_TEMPLATES = 12
        const val MAX_TEMPLATES = 16
    }
}
