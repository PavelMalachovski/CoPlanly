package com.coparently.app.presentation.stickers

import com.coparently.app.R
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.stickers.StickerKind
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A sticker is worded by its receiver's own answer, and neutrally when there is none — never by a
 * guess.
 */
class StickerTextTest {

    @Test
    fun `great parent follows the receiver's answer`() {
        assertEquals(R.string.sticker_great_parent_dad, StickerText.sentence(StickerKind.GREAT_PARENT, ParentRole.DAD))
        assertEquals(R.string.sticker_great_parent_mom, StickerText.sentence(StickerKind.GREAT_PARENT, ParentRole.MOM))
        assertEquals(
            R.string.sticker_great_parent_parent,
            StickerText.sentence(StickerKind.GREAT_PARENT, ParentRole.PARENT)
        )
    }

    @Test
    fun `no answer words every sticker neutrally`() {
        StickerKind.entries.forEach { kind ->
            assertEquals(StickerText.sentence(kind, ParentRole.PARENT), StickerText.sentence(kind, null), kind.name)
        }
    }

    @Test
    fun `the app's stickers say dad, mom or parent, the plain phrases say the same to everybody`() {
        listOf(StickerKind.ON_TIME, StickerKind.REGULAR, StickerKind.GREAT_PARENT).forEach { kind ->
            assertEquals(3, ParentRole.entries.map { StickerText.sentence(kind, it) }.toSet().size, kind.name)
        }
        val byRole = setOf(StickerKind.ON_TIME, StickerKind.REGULAR, StickerKind.GREAT_PARENT)
        (StickerKind.entries - byRole).forEach { kind ->
            assertEquals(1, ParentRole.entries.map { StickerText.sentence(kind, it) }.toSet().size, kind.name)
        }
    }

    @Test
    fun `a sender reads the neutral phrase`() {
        assertEquals(R.string.sticker_great_parent_parent, StickerText.phrase(StickerKind.GREAT_PARENT))
        assertEquals(StickerKind.entries.size, StickerKind.entries.map { StickerText.phrase(it) }.toSet().size)
    }
}
