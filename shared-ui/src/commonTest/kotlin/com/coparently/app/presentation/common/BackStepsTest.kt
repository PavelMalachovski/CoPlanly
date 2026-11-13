package com.coparently.app.presentation.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackStepsTest {

    @Test
    fun `no step answers false`() {
        assertFalse(BackSteps().handle())
    }

    @Test
    fun `a disabled step is skipped`() {
        val steps = BackSteps()
        val ran = mutableListOf<String>()
        steps.register(BackStep(enabled = true) { ran += "first" })
        steps.register(BackStep(enabled = false) { ran += "second" })

        assertTrue(steps.handle())
        assertEquals(listOf("first"), ran)
    }

    @Test
    fun `the latest enabled step runs first`() {
        val steps = BackSteps()
        val ran = mutableListOf<String>()
        steps.register(BackStep(enabled = true) { ran += "first" })
        steps.register(BackStep(enabled = true) { ran += "second" })

        assertTrue(steps.handle())
        assertEquals(listOf("second"), ran)
    }

    @Test
    fun `an unregistered step never runs`() {
        val steps = BackSteps()
        var ran = false
        val unregister = steps.register(BackStep(enabled = true) { ran = true })
        unregister()

        assertFalse(steps.handle())
        assertFalse(ran)
        assertFalse(steps.hasEnabledStep)
    }

    @Test
    fun `hasEnabledStep follows enabled`() {
        val steps = BackSteps()
        val step = BackStep(enabled = false) { }
        steps.register(step)
        assertFalse(steps.hasEnabledStep)

        step.enabled = true
        assertTrue(steps.hasEnabledStep)

        step.enabled = false
        assertFalse(steps.hasEnabledStep)
    }
}
