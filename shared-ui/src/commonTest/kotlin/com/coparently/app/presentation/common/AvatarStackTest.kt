package com.coparently.app.presentation.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** How many of a family's children the brand header draws, and how many it folds into "+n". */
class AvatarStackTest {
    @Test
    fun `no children draw nothing`() {
        assertEquals(0, AvatarStack.visible(0))
        assertEquals(0, AvatarStack.overflow(0))
    }

    @Test
    fun `two children are both shown`() {
        assertEquals(2, AvatarStack.visible(2))
        assertEquals(0, AvatarStack.overflow(2))
    }

    @Test
    fun `three children fill the stack with no overflow`() {
        assertEquals(3, AvatarStack.visible(3))
        assertEquals(0, AvatarStack.overflow(3))
    }

    @Test
    fun `four children show three and fold one`() {
        assertEquals(3, AvatarStack.visible(4))
        assertEquals(1, AvatarStack.overflow(4))
    }
}
