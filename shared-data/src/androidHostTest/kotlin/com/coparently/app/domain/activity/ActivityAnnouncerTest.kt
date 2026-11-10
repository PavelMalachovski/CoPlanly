package com.coparently.app.domain.activity

import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Which thread a card is posted to.
 *
 * A parent in two families has two threads, and the signed-in user's `partnerId` is only the one
 * the switcher shows. A card posted by a screen belongs there. A card posted by a background step
 * belongs to the family its clock names (spec §5.6), whatever the switcher shows by then — posting
 * "No answer" to the other family's co-parent is the defect this pins.
 */
class ActivityAnnouncerTest {

    private val messageRepository = mockk<MessageRepository>()
    private val userRepository = mockk<UserRepository>()
    private val announcer = ActivityAnnouncer(messageRepository, userRepository)

    private val sent = slot<Message>()

    @Before
    fun setUp() {
        coEvery { userRepository.getCurrentUserId() } returns ME
        // The family on screen is Carol's, which is all the user's row says.
        coEvery { userRepository.getUserById(ME) } returns me(partnerId = ON_SCREEN)
        coEvery { messageRepository.sendMessage(capture(sent)) } returns Unit
    }

    @Test
    fun `a named co-parent's thread is used whatever family is on screen`() = runTest {
        announcer.announce(CARD, senderName = "Alice", partnerUid = "bob")

        assertEquals(ConversationKey.of(ME, "bob"), sent.captured.conversationId)
    }

    @Test
    fun `a split proposal's fallback names the sender's own share, from their slot`() = runTest {
        // Alice is slot 1 ("mom"); the card carries slot 1's share, 35 %.
        val proposed = ActivityAnnouncement(
            kind = ActivityKind.SPLIT_PROPOSED,
            entityType = ActivityEntityType.SPLIT_PROPOSAL,
            entityId = "alice__bob",
            title = "",
            amount = "3500"
        )

        announcer.announce(proposed, partnerUid = "bob")

        assertEquals("Proposed a new expense split: I pay 35 %, you pay 65 %", sent.captured.content)
        assertEquals(proposed, sent.captured.activity)
    }

    @Test
    fun `without a name it is the family on screen, as before`() = runTest {
        announcer.announce(CARD, senderName = "Alice")

        assertEquals(ConversationKey.of(ME, ON_SCREEN), sent.captured.conversationId)
    }

    private fun me(partnerId: String?) = User(
        id = ME,
        email = "alice@example.com",
        name = "Alice",
        role = "mom",
        colorCode = "",
        partnerId = partnerId
    )

    private companion object {
        const val ME = "alice"
        const val ON_SCREEN = "carol"

        /** The end of a custody proposal made in Bob's family, which is not the one on screen. */
        val CARD = ActivityAnnouncement(
            kind = ActivityKind.REQUEST_UNANSWERED,
            entityType = ActivityEntityType.CUSTODY_PROPOSAL,
            entityId = "alice__bob",
            title = ""
        )
    }
}
