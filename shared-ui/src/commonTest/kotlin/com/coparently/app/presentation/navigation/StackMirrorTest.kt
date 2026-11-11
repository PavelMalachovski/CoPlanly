package com.coparently.app.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [StackMirror], the rules the iPhone's stack host follows (MON-24 phase 3): how a native
 * stack follows the shared back stack, and how far the shared back stack follows the user's own
 * back — the back button, the edge swipe, a swipe started and cancelled, and a second tap on the
 * selected tab, which pops several screens at once.
 */
class StackMirrorTest {

    @Test
    fun `a pushed entry is pushed`() {
        assertEquals(listOf(StackChange.Push("c")), StackMirror.diff(listOf("a", "b"), listOf("a", "b", "c")))
    }

    @Test
    fun `popping two entries is one pop of two`() {
        assertEquals(listOf(StackChange.Pop(2)), StackMirror.diff(listOf("a", "b", "c"), listOf("a")))
    }

    @Test
    fun `a replaced top is a pop and a push`() {
        assertEquals(
            listOf(StackChange.Pop(1), StackChange.Push("c")),
            StackMirror.diff(listOf("a", "b"), listOf("a", "c"))
        )
    }

    @Test
    fun `the same stack changes nothing`() {
        assertEquals(emptyList(), StackMirror.diff(listOf("a"), listOf("a")))
    }

    @Test
    fun `a different first screen replaces the stack`() {
        assertEquals(listOf(StackChange.Replace(listOf("x"))), StackMirror.diff(listOf("a", "b"), listOf("x")))
    }

    @Test
    fun `an empty native stack is replaced`() {
        assertEquals(listOf(StackChange.Replace(listOf("a"))), StackMirror.diff(emptyList(), listOf("a")))
    }

    @Test
    fun `several entries pushed at once are pushed in order`() {
        assertEquals(
            listOf(StackChange.Push("b"), StackChange.Push("c")),
            StackMirror.diff(listOf("a"), listOf("a", "b", "c"))
        )
    }

    @Test
    fun `the back button pops one shared entry`() {
        assertEquals(1, StackMirror.poppedByUser(listOf("a", "b", "c"), listOf("a", "b"), listOf("a", "b", "c")))
    }

    @Test
    fun `a second tap on the selected tab pops every entry above the first`() {
        assertEquals(2, StackMirror.poppedByUser(listOf("a", "b", "c"), listOf("a"), listOf("a", "b", "c")))
    }

    @Test
    fun `a back swipe that was cancelled pops nothing`() {
        assertEquals(0, StackMirror.poppedByUser(listOf("a", "b"), listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun `a push the shared controller made during a transition is not a pop`() {
        assertEquals(0, StackMirror.poppedByUser(listOf("a", "b"), listOf("a", "b"), listOf("a", "b", "c")))
    }

    @Test
    fun `a pop the shared controller already made pops nothing more`() {
        assertEquals(0, StackMirror.poppedByUser(listOf("a", "b"), listOf("a"), listOf("a")))
    }

    @Test
    fun `a native stack that is no bottom of the asked one pops nothing`() {
        assertEquals(0, StackMirror.poppedByUser(listOf("a", "b"), listOf("x"), listOf("a", "b")))
    }

    @Test
    fun `an emptied native stack pops nothing`() {
        assertEquals(0, StackMirror.poppedByUser(listOf("a", "b"), emptyList(), listOf("a", "b")))
    }

    @Test
    fun `the user pop counts from the shared stack as it stands now`() {
        // The shared controller pushed d while UIKit popped c away: both above b go.
        assertEquals(2, StackMirror.poppedByUser(listOf("a", "b", "c"), listOf("a", "b"), listOf("a", "b", "c", "d")))
    }

    @Test
    fun `after the user pop the two stacks agree`() {
        val asked = listOf("a", "b", "c", "d")
        val shown = listOf("a", "b")
        val pops = StackMirror.poppedByUser(asked, shown, asked)
        assertEquals(emptyList(), StackMirror.diff(shown, asked.dropLast(pops)))
    }
}
