package com.coparently.app.pushwording.golden

import com.coparently.app.domain.stickers.StickerKind
import com.coparently.app.presentation.stickers.StickerText
import com.coparently.app.pushwording.CardWording
import org.junit.Test
import kotlin.test.assertEquals

/**
 * `:push-wording` cannot read `StickerKind` (in `:shared`) or `StickerText` (in `:shared-ui`), so
 * `CardWording.stickerPhraseKey` spells each kind's wire value and neutral phrase itself. This holds
 * the two to every kind: a sticker card says in the thread and in its push what `StickerText` says.
 */
class CardWordingStickerTest {

    @Test
    fun everyStickerKindsPhraseIsStickerTextsNeutralPhrase() {
        for (kind in StickerKind.entries) {
            assertEquals(StickerText.phrase(kind).key, CardWording.stickerPhraseKey(kind.wire), kind.name)
        }
    }

    @Test
    fun aKindThisBuildDoesNotKnowIsAStickerOfNoKind() {
        assertEquals("activity_subject_sticker", CardWording.stickerPhraseKey("a_kind_from_a_newer_build"))
        assertEquals("activity_subject_sticker", CardWording.stickerPhraseKey(null))
    }
}
