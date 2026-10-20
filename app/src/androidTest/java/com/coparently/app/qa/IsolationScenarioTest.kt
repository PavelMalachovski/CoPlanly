package com.coparently.app.qa

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.R
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.e2e.EmulatorParent
import com.coparently.app.presentation.navigation.BottomNavDestination
import com.coparently.app.testing.exists
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Two families, kept apart — the defect the testers found. Zuzana co-parents Lena with Igor and
 * Max with Carol. Each co-parent writes an event for today, an expense, a child's record and a
 * message in their own family; the scenario then looks, with each family on screen in turn, for
 * the family's own records on Home, Expenses and Chat and for the other family's nowhere — and on
 * Igor's and Carol's phones, after their syncs, for nothing of the family they are not in.
 *
 * What it does not cover yet, and the report's coverage list says so: custody, documents, photos,
 * requests and their clocks across families (`MultiFamilyCustodyTest`, `MultiFamilyRecordsTest`
 * and `OnScreenFamiliesTest` hold those in the e2e job).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class IsolationScenarioTest : QaOnScreenTest() {

    @Inject
    lateinit var selectedFamilySource: SelectedFamilySource

    /** The family with Igor on screen, Carol's beside it. */
    @Test
    fun familyOneOnScreen() = walk(carolOnScreen = false)

    /** The family with Carol on screen, Igor's beside it. */
    @Test
    fun familyTwoOnScreen() = walk(carolOnScreen = true)

    private class Records(val title: String, val expense: String, val child: String, val message: String)

    @Suppress("LongMethod") // one story, step by step, reads best in one place
    private fun walk(carolOnScreen: Boolean) {
        val id = shortId()
        val igorRecords = Records("Zubař Lena $id", "Boty Lena $id", "Lena", "Vyzvednu Lenu v pátek $id")
        val carolRecords = Records("Plavání Max $id", "Kroužek Max $id", "Max", "Max má trénink v úterý $id")
        lateinit var carol: EmulatorParent

        report.step(
            id = "setup",
            title = "Pair Carol as a second co-parent and let each co-parent write in their own family",
            expected = "Zuzana's phone knows two families; each family holds an event, an expense, a child and a message"
        ) {
            runBlocking {
                carol = pairThirdParent(CAROL)
                withTimeout(EmulatorParent.WAIT_MS) {
                    while (selectedFamilySource.families().size < 2) delay(POLL_MS)
                }
                write(bob, igorRecords, born = LocalDate.of(2017, 3, 14))
                write(carol, carolRecords, born = LocalDate.of(2019, 11, 2))
            }
        }

        val mine = if (carolOnScreen) carolRecords else igorRecords
        val theirs = if (carolOnScreen) igorRecords else carolRecords
        val onScreenCoParent = if (carolOnScreen) CAROL else coParentName
        val otherCoParent = if (carolOnScreen) coParentName else CAROL

        if (carolOnScreen) {
            report.step(
                id = "switch",
                title = "Switch to the family with Carol",
                expected = "The switcher lists Carol's family and the app shows it"
            ) {
                val switcher = hasContentDescription(string(R.string.settings_family_switch), substring = true) and
                    hasClickAction()
                waitFor("the family switcher on Home", switcher)
                tap(switcher)
                val carolsRow = hasAnyAncestor(isDialog()) and isSelectable() and hasText(CAROL, substring = true)
                waitFor("Carol's row in the switcher", carolsRow)
                tap(carolsRow)
                runBlocking {
                    withTimeout(EmulatorParent.WAIT_MS) {
                        while (selectedFamilySource.selected()?.familyId != FamilyKey.of(aliceUid, carol.uid)) {
                            delay(POLL_MS)
                        }
                    }
                }
            }
        }

        report.step(
            id = "home_today",
            title = "Home's today card in the family with $onScreenCoParent",
            expected = "\"${mine.title}\" is on Home; \"${theirs.title}\" from the family with $otherCoParent is not"
        ) {
            openTab(BottomNavDestination.HOME)
            awaitDelivered(this, "today's event from $onScreenCoParent", hasText(mine.title, substring = true), false)
            check(!composeTestRule.exists(hasText(theirs.title, substring = true))) {
                "the other family's event \"${theirs.title}\" is shown on Home"
            }
        }

        report.step(
            id = "expenses_list",
            title = "The Expenses tab in the family with $onScreenCoParent",
            expected = "\"${mine.expense}\" is listed; \"${theirs.expense}\" is not"
        ) {
            openTab(BottomNavDestination.EXPENSES)
            awaitDelivered(this, "the family's expense", hasText(mine.expense, substring = true), false)
            check(!composeTestRule.exists(hasText(theirs.expense, substring = true))) {
                "the other family's expense \"${theirs.expense}\" is listed"
            }
        }

        report.step(
            id = "chat_thread",
            title = "The Chat tab in the family with $onScreenCoParent",
            expected = "$onScreenCoParent's message is in the thread; $otherCoParent's is not"
        ) {
            openTab(BottomNavDestination.CHAT)
            awaitDelivered(this, "$onScreenCoParent's message", hasText(mine.message, substring = true), true)
            check(!composeTestRule.exists(hasText(theirs.message, substring = true))) {
                "$otherCoParent's message is shown in the thread with $onScreenCoParent"
            }
        }

        report.step(
            id = "switcher_dot",
            title = "The switcher says the other family has something new",
            expected = "Home's family chip carries the dot for new messages in another family"
        ) {
            openTab(BottomNavDestination.HOME)
            waitFor(
                "the dot naming new messages in another family",
                hasContentDescription(string(R.string.family_switcher_unread_other), substring = true)
            )
        }

        report.step(
            id = "coparents_phones",
            title = "What reaches Igor's and Carol's phones after their syncs",
            expected = "Igor has only his family's event, expense and child; Carol only hers",
            independent = true
        ) {
            runBlocking {
                bob.syncService.performFullSync().getOrThrow()
                carol.syncService.performFullSync().getOrThrow()
                bob.childInfoRepository.pullOnce()
                carol.childInfoRepository.pullOnce()
                val leaks = buildList {
                    addAll(leaksOn(bob, IGOR_PHONE, forbidden = carolRecords))
                    addAll(leaksOn(carol, CAROL_PHONE, forbidden = igorRecords))
                }
                check(leaks.isEmpty()) { leaks.joinToString("; ") }
            }
        }

        report.step(
            id = "pushes",
            title = "Every push names the family it belongs to",
            expected = "Every push to Zuzana names one of her two families, and each family has its own",
            independent = true
        ) {
            val igorFamily = FamilyKey.of(aliceUid, bob.uid)
            val carolFamily = FamilyKey.of(aliceUid, carol.uid)
            // `queuedFor` reads every push addressed to Zuzana, whoever queued it; a push carries
            // no sender, so what is checked is the family each one names.
            val named = bob.queuedFor(aliceUid).map { push ->
                val data = push["data"] as? Map<*, *>
                (data?.get(PushPayload.TYPE) as? String) to (data?.get(PushPayload.FAMILY_ID) as? String)
            }
            val stray = named.filter { (_, family) -> family != null && family != igorFamily && family != carolFamily }
            check(stray.isEmpty()) { "pushes naming a family Zuzana is not in: $stray" }
            check(named.any { it.second == igorFamily }) { "no push names the family with Igor: $named" }
            check(named.any { it.second == carolFamily }) { "no push names the family with Carol: $named" }
            actual = "${named.size} pushes, each naming one of Zuzana's families"
        }
    }

    private suspend fun write(parent: EmulatorParent, records: Records, born: LocalDate) {
        val start = minOf(LocalDateTime.now().plusMinutes(30), LocalDate.now().atTime(23, 30))
        QaFamily.event(parent, aliceUid, records.title, start)
        QaFamily.expense(parent, aliceUid, records.expense, EXPENSE_CZK)
        QaFamily.child(parent, aliceUid, records.child, born)
        QaFamily.message(parent, aliceUid, records.message)
    }

    private suspend fun leaksOn(phone: EmulatorParent, label: String, forbidden: Records): List<String> {
        val events = phone.eventRepository.getAllEvents().first().map { it.title }
        val expenses = phone.expenseRepository.getAllExpenses().first().map { it.title }
        val children = phone.childInfoRepository.getAllChildInfo().first().map { it.childName }
        return buildList {
            if (forbidden.title in events) add("$label holds the other family's event \"${forbidden.title}\"")
            if (forbidden.expense in expenses) add("$label holds the other family's expense \"${forbidden.expense}\"")
            if (forbidden.child in children) add("$label holds the other family's child ${forbidden.child}")
        }
    }

    private companion object {
        const val CAROL = "Carol"
        const val IGOR_PHONE = "Igor's phone"
        const val CAROL_PHONE = "Carol's phone"
        const val EXPENSE_CZK = 1_290.0
        const val POLL_MS = 250L
    }
}
