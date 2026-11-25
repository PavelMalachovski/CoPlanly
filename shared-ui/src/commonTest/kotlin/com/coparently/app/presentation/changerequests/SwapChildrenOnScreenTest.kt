package com.coparently.app.presentation.changerequests

import com.coparently.app.data.family.FamilyView
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.ChildInfo
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A day swap names only the family on screen's own children (meeting of 25 November 2026, item 3;
 * CLAUDE.md item 46): never one placed "Only me", never another family's.
 */
class SwapChildrenOnScreenTest {

    private val at = LocalDateTime(2026, 1, 1, 9, 0)
    private val withBob = FamilyKey.of("alice", "bob")
    private val withCarol = FamilyKey.of("alice", "carol")

    private fun child(id: String, name: String, familyId: String?, createdBy: String?) = ChildInfo(
        id = id,
        childName = name,
        dateOfBirth = null,
        createdAt = at,
        updatedAt = at,
        createdByFirebaseUid = createdBy,
        familyId = familyId
    )

    private val children = listOf(
        child("a", "Lenička", withBob, "alice"),
        child("b", "Sasha", null, "alice"),
        child("c", "Ema", withCarol, "alice"),
        child("d", "Max", null, "bob")
    )

    @Test
    fun `a two-family account names the family on screen and none placed only me`() {
        val view = FamilyView(me = "alice", onScreen = withBob, others = setOf(withCarol))
        assertEquals(listOf("Lenička", "Max"), swapChildrenOnScreen(children, view, emptyList()))
    }

    @Test
    fun `the other family on screen names its own child alone`() {
        val view = FamilyView(me = "alice", onScreen = withCarol, others = setOf(withBob))
        assertEquals(listOf("Ema"), swapChildrenOnScreen(children, view, emptyList()))
    }

    @Test
    fun `an own child of no family is only me even with one family`() {
        val view = FamilyView(me = "alice", onScreen = withBob, others = emptySet())
        assertEquals(
            listOf("Lenička", "Max"),
            swapChildrenOnScreen(children.filter { it.id != "c" }, view, emptyList())
        )
    }
}
