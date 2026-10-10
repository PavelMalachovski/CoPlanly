package com.coparently.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.R
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.pairing.InviteCodeGenerator
import com.coparently.app.e2e.EmulatorEnvironment.step
import com.coparently.app.presentation.navigation.BottomNavDestination
import com.coparently.app.testing.settle
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        val code = aliceCodeFromHerPairingScreen()
        val carol = runBlocking { pairCarolWithAlice(code) }
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
     * The code Alice, already Bob's co-parent, hands a second one — read off her own pairing
     * screen, the way a parent gets it: the gear, Co-parent pairing, Add another co-parent.
     *
     * Through the screen rather than the repository on purpose. The repository could always mint
     * a paired account a code, and every test took it from there, while the screen gave a paired
     * account none: nobody could invite a second co-parent, and every test passed. The row also
     * offers no field to enter somebody else's code, because entering one would move Alice's slot
     * in Bob's family too.
     */
    private fun aliceCodeFromHerPairingScreen(): String {
        step("families: Alice opens Add another co-parent")
        tap(hasContentDescription(string(R.string.nav_settings)) and hasClickAction())
        val pairingRow = hasText(string(R.string.settings_pairing_title)) and hasClickAction()
        waitFor("the co-parent pairing row in Settings", pairingRow)
        composeTestRule.onAllNodes(pairingRow).onFirst().performScrollTo()
        tap(pairingRow)
        val addAnother = hasText(string(R.string.pairing_add_another_title)) and hasClickAction()
        waitFor("Add another co-parent", addAnother)
        tap(addAnother)
        waitFor("Alice's code for another co-parent", inviteCode)
        assertTrue(
            "a paired account must not be offered a field for somebody else's code",
            composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()
        )
        val code = composeTestRule.onAllNodes(inviteCode).onFirst().fetchSemanticsNode()
            .config[SemanticsProperties.Text].map { it.text }.first(InviteCodeGenerator::isValid)
        step("families: back to Home")
        repeat(2) {
            Espresso.pressBackUnconditionally()
            composeTestRule.settle()
        }
        return code
    }

    /**
     * Pairs a third phone with Alice through the real callable, redeeming [code] from her screen,
     * then runs Alice's sync — the step that brings a second co-parent onto a phone (`partnerIds`
     * into Room, the selection reconciled) — and keeps Bob's family on screen, as a parent who has
     * not switched would have.
     */
    private suspend fun pairCarolWithAlice(code: String): EmulatorParent {
        step("families: pair Carol")
        val carol = newParent("Carol")
        carol.pairingRepository.redeem(code).getOrThrow()
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

    private companion object {
        /** A node showing an invitation code — `InviteCodeText` on the pairing screen's card. */
        val inviteCode = SemanticsMatcher("an invite code") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { InviteCodeGenerator.isValid(it.text) }
        }
    }
}
