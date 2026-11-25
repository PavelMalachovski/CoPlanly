package com.coparently.app.presentation.chat

import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
import kotlin.test.Test
import kotlin.test.assertEquals

/** How the thread names a message's author: by the family's names first, the stored name last. */
class ChatAuthorTest {

    private val names = ParentNames(
        parents = Parents(
            me = NamedParent(uid = "alice", slot = "mom", name = "Alice"),
            coParent = NamedParent(uid = "bob", slot = "dad", name = "Robert"),
            isPaired = true,
            loaded = true
        ),
        youFallback = "You",
        coParentFallback = "Co-parent",
        unknownFallback = "Parent"
    )

    @Test
    fun `a renamed co-parent is named as the family names them now`() {
        assertEquals("Robert", names.chatAuthor("bob", "Bob"))
    }

    @Test
    fun `an author outside the family keeps the name the message stored`() {
        assertEquals("Carol", names.chatAuthor("carol", "Carol"))
    }

    @Test
    fun `an author outside the family with no stored name is the unknown fallback`() {
        assertEquals("Parent", names.chatAuthor("carol", ""))
    }

    @Test
    fun `with no names yet the stored name is used`() {
        val none: ParentNames? = null
        assertEquals("Bob", none.chatAuthor("bob", "Bob"))
    }
}
