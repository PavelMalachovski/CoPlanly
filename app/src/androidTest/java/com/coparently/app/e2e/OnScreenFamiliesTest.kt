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
 * proves what she **sees**: Carol's family reaching the switcher while the app is open, by Alice's
 * own pairing listener and without taking over the screen (tester report #4); with Bob's family on
 * screen, a message from Carol puts a dot on Home's switcher chip whose description names the kind
 * ("New messages in another family"), Carol's row in the switcher dialog says the same; switching
 * there makes the new-event form name Carol rather than Bob (tester report #11) and brings Carol's
 * thread onto the Chat tab (chat follows the selected family, `ChatPartnerSource`), and what Alice
 * types into it reaches Carol.
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
        // Nothing runs Alice's sync: her open app learns Carol's family from its own pairing
        // listener, which adds the co-parent the server's set gained while `partnerId` stays on Bob.
        waitFor("the family switcher chip on Home", switcher)
        runBlocking {
            assertEquals(
                "Alice's phone does not know both families",
                setOf(bob.uid, carol.uid),
                selectedFamilySource.families().map { it.partnerUid }.toSet()
            )
            // A new family joins the switcher; it does not take over the screen.
            assertEquals(
                "Carol's family took over Alice's screen",
                FamilyKey.of(aliceUid, bob.uid),
                selectedFamilySource.selected()?.familyId
            )
        }

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

        // The new-event form names the family on screen: the important mark asks for Carol, not
        // Bob, the co-parent the server's link still names (tester report #11). It named the
        // owner cards until they went (21 October 2026).
        step("families: the new-event form names Carol")
        openTab(BottomNavDestination.CALENDAR)
        tap(hasContentDescription(string(R.string.calendar_add_event)) and hasClickAction())
        waitFor("the important mark naming Carol", importantMarkNaming(carol.name))
        assertTrue(
            "the new-event form in Carol's family still names Bob",
            composeTestRule.onAllNodes(importantMarkNaming(bob.name)).fetchSemanticsNodes().isEmpty()
        )
        step("families: back to the calendar")
        Espresso.pressBackUnconditionally()
        composeTestRule.settle()

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
     * screen, the way a parent gets it: the Family tab, Co-parent pairing, Add another co-parent.
     *
     * Through the screen rather than the repository on purpose. The repository could always mint
     * a paired account a code, and every test took it from there, while the screen gave a paired
     * account none: nobody could invite a second co-parent, and every test passed. Entering
     * somebody else's code is the row's second mode (October 2026), checked by the server before
     * it can move Alice's slot in Bob's family.
     */
    private fun aliceCodeFromHerPairingScreen(): String {
        step("families: Alice opens Add another co-parent")
        openFamilyRow(R.string.settings_pairing_title)
        val addAnother = hasText(string(R.string.pairing_add_another_title)) and hasClickAction()
        waitFor("Add another co-parent", addAnother)
        tap(addAnother)
        waitFor("Alice's code for another co-parent", inviteCode)
        // The row opens on sharing; entering the other person's code is the toggle's second half.
        assertTrue(
            "Add another co-parent must open on sharing this parent's code, not on a code field",
            composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()
        )
        val code = composeTestRule.onAllNodes(inviteCode).onFirst().fetchSemanticsNode()
            .config[SemanticsProperties.Text].map { it.text }.first(InviteCodeGenerator::isValid)
        step("families: back to Home")
        repeat(2) {
            Espresso.pressBackUnconditionally()
            composeTestRule.settle()
        }
        // Back lands on the Family tab the pairing screen opened from.
        openTab(BottomNavDestination.HOME)
        return code
    }

    /**
     * Pairs a third phone with Alice through the real callable, redeeming [code] from her screen.
     *
     * Nothing is run on Alice's side: her app is open, and bringing the second family onto her
     * phone is its own pairing listener's job. This used to run her `SyncService.performFullSync()`
     * here, because that listener watched only `partnerId`, which the server keeps on Bob — an
     * open phone learnt of a second co-parent only on the next sync (tester report #4).
     */
    private suspend fun pairCarolWithAlice(code: String): EmulatorParent {
        step("families: pair Carol")
        val carol = newParent("Carol")
        carol.pairingRepository.redeem(code).getOrThrow()
        carol.awaitPairedWith(aliceUid)
        return carol
    }

    /** The event form's important mark, asking for [name] (`AddEditEventScreen`). */
    private fun importantMarkNaming(name: String): SemanticsMatcher =
        hasText(string(R.string.event_form_important_description_named, name))

    private companion object {
        /** A node showing an invitation code — `InviteCodeText` on the pairing screen's card. */
        val inviteCode = SemanticsMatcher("an invite code") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { InviteCodeGenerator.isValid(it.text) }
        }
    }
}
