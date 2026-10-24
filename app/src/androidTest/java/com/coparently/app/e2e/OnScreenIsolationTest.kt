package com.coparently.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.R
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.e2e.EmulatorEnvironment.step
import com.coparently.app.presentation.navigation.BottomNavDestination
import com.coparently.app.testing.settle
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/**
 * What Alice **sees** with her second family on screen: nothing of the first (isolation audit,
 * October 2026; `FamilyIsolationTest` is the data side).
 *
 * Bob, Alice's first co-parent, fills family A with an event today, an expense, a child and a pet,
 * each named with this run's canary; Alice's app syncs them and Home shows Bob's event — the
 * control that the canary reached her phone at all. Carol then pairs with Alice and fills family B
 * with records named with a marker. With family B on screen, each screen is walked — Home, the
 * calendar's Day view, Expenses, Child information, Pets — and on each the marker is awaited first,
 * so the screen has drawn family B's records, and then no node may show the canary in its text or
 * its content description.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnScreenIsolationTest : AliceOnScreenTest() {

    @Inject
    lateinit var selectedFamilySource: SelectedFamilySource

    @Test
    fun withTheSecondFamilyOnScreenNoScreenShowsTheFirstFamilysRecords() {
        val canary = "canaryA${shortId()}"
        val marker = "markerB${shortId()}"

        step("isolation: Bob fills family A")
        runBlocking { fill(bob, canary, listOf(aliceUid, bob.uid)) }
        runBlocking { syncService.performFullSync().getOrThrow() }
        waitFor("Bob's event on Alice's Home", showing(canary))
        step("isolation: Bob's expense on Expenses, which starts its listener")
        openExpenses()
        waitFor("Bob's expense on Alice's Expenses", showing(canary))
        openTab(BottomNavDestination.HOME)

        step("isolation: Carol pairs and fills family B")
        val carol = runBlocking { pairCarol() }
        runBlocking { fill(carol, marker, listOf(aliceUid, carol.uid)) }
        runBlocking {
            checkNotNull(selectedFamilySource.select(FamilyKey.of(aliceUid, carol.uid)))
            syncService.performFullSync().getOrThrow()
        }
        composeTestRule.settle()

        assertScreenShowsOnly("Home", marker, canary)
        openTab(BottomNavDestination.CALENDAR)
        tap(hasContentDescription(string(R.string.calendar_change_view_mode)) and hasClickAction())
        tap(hasText(string(R.string.calendar_viewmode_day)) and hasClickAction())
        assertScreenShowsOnly("the calendar's Day view", marker, canary)
        openExpenses()
        assertScreenShowsOnly("Expenses", marker, canary)
        Espresso.pressBackUnconditionally()
        composeTestRule.settle()
        openFamilyRow(R.string.settings_child_info_title)
        assertScreenShowsOnly("Child information", marker, canary)
        Espresso.pressBackUnconditionally()
        composeTestRule.settle()
        openFamilyRow(R.string.settings_pets_title)
        assertScreenShowsOnly("Pets", marker, canary)
    }

    /** Waits for [marker] on the screen, then fails if any node shows [canary]. */
    private fun assertScreenShowsOnly(screen: String, marker: String, canary: String) {
        waitFor("family B's records on $screen", showing(marker))
        val leaked = composeTestRule.onAllNodes(showing(canary), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$screen shows family A's records with family B on screen: $leaked", leaked.isEmpty())
    }

    /**
     * Pairs a third phone with Alice through the real callable and waits until Alice's open app
     * knows both families — its own pairing listener's job (`OnScreenFamiliesTest`).
     */
    private suspend fun pairCarol(): EmulatorParent {
        val carol = newParent("Carol")
        val invite = pairingRepository.createOrReuseInviteCode().getOrThrow()
        carol.pairingRepository.redeem(invite.code).getOrThrow()
        carol.awaitPairedWith(aliceUid)
        withTimeout(EmulatorParent.WAIT_MS) {
            while (selectedFamilySource.families().size < 2) delay(POLL_MS)
        }
        return carol
    }

    /** An event today, an expense, a child and a pet on [parent]'s phone, each named with [word]. */
    private suspend fun fill(parent: EmulatorParent, word: String, between: List<String>) {
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        parent.eventRepository.insertEvent(
            Event(
                id = UUID.randomUUID().toString(),
                title = "$word dentist",
                startDateTime = LocalDate.now().atTime(NOON, 0),
                eventType = "medical",
                parentOwner = parent.database.userDao().getUserById(parent.uid)?.role ?: "mom",
                createdAt = now,
                updatedAt = now
            )
        )
        parent.expenseRepository.addExpense(
            Expense(
                id = UUID.randomUUID().toString(),
                title = "$word shoes",
                amount = AMOUNT,
                currency = "EUR",
                category = ExpenseCategory.CLOTHING,
                paidBy = parent.uid,
                splitBetween = between,
                splitBasisPoints = HALF_BASIS_POINTS
            )
        )
        parent.childInfoRepository.upsertChildInfo(
            ChildInfo(
                id = UUID.randomUUID().toString(),
                childName = "$word Mia",
                dateOfBirth = null,
                createdAt = now,
                updatedAt = now,
                createdByFirebaseUid = parent.uid,
                lastModifiedBy = parent.uid
            )
        )
        parent.petRepository.upsertPet(
            Pet(
                id = UUID.randomUUID().toString(),
                name = "$word Rex",
                species = PetSpecies.DOG,
                createdAt = now,
                updatedAt = now,
                createdByFirebaseUid = parent.uid,
                lastModifiedBy = parent.uid
            )
        )
    }

    private companion object {
        const val NOON = 12
        const val AMOUNT = 80.0
        const val HALF_BASIS_POINTS = 5_000
        const val POLL_MS = 250L

        /** A node whose text, editable text or content description contains [needle]. */
        fun showing(needle: String) = SemanticsMatcher("shows $needle") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.contains(needle) } ||
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.contains(needle) } ||
                node.config.getOrNull(SemanticsProperties.EditableText)?.text?.contains(needle) == true
        }
    }
}
