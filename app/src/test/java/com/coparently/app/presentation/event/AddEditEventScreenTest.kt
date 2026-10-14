package com.coparently.app.presentation.event

import com.coparently.app.domain.events.EventAcceptanceTransition
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [showParentOwnerSelector] decides whether AddEditEventScreen's parent-owner picker renders.
 *
 * These four cases are the ones a code-review round pinned after the first fix only closed the
 * gap `Parents.loaded` can see: `parentOwner` starts null and is filled in by a `LaunchedEffect`
 * that runs one composition *after* `parents` resolves, so gating on `parentOwner == null` alone
 * still let the picker flash open for a frame for an unpaired account whose slot had already
 * resolved. Falling back to `currentUserSlot` — the value that effect is about to assign —
 * closes it by construction instead of by narrowing the window.
 */
class AddEditEventScreenTest {

    @Test
    fun `hidden before the upstream has emitted once, regardless of the rest`() {
        assertFalse(
            showParentOwnerSelector(
                parentsLoaded = false,
                isPaired = false,
                parentOwner = null,
                currentUserSlot = null
            )
        )
        assertFalse(
            showParentOwnerSelector(
                parentsLoaded = false,
                isPaired = true,
                parentOwner = null,
                currentUserSlot = "mom"
            )
        )
    }

    @Test
    fun `a family of one whose own slot already resolved is hidden immediately, not after a frame`() {
        // The case the first fix round left open: parentOwner has not caught up to
        // currentUserSlot yet (the LaunchedEffect that copies it hasn't run this composition),
        // but there is nobody to choose between either way.
        assertFalse(
            showParentOwnerSelector(
                parentsLoaded = true,
                isPaired = false,
                parentOwner = null,
                currentUserSlot = "mom"
            )
        )
    }

    @Test
    fun `an account with no Room profile row is shown so it has an escape from a disabled Save`() {
        assertTrue(
            showParentOwnerSelector(
                parentsLoaded = true,
                isPaired = false,
                parentOwner = null,
                currentUserSlot = null
            )
        )
    }

    @Test
    fun `a legacy pair is shown even though the co-parent's slot is not known yet`() {
        assertTrue(
            showParentOwnerSelector(
                parentsLoaded = true,
                isPaired = true,
                parentOwner = null,
                currentUserSlot = "mom"
            )
        )
    }

    @Test
    fun `once an owner is assigned - a loaded event, say - an unpaired account no longer sees it`() {
        assertFalse(
            showParentOwnerSelector(
                parentsLoaded = true,
                isPaired = false,
                parentOwner = "dad",
                currentUserSlot = null
            )
        )
    }

    /** One state of the form when the hint is decided. */
    private data class HintCase(val isPaired: Boolean, val isPrivate: Boolean, val owner: String?, val mySlot: String?)

    /** Every combination of the four, so nothing is left to the rule's own reading. */
    private val hintCases: List<HintCase> =
        listOf(true, false).flatMap { isPaired ->
            listOf(true, false).flatMap { isPrivate ->
                listOf("mom", "dad", null).flatMap { owner ->
                    listOf("mom", null).map { mySlot -> HintCase(isPaired, isPrivate, owner, mySlot) }
                }
            }
        }

    @Test
    fun `the description hint follows the rule that makes an event wait for an answer`() {
        // Phase 2, spec §5.2: the hint promises the co-parent will read the description when they
        // answer, so it may appear exactly when the event will wait for their answer.
        val at = LocalDateTime.of(2026, 10, 21, 16, 0)
        val event = Event(
            id = "e1",
            title = "Dentist",
            startDateTime = at,
            eventType = "medical",
            parentOwner = "mom",
            createdAt = at,
            updatedAt = at
        )
        for (case in hintCases) {
            val waits = EventAcceptanceTransition.required(
                event.copy(isPrivate = case.isPrivate, parentOwner = case.owner ?: "mom"),
                case.mySlot,
                if (case.isPaired) "u2" else null
            )
            val newEvent = descriptionReachesCoParent(true, case.isPaired, case.isPrivate, case.owner, case.mySlot)

            assertEquals(waits, newEvent, "$case")
            if (case.owner == null) assertFalse(newEvent, "$case")
            // An edit never asks again, so its description reaches nobody new.
            val edit = descriptionReachesCoParent(false, case.isPaired, case.isPrivate, case.owner, case.mySlot)
            assertFalse(edit, "$case")
        }
    }
}
