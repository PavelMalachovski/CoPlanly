package com.coparently.app.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelectable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.R
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Message
import com.coparently.app.e2e.EmulatorEnvironment.step
import com.coparently.app.presentation.navigation.BottomNavDestination
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject

/**
 * The family switcher (M-8, design item 13) on Alice's real screens, with two co-parents: Bob,
 * paired before the test, and Carol, a third phone paired during it.
 *
 * `MultiFamilyTest` proves the data side — what Alice writes in Carol's family stays there. This
 * proves what she **sees**: with Bob's family on screen, a message from Carol puts a dot on Home's
 * switcher chip whose description names the kind ("New messages in another family"), Carol's row
 * in the switcher dialog says the same, switching there brings Carol's thread onto the Chat tab
 * (chat follows the selected family, `ChatPartnerSource`), and what Alice types into it reaches
 * Carol.
 *
 * What a phone still adds (`docs/DEVICE-CHECKLIST.md` §5.2): the push from the family not on
 * screen switching families when tapped, the chip on Expenses, TalkBack reading the dot, and two
 * or three screens at once.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnScreenFamiliesTest : AliceOnScreenTest() {

    @Inject
    lateinit var selectedFamilySource: SelectedFamilySource

    @Test
    fun carolsMessageDotsTheSwitcherAndSwitchingBringsHerThreadOntoTheChatTab() {
        val carol = runBlocking { pairCarolWithAlice() }
        val switcher = hasContentDescription(string(R.string.settings_family_switch), substring = true) and
            hasClickAction()
        waitFor("the family switcher chip on Home", switcher)

        // Carol writes while Bob's family is on screen: a dot, never a count, naming the kind.
        val carolThread = ConversationKey.of(aliceUid, carol.uid)
        val fromCarol = "Can you take her on Friday? ${shortId()}"
        runBlocking {
            carol.messageRepository.sendMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = carolThread,
                    senderId = carol.uid,
                    senderName = carol.name,
                    content = fromCarol
                )
            )
        }
        waitFor(
            "the dot on the switcher naming new messages",
            hasContentDescription(string(R.string.family_switcher_unread_other), substring = true)
        )

        // The dialog: Carol's row, not selected, saying what waits there. Tapping it switches.
        tap(switcher)
        val carolsRow = hasAnyAncestor(isDialog()) and isSelectable() and isNotSelected() and
            hasText(carol.name, substring = true) and
            hasText(string(R.string.family_switcher_unread_row), substring = true)
        waitFor("Carol's row in the switcher dialog", carolsRow)
        tap(carolsRow)

        // Chat follows the selected family: the tab opens Carol's thread, in place.
        step("families: open the Chat tab")
        openTab(BottomNavDestination.CHAT)
        waitFor("Carol's message in Alice's thread", hasText(fromCarol))

        val fromAlice = "Yes, I'll pick her up ${shortId()}"
        step("families: reply to Carol")
        sendInChat(fromAlice)
        runBlocking {
            withTimeout(EmulatorParent.WAIT_MS) {
                carol.messageRepository.observeMessages(carolThread).first { list ->
                    list.any { it.content == fromAlice && it.senderId == aliceUid }
                }
            }
        }
    }

    /**
     * Pairs a third phone with Alice through the real callable, then runs Alice's sync — the step
     * that brings a second co-parent onto a phone (`partnerIds` into Room, the selection
     * reconciled) — and keeps Bob's family on screen, as a parent who has not switched would have.
     */
    private suspend fun pairCarolWithAlice(): EmulatorParent {
        step("families: pair Carol")
        val carol = newParent("Carol")
        val invite = pairingRepository.createOrReuseInviteCode().getOrThrow()
        carol.pairingRepository.redeem(invite.code).getOrThrow()
        carol.awaitPairedWith(aliceUid)
        step("families: Alice's sync brings the second family")
        syncService.performFullSync().getOrThrow()
        assertEquals(
            "Alice's sync did not bring Carol's family",
            setOf(bob.uid, carol.uid),
            selectedFamilySource.families().map { it.partnerUid }.toSet()
        )
        assertNotNull(
            "Alice's phone does not know Bob's family",
            selectedFamilySource.select(FamilyKey.of(aliceUid, bob.uid))
        )
        return carol
    }
}
