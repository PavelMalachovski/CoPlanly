package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.custody.CustodyDecisionOutcome
import com.coparently.app.domain.custody.CustodyKey
import com.coparently.app.domain.custody.CustodyWriteKind
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DayOverrideTransition
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.custody.SharedCustodyRead
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * The shared custody schedule between two phones: a pattern change that waits for the co-parent,
 * and one-off day swaps, single and grouped — every write through `CustodyModelRepository` on the
 * phone that makes it, every read through the same repository's listener on the other.
 *
 * What it proves, against the real `firestore.rules`:
 * - **A change to an agreed pattern is a proposal** (item 7 of the Aug 2026 walkthrough, CLAUDE.md
 *   items 24/30/33 ride on the same path): the proposer's calendar keeps the agreed pattern, the
 *   co-parent's phone sees the proposal through `observeShared`, accepting moves **both** Rooms to
 *   it, and a declined second proposal leaves the accepted one in force on both.
 * - **Both phones hold the same instant for the agreed pattern** although they run twenty-five
 *   hours apart (SEC-4): the accepter stamps `lastModifiedAtMillis`, and the proposer reads it
 *   back from the wire's UTC string in another zone.
 * - **A swap is an agreement, not an announcement**: an offer reaches the other phone pending,
 *   the offerer cannot answer it by writing the document directly (the rule refuses it), and the
 *   answer lands on both phones.
 * - **A run of days is one offer and one answer**: one `day_swap_group_*` push carrying the day
 *   count, not one push per day.
 * - Every push is the production `notification_queue` document, addressed to the other parent.
 *
 * What it cannot: the delivery of those pushes (FCM has no emulator), the calendar grid and the
 * banners that draw all this, and a co-parent on an older build that drops the newer keys.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentCustodyTest : TwoParentTest() {

    @Test
    fun aPatternChangeWaitsForTheCoParentAndADeclineKeepsTheAgreedOne() = runBlocking<Unit> {
        val agreed = agreeFirstPattern()

        EmulatorEnvironment.step("custody: Alice proposes a 2-2-3")
        val proposed = inZone(ALICE_ZONE) {
            assertEquals(
                PatternSubmission.PROPOSED,
                alice.custodyRepository.createTwoTwoThree(START, momStartsFirst = true)
            )
            // The proposer's calendar keeps the agreed pattern until the co-parent answers.
            assertEquals(agreed.id, alice.custodyRepository.getActiveModelSync()?.id)
            // And a parent may never decide their own proposal.
            assertTrue(alice.custodyRepository.acceptProposal().isFailure)
            checkNotNull(awaitShared(alice) { it.proposal?.proposedBy == alice.uid }.proposal).model
        }
        assertEquals(CustodyModelType.TWO_TWO_THREE, proposed.modelType)
        EmulatorEnvironment.awaitQueuedPush(bob.uid, "custody_proposal_proposed")

        acceptOnBobsPhone(agreed, proposed)
        declineASecondProposal(proposed)
    }

    @Test
    fun aSingleDaySwapIsOfferedOnOnePhoneAndAnsweredOnTheOther() = runBlocking<Unit> {
        val agreed = agreeFirstPattern()
        val aliceDay = START.plusDays(ALICE_OFFER_OFFSET).toString()
        val bobDay = START.plusDays(BOB_OFFER_OFFSET).toString()

        EmulatorEnvironment.step("swap: Alice offers one day")
        offerDays(alice, mapOf(aliceDay to otherSlotOn(agreed, aliceDay))).getOrThrow()
        val offered = EmulatorEnvironment.awaitQueuedPush(bob.uid, "day_swap_offered")
        assertEquals(aliceDay, offered["date"])
        assertNull("a single day carries no count", offered["dayCount"])
        assertEquals(alice.uid, awaitOverride(bob, aliceDay) { it.isPending }.requestedBy)

        refuseSelfAcceptance(aliceDay)

        EmulatorEnvironment.step("swap: Bob accepts it")
        answerDays(bob, listOf(aliceDay), accept = true).getOrThrow()
        assertEquals(aliceDay, EmulatorEnvironment.awaitQueuedPush(alice.uid, "day_swap_accepted")["date"])
        for (phone in listOf(alice, bob)) {
            awaitOverride(phone, aliceDay) { it.isAccepted && it.decidedBy == bob.uid }
        }

        EmulatorEnvironment.step("swap: Bob offers one day, Alice declines it")
        offerDays(bob, mapOf(bobDay to otherSlotOn(agreed, bobDay))).getOrThrow()
        assertEquals(bobDay, EmulatorEnvironment.awaitQueuedPush(alice.uid, "day_swap_offered")["date"])
        awaitOverride(alice, bobDay) { it.isPending && it.requestedBy == bob.uid }
        answerDays(alice, listOf(bobDay), accept = false).getOrThrow()
        assertEquals(bobDay, EmulatorEnvironment.awaitQueuedPush(bob.uid, "day_swap_declined")["date"])
        for (phone in listOf(alice, bob)) {
            awaitOverride(phone, bobDay) { it.status == DayOverrideStatus.DECLINED && it.decidedBy == alice.uid }
            // The earlier agreement is untouched by the later refusal.
            awaitOverride(phone, aliceDay) { it.isAccepted }
        }
    }

    @Test
    fun aGroupSwapIsOfferedAndAnsweredAsOneAgreement() = runBlocking<Unit> {
        val agreed = agreeFirstPattern()
        val aliceRun = (0 until ALICE_RUN_DAYS).map { START.plusDays(ALICE_OFFER_OFFSET + it).toString() }
        val bobRun = (0 until BOB_RUN_DAYS).map { START.plusDays(BOB_OFFER_OFFSET + it).toString() }

        EmulatorEnvironment.step("group: Alice offers a run, Bob declines it")
        offerDays(alice, aliceRun.associateWith { otherSlotOn(agreed, it) }).getOrThrow()
        val offered = EmulatorEnvironment.awaitQueuedPush(bob.uid, "day_swap_group_offered")
        assertEquals(aliceRun.first(), offered["date"])
        assertEquals(ALICE_RUN_DAYS.toString(), offered["dayCount"])
        val groupIds = aliceRun.map { day -> awaitOverride(bob, day) { it.isPending }.groupId }.toSet()
        assertEquals("one run is one group", 1, groupIds.size)
        answerDays(bob, aliceRun, accept = false).getOrThrow()
        val declined = EmulatorEnvironment.awaitQueuedPush(alice.uid, "day_swap_group_declined")
        assertEquals(ALICE_RUN_DAYS.toString(), declined["dayCount"])
        for (phone in listOf(alice, bob)) {
            aliceRun.forEach { day -> awaitOverride(phone, day) { it.status == DayOverrideStatus.DECLINED } }
        }

        EmulatorEnvironment.step("group: Bob offers a run, Alice accepts it")
        offerDays(bob, bobRun.associateWith { otherSlotOn(agreed, it) }).getOrThrow()
        assertEquals(
            BOB_RUN_DAYS.toString(),
            EmulatorEnvironment.awaitQueuedPush(alice.uid, "day_swap_group_offered")["dayCount"]
        )
        bobRun.forEach { day -> awaitOverride(alice, day) { it.isPending && it.requestedBy == bob.uid } }
        answerDays(alice, bobRun, accept = true).getOrThrow()
        val accepted = EmulatorEnvironment.awaitQueuedPush(bob.uid, "day_swap_group_accepted")
        assertEquals(bobRun.first(), accepted["date"])
        assertEquals(BOB_RUN_DAYS.toString(), accepted["dayCount"])
        for (phone in listOf(alice, bob)) {
            bobRun.forEach { day -> awaitOverride(phone, day) { it.isAccepted && it.decidedBy == alice.uid } }
        }
    }

    // ---- the pattern ----------------------------------------------------------------------------

    /**
     * The pair's first schedule: activated on Alice's phone (there is no agreed pattern to protect
     * yet), published to the pair's document, and mirrored into Bob's Room.
     */
    private suspend fun agreeFirstPattern(): CustodyModel {
        EmulatorEnvironment.step("custody: Alice sets the first pattern")
        assertEquals(
            PatternSubmission.ACTIVATED,
            alice.custodyRepository.createWeekOnWeekOff(START, momFirst = true)
        )
        val model = checkNotNull(alice.custodyRepository.getActiveModelSync())
        assertTrue(EmulatorEnvironment.documentExists("custody_models/${CustodyKey.of(alice.uid, bob.uid)}"))
        awaitActivePattern(bob, model.id)
        return model
    }

    /** Bob sees the proposal, accepts it, and both phones — in zones 25 h apart — hold it. */
    private suspend fun acceptOnBobsPhone(agreed: CustodyModel, proposed: CustodyModel) {
        EmulatorEnvironment.step("custody: Bob accepts")
        val acceptedAt = inZone(BOB_ZONE) {
            val pending = checkNotNull(awaitShared(bob) { it.proposal != null }.proposal)
            assertEquals(alice.uid, pending.proposedBy)
            assertEquals(proposed.id, pending.model.id)
            val beforeAnswer = bob.custodyRepository.getActiveModelSync()?.id
            assertEquals("nothing moves before the answer", agreed.id, beforeAnswer)
            bob.custodyRepository.acceptProposal().getOrThrow()
            assertEquals(proposed.id, awaitActivePattern(bob, proposed.id).id)
            checkNotNull(bob.database.custodyModelDao().getActiveModelSync()).lastModifiedAtMillis
        }
        EmulatorEnvironment.awaitQueuedPush(alice.uid, "custody_proposal_accepted")
        inZone(ALICE_ZONE) {
            val shared = awaitShared(alice) { it.lastDecision?.outcome == CustodyDecisionOutcome.ACCEPTED }
            assertNull(shared.proposal)
            assertEquals(bob.uid, shared.lastModifiedBy)
            awaitActivePattern(alice, proposed.id)
            // SEC-4: the same instant on both phones, read back from the wire in another zone.
            val alicesRow = checkNotNull(alice.database.custodyModelDao().getActiveModelSync())
            assertEquals(acceptedAt, alicesRow.lastModifiedAtMillis)
        }
    }

    /** A second proposal, declined: the pattern Bob accepted stays in force on both phones. */
    private suspend fun declineASecondProposal(inForce: CustodyModel) {
        EmulatorEnvironment.step("custody: Alice proposes again, Bob declines")
        assertEquals(
            PatternSubmission.PROPOSED,
            alice.custodyRepository.createWeekOnWeekOff(START, momFirst = false)
        )
        // A proposal has no id of its own — it reads back under the pair's document id, which the
        // accepted pattern now carries too — so it is told apart by being pending at all: the
        // first one was answered and cleared.
        val second = checkNotNull(awaitShared(bob) { it.proposal?.proposedBy == alice.uid }.proposal)
        assertEquals(CustodyModelType.WEEK_ON_WEEK_OFF, second.model.modelType)
        bob.custodyRepository.declineProposal().getOrThrow()
        EmulatorEnvironment.awaitQueuedPush(alice.uid, "custody_proposal_declined")

        for (phone in listOf(alice, bob)) {
            val shared = awaitShared(phone) {
                it.proposal == null && it.lastDecision?.outcome == CustodyDecisionOutcome.DECLINED
            }
            assertEquals(second.proposedAt, shared.lastDecision?.proposalAt)
            assertEquals(inForce.id, shared.model.id)
            assertEquals(inForce.id, phone.custodyRepository.getActiveModelSync()?.id)
        }
    }

    // ---- swaps ----------------------------------------------------------------------------------

    /**
     * Offers each day in [toSlotByDate] from [parent], exactly as the calendar does: one day through
     * `applyDayOverrides` (`CalendarViewModel.offerDaySwap`), a run through
     * `applyDayOverridesForDates` under one group id (`offerDaySwapForDates`).
     */
    private suspend fun offerDays(
        parent: EmulatorParent,
        toSlotByDate: Map<String, String>
    ): Result<Map<String, DayOverride>> {
        val atIso = LocalDateTime.now().toString()
        if (toSlotByDate.size == 1) {
            val (date, toSlot) = toSlotByDate.entries.single()
            return parent.custodyRepository.applyDayOverrides(date) { current ->
                DayOverrideTransition.offer(current, date, toSlot, parent.uid, atIso)
            }
        }
        val groupId = UUID.randomUUID().toString()
        return parent.custodyRepository.applyDayOverridesForDates(toSlotByDate.keys.sorted()) { current, date ->
            DayOverrideTransition.offerAll(
                current = current,
                toParentByDate = mapOf(date to toSlotByDate.getValue(date)),
                byUid = parent.uid,
                atIso = atIso,
                groupId = groupId
            )
        }
    }

    /**
     * Answers [dates] from [parent] the way `ChangeRequestViewModel.decideSwapGroup` does — one
     * day or a run, each day decided against the live document, days already answered left alone.
     */
    private suspend fun answerDays(
        parent: EmulatorParent,
        dates: List<String>,
        accept: Boolean
    ): Result<Map<String, DayOverride>> {
        val now = LocalDateTime.now().toString()
        return parent.custodyRepository.applyDayOverridesForDates(dates) { current, date ->
            if (DayOverrideTransition.awaitsAnswerFrom(current, date, parent.uid)) {
                DayOverrideTransition.decideGroup(current, listOf(date), parent.uid, now, accept)
            } else {
                Result.success(current)
            }
        }
    }

    /**
     * Alice writes an acceptance of her **own** offer straight to the document, past the
     * transition that would refuse it on the phone — and the rule refuses it on the server.
     */
    private suspend fun refuseSelfAcceptance(date: String) {
        val current = (alice.custodyRepository.readShared() as SharedCustodyRead.Found).custody
        val offer = current.dayOverrides.getValue(date)
        val forged: SharedCustody = current.copy(
            lastModifiedBy = alice.uid,
            dayOverrides = current.dayOverrides + (
                date to offer.copy(
                    status = DayOverrideStatus.ACCEPTED,
                    decidedBy = alice.uid,
                    decidedAt = LocalDateTime.now().toString()
                )
                ),
            lastSwapDate = date,
            lastModifiedKind = CustodyWriteKind.SWAP
        )
        val refusal = runCatching {
            FirestoreCustodyDataSource(alice.firestore)
                .setCustody(CustodyKey.of(alice.uid, bob.uid), listOf(alice.uid, bob.uid), forged)
        }.exceptionOrNull()
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (refusal as? FirebaseFirestoreException)?.code
        )
    }

    // ---- waits ----------------------------------------------------------------------------------

    /** The pair's document as [parent]'s shared listener delivers it, once [condition] holds. */
    private suspend fun awaitShared(parent: EmulatorParent, condition: (SharedCustody) -> Boolean): SharedCustody =
        withTimeout(EmulatorParent.WAIT_MS) {
            checkNotNull(parent.custodyRepository.observeShared().first { it != null && condition(it) })
        }

    /** [parent]'s calendar pattern — Room, fed by the mirror — once it is [modelId]. */
    private suspend fun awaitActivePattern(parent: EmulatorParent, modelId: String): CustodyModel =
        withTimeout(EmulatorParent.WAIT_MS) {
            checkNotNull(parent.custodyRepository.getActiveModel().first { it?.id == modelId })
        }

    /** The swap on [date] as [parent]'s Room holds it, once [condition] holds. */
    private suspend fun awaitOverride(
        parent: EmulatorParent,
        date: String,
        condition: (DayOverride) -> Boolean
    ): DayOverride = withTimeout(EmulatorParent.WAIT_MS) {
        parent.custodyRepository.observeDayOverrides()
            .first { overrides -> overrides[date]?.let(condition) == true }
            .getValue(date)
    }

    /** The slot that would take [date] from whoever the agreed pattern gives it to. */
    private fun otherSlotOn(model: CustodyModel, date: String): String =
        if (model.getCustodyFor(LocalDate.parse(date)) == "mom") "dad" else "mom"

    private companion object {
        /** A Monday; the pattern's anchor. */
        val START: LocalDate = LocalDate.of(2026, 10, 5)

        /** Alice's offers start on the third day of her first week. */
        const val ALICE_OFFER_OFFSET = 2L

        /** Bob's start in his first week of the fortnight. */
        const val BOB_OFFER_OFFSET = 9L

        const val ALICE_RUN_DAYS = 3
        const val BOB_RUN_DAYS = 2

        /** UTC+14, the first place a new day starts. */
        const val ALICE_ZONE = "Pacific/Kiritimati"

        /** UTC−11, among the last. */
        const val BOB_ZONE = "Pacific/Pago_Pago"
    }
}
