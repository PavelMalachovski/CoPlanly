package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.remote.firebase.FirestoreParentingPlanDataSource
import com.coparently.app.data.repository.ParentingPlanPair
import com.coparently.app.data.repository.RatioSubmission
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioOutcome
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.parentingplan.ParentingPlanComparison
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.parentingplan.PlanQuestionStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two agreements a pair makes in writing — the expense split (`family_settings`) and the
 * parenting plan (`parenting_plans`) — made on one phone and read on the other.
 *
 * **The split.** Every step goes through `FamilySettingsRepository`, the class `ExpenseViewModel`
 * and the onboarding split step call: a pair's first ratio applies outright and announces itself
 * as `split_ratio_agreed` (UX-18, CLAUDE.md item 22), whether it is set on a paired screen or
 * carried over by `publishCachedRatioIfMissing` from a ratio chosen before pairing; every later
 * change is a proposal (`split_ratio_proposed`) the co-parent accepts (`split_ratio_accepted`) or
 * declines (`split_ratio_declined`), and the proposer may withdraw it (UX-17), which pushes
 * nothing. Each push is read back from `notification_queue` addressed to the right parent — the
 * production `FcmService` write, checked by the real `firestore.rules`.
 *
 * **The plan.** Each parent writes their own half through `ParentingPlanRepository.save`, built
 * the way `ParentingPlanViewModel.edit` builds it, and reads the other's through the same
 * repository's mirror. What it proves is item 21: an agreement records the co-parent's *wording*,
 * so when that wording changes the agreement lapses on the other phone without anybody writing
 * to it; and a write that touches the other parent's key is refused by the rules, through the
 * production data source and through a crafted merge.
 *
 * **What it cannot do.** Deliver a push (FCM has no emulator), or show the proposal banner or the
 * plan screen as drawn — the Expenses and plan screens are not driven here.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentAgreementsTest : TwoParentTest() {

    @Test
    fun firstRatioOnAFreshPairAppliesAndIsAnnouncedAsAgreed() = runBlocking<Unit> {
        val submitted = alice.familySettingsRepository.submitRatio(SEVENTY_THIRTY).getOrThrow()
        assertEquals(RatioSubmission.APPLIED, submitted)

        val onBobsPhone = bob.settingsWhere { it.ratio == SEVENTY_THIRTY }
        assertNull("a first agreement is not a proposal", onBobsPhone.proposal)
        assertEquals(alice.uid, onBobsPhone.lastModifiedBy)

        val agreed = pushesTo(bob).filter { it.type == AGREED }
        assertEquals("one split_ratio_agreed push for Bob", 1, agreed.size)
        assertEquals(FamilyKey.of(alice.uid, bob.uid), agreed.single().familyId)
        assertTrue(
            "nothing about the split is addressed to Alice",
            pushesTo(alice).none { it.type in SPLIT_TYPES }
        )
    }

    @Test
    fun ratioChosenBeforePairingIsPublishedToTheNewPair() = runBlocking<Unit> {
        val carol = newParent("Carol")
        val dave = newParent("Dave")
        assertEquals(
            RatioSubmission.APPLIED,
            carol.familySettingsRepository.submitRatio(SEVENTY_THIRTY).getOrThrow()
        )

        pair(inviter = carol, accepter = dave)
        // The call `SyncService` makes on every pass; here it is the first sync after pairing.
        carol.familySettingsRepository.publishCachedRatioIfMissing()

        // Carol set *her own* share; the document stores slot 1's, so it depends on her slot.
        val carolsSlot = checkNotNull(carol.userRepository.getRemoteUserProfile(carol.uid)).role
        val expected = if (carolsSlot == "mom") SEVENTY_THIRTY else SplitRatio(SEVENTY_THIRTY.dadShareBasisPoints)
        val onDavesPhone = dave.settingsWhere { true }
        assertEquals(expected, onDavesPhone.ratio)
        assertNull(onDavesPhone.proposal)
        assertEquals(listOf(AGREED), pushTypesTo(dave).filter { it in SPLIT_TYPES })
    }

    @Test
    fun proposalIsSeenPendingAcceptedAndBothPhonesHoldTheNewRatio() = runBlocking<Unit> {
        alice.familySettingsRepository.submitRatio(SplitRatio.EVEN).getOrThrow()
        assertEquals(
            RatioSubmission.PROPOSED,
            alice.familySettingsRepository.submitRatio(SEVENTY_THIRTY).getOrThrow()
        )
        assertEquals(1, pushesTo(bob).count { it.type == PROPOSED })

        val pending = bob.settingsWhere { it.proposal != null }
        assertEquals("the agreed ratio does not move on a proposal", SplitRatio.EVEN, pending.ratio)
        assertEquals(SEVENTY_THIRTY, pending.proposal?.ratio)
        assertEquals(alice.uid, pending.proposal?.proposedBy)
        assertTrue(
            "a parent may not answer their own proposal",
            alice.familySettingsRepository.acceptProposal().isFailure
        )

        bob.familySettingsRepository.acceptProposal().getOrThrow()
        assertEquals(1, pushesTo(alice).count { it.type == ACCEPTED })

        for (phone in listOf(alice, bob)) {
            val settled = phone.settingsWhere { it.ratio == SEVENTY_THIRTY && it.proposal == null }
            assertEquals(SplitRatioOutcome.ACCEPTED, settled.lastDecision?.outcome)
            assertEquals(bob.uid, settled.lastDecision?.by)
        }
        // What the next expense on the accepter's phone is priced at, with no round trip.
        assertEquals(SEVENTY_THIRTY, bob.familySettingsRepository.agreedRatioOrDefault())
    }

    @Test
    fun declinedProposalLeavesTheAgreedRatioOnBothPhones() = runBlocking<Unit> {
        alice.familySettingsRepository.submitRatio(SIXTY_FORTY).getOrThrow()
        alice.familySettingsRepository.submitRatio(SplitRatio.EVEN).getOrThrow()
        bob.settingsWhere { it.proposal != null }

        bob.familySettingsRepository.declineProposal().getOrThrow()
        assertEquals(1, pushesTo(alice).count { it.type == DECLINED })
        assertTrue(pushesTo(alice).none { it.type == ACCEPTED })

        for (phone in listOf(alice, bob)) {
            val settled = phone.settingsWhere { it.proposal == null && it.lastDecision != null }
            assertEquals(SIXTY_FORTY, settled.ratio)
            assertEquals(SplitRatioOutcome.DECLINED, settled.lastDecision?.outcome)
        }
    }

    @Test
    fun proposerWithdrawsAndTheCoParentCannot() = runBlocking<Unit> {
        alice.familySettingsRepository.submitRatio(SplitRatio.EVEN).getOrThrow()
        alice.familySettingsRepository.submitRatio(SIXTY_FORTY).getOrThrow()
        bob.settingsWhere { it.proposal != null }

        assertTrue(
            "only the proposer may withdraw",
            bob.familySettingsRepository.withdrawProposal().isFailure
        )
        alice.familySettingsRepository.withdrawProposal().getOrThrow()

        val withdrawn = bob.settingsWhere { it.proposal == null }
        assertEquals(SplitRatio.EVEN, withdrawn.ratio)
        assertNull("a withdrawal decides nothing", withdrawn.lastDecision)
        // A withdrawal pushes nothing: Bob holds exactly the agreement and the proposal.
        assertEquals(
            listOf(AGREED, PROPOSED),
            pushTypesTo(bob).filter { it in SPLIT_TYPES }.sorted()
        )
        assertTrue(pushesTo(alice).none { it.type in SPLIT_TYPES })
    }

    @Test
    fun planAgreementRecordsTheWordingAndLapsesWhenItChanges() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)

        alice.editPlan(familyId) { entry, now -> entry.withAnswer(QUESTION, ALICE_ANSWER, now) }
        bob.editPlan(familyId) { entry, now -> entry.withAnswer(QUESTION, BOB_ANSWER, now) }
        bob.planWhere(familyId) { it.theirs?.answerTo(QUESTION) == ALICE_ANSWER }
        alice.planWhere(familyId) { it.theirs?.answerTo(QUESTION) == BOB_ANSWER }

        // Each ticks the other's wording as it reads on their own screen.
        alice.editPlan(familyId) { entry, now -> entry.withAgreement(QUESTION, BOB_ANSWER, now) }
        bob.editPlan(familyId) { entry, now -> entry.withAgreement(QUESTION, ALICE_ANSWER, now) }
        for (phone in listOf(alice, bob)) {
            phone.planWhere(familyId) { status(it) == PlanQuestionStatus.AGREED }
        }

        // Bob rewords his answer. Alice writes nothing, and her agreement lapses on her phone.
        bob.editPlan(familyId) { entry, now -> entry.withAnswer(QUESTION, BOB_REWORDED, now) }
        val onAlicesPhone = alice.planWhere(familyId) { it.theirs?.answerTo(QUESTION) == BOB_REWORDED }
        assertEquals(PlanQuestionStatus.OPEN, status(onAlicesPhone))
        assertEquals(
            "Alice's mark still names the wording she agreed to",
            BOB_ANSWER,
            onAlicesPhone.yours.agreedTo[QUESTION]
        )
        bob.planWhere(familyId) { status(it) == PlanQuestionStatus.OPEN }
    }

    @Test
    fun aWriteToTheCoParentsHalfIsRefused() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)
        alice.editPlan(familyId) { entry, now -> entry.withAnswer(QUESTION, ALICE_ANSWER, now) }
        bob.editPlan(familyId) { entry, now -> entry.withAnswer(QUESTION, BOB_ANSWER, now) }
        alice.planWhere(familyId) { it.theirs?.answerTo(QUESTION) == BOB_ANSWER }

        // The production writer, pointed at Bob's key from Alice's phone.
        val forged = ParentingPlanEntry(
            answers = mapOf(QUESTION to FORGED_ANSWER),
            updatedAtMillis = System.currentTimeMillis()
        )
        val alicesWriter = FirestoreParentingPlanDataSource(alice.firestore)
        assertDenied(runCatching { alicesWriter.uploadHalf(familyId, bob.uid, forged) })

        // A crafted merge that writes Alice's own half and slips Bob's in beside it.
        val crafted = mapOf(
            "answers" to mapOf(
                alice.uid to mapOf(QUESTION to ALICE_ANSWER),
                bob.uid to mapOf(QUESTION to FORGED_ANSWER)
            )
        )
        assertDenied(
            runCatching {
                alice.firestore.collection("parenting_plans").document(familyId)
                    .set(crafted, SetOptions.merge()).await()
            }
        )

        val server = FirestoreParentingPlanDataSource(bob.firestore).fetchFromServer(familyId)
        assertEquals(BOB_ANSWER, server[bob.uid]?.answerTo(QUESTION))
        assertEquals(ALICE_ANSWER, server[alice.uid]?.answerTo(QUESTION))
    }

    /** One queued push: its `data.type` and `data.familyId`. */
    private data class QueuedPush(val type: String?, val familyId: String?)

    /** Every push queued for [receiver], from anyone — read as admin, as `FcmService` wrote it. */
    private fun pushesTo(receiver: EmulatorParent): List<QueuedPush> =
        receiver.queuedFor(receiver.uid).map { document ->
            val data = document["data"] as? Map<*, *>
            QueuedPush(type = data?.get("type") as? String, familyId = data?.get("familyId") as? String)
        }

    /** The types of every push queued for [receiver], in no particular order. */
    private fun pushTypesTo(receiver: EmulatorParent): List<String> = pushesTo(receiver).mapNotNull { it.type }

    /** Waits until this phone's `observeSettings` shows a document matching [predicate]. */
    private suspend fun EmulatorParent.settingsWhere(predicate: (FamilySettings) -> Boolean): FamilySettings =
        withTimeout(EmulatorParent.WAIT_MS) {
            familySettingsRepository.observeSettings().filterNotNull().first { predicate(it) }
        }

    /**
     * Changes this parent's half exactly as `ParentingPlanViewModel.edit` does: the current half
     * from the repository, the change applied at the wall clock, saved through `save`.
     */
    private suspend fun EmulatorParent.editPlan(
        familyId: String,
        change: (ParentingPlanEntry, Long) -> ParentingPlanEntry
    ) {
        val current = parentingPlanRepository.observe(familyId, uid).first().yours
        parentingPlanRepository.save(familyId, uid, change(current, System.currentTimeMillis()))
    }

    /** Waits until this phone's plan — Room, filled by the mirror — satisfies [predicate]. */
    private suspend fun EmulatorParent.planWhere(
        familyId: String,
        predicate: (ParentingPlanPair) -> Boolean
    ): ParentingPlanPair = withTimeout(EmulatorParent.WAIT_MS) {
        parentingPlanRepository.observe(familyId, uid).first { predicate(it) }
    }

    private fun status(plan: ParentingPlanPair): PlanQuestionStatus =
        ParentingPlanComparison.statusOf(QUESTION, plan.yours, plan.theirs)

    private fun assertDenied(result: Result<*>) {
        val error = result.exceptionOrNull()
        assertTrue("expected a refusal, got $result", error is FirebaseFirestoreException)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (error as FirebaseFirestoreException).code
        )
    }

    private companion object {
        val SEVENTY_THIRTY = SplitRatio(7_000)
        val SIXTY_FORTY = SplitRatio(6_000)

        const val AGREED = "split_ratio_agreed"
        const val PROPOSED = "split_ratio_proposed"
        const val ACCEPTED = "split_ratio_accepted"
        const val DECLINED = "split_ratio_declined"
        val SPLIT_TYPES = setOf(AGREED, PROPOSED, ACCEPTED, DECLINED)

        /** A catalogue question (`ParentingPlanCatalogue`), the one a schedule proposal cites. */
        const val QUESTION = "care_weekday"
        const val ALICE_ANSWER = "Alternate weeks, handover on Friday after school."
        const val BOB_ANSWER = "Alternate weeks, handover on Monday morning at school."
        const val BOB_REWORDED = "Alternate weeks, handover on Sunday evening."
        const val FORGED_ANSWER = "Every weekend with Alice."
    }
}
