package com.coparently.app.data.chat

import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The rule that decides which co-parent the chat is with (M-8).
 *
 * The server says *whether* there is a co-parent; the family switcher's projection says *which*.
 * Every case below is one where getting that split backwards shows the wrong thread, or a thread
 * for an account that has none.
 */
class ChatPartnerSourceTest {

    private fun paired(partnerUid: String = FIRST) = PairingState.Paired(
        partner = PartnerSummary(id = partnerUid, name = "", email = "", pairedSinceMillis = null)
    )

    @Test
    fun `the selected family wins over the server's first co-parent`() {
        assertEquals(ChatPartner.Linked(SECOND), ChatPartnerSource.resolve(paired(), SECOND))
    }

    @Test
    fun `a one-family account reads the same partner whether or not the row has caught up`() {
        assertEquals(ChatPartner.Linked(FIRST), ChatPartnerSource.resolve(paired(), null))
        assertEquals(ChatPartner.Linked(FIRST), ChatPartnerSource.resolve(paired(), FIRST))
        assertEquals(ChatPartner.Linked(FIRST), ChatPartnerSource.resolve(paired(), ""))
    }

    @Test
    fun `an unanswered pairing is resolving even when the row names somebody`() {
        // A cold start and a signed-out device both look like this; the row can outlive either.
        assertEquals(ChatPartner.Resolving, ChatPartnerSource.resolve(PairingState.Loading, SECOND))
    }

    @Test
    fun `an unpaired account has nobody, whatever a stale projection says`() {
        assertEquals(ChatPartner.None, ChatPartnerSource.resolve(PairingState.NotPaired(), SECOND))
    }

    @Test
    fun `a paired state with no usable id and no projection has nobody to chat with`() {
        assertEquals(ChatPartner.None, ChatPartnerSource.resolve(paired(partnerUid = ""), null))
    }

    private companion object {
        const val FIRST = "uid-bob"
        const val SECOND = "uid-carol"
    }
}
