package com.coparently.app.data.repository

import app.cash.turbine.test
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirestoreFamilySettingsDataSource
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.custody.CustodyKey
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An expense split proposal as a request (October 2026, owner decision): a proposal given a
 * deadline starts its clock once it is written, every step is a card in the family's thread, the
 * proposal's push says when to answer by, and the clock's end withdraws a proposal nobody answered
 * — only that proposal, in a transaction, and with no card of its own (the end posts "No answer").
 */
class FamilySettingsSplitRequestTest {

    private val dataSource = mockk<FirestoreFamilySettingsDataSource>()
    private val preferences = mockk<EncryptedPreferences>(relaxed = true)
    private val fcmService = mockk<FcmService>(relaxed = true)
    private val announcer = mockk<ActivityAnnouncer>(relaxed = true)
    private val requestDeadlines = mockk<RequestDeadlineRepository>()

    private val userRepository = mockk<UserRepository> {
        coEvery { getCurrentUserId() } returns ME
        coEvery { getUserById(ME) } returns User(
            id = ME,
            email = "zuzana@example.test",
            name = "Zuzana",
            role = "mom",
            colorCode = "#FF4081",
            partnerId = PARTNER,
            partnerIds = listOf(PARTNER)
        )
    }

    private val repository = FamilySettingsRepository(
        dataSource,
        userRepository,
        preferences,
        fcmService,
        announcer,
        requestDeadlines
    )

    private val written = slot<FamilySettings>()

    @Before
    fun setUp() {
        every { preferences.getFamilySplitRatioBasisPoints(any()) } returns null
        coEvery { dataSource.setSettings(FAMILY, capture(written)) } returns Unit
    }

    // ---- A proposal -------------------------------------------------------------------------

    @Test
    fun `a proposal given a deadline starts its clock after the write, then posts its card and its push`() =
        runTest {
            coEvery { dataSource.getSettings(FAMILY) } returns agreedEven()
            val started = slot<RequestStart>()
            coEvery { requestDeadlines.start(capture(started)) } answers { clockFor(started.captured) }

            val outcome = repository.submitRatio(SEVENTY_THIRTY, FAMILY, AnswerByPick.DEFAULT)

            assertEquals(RatioSubmission.PROPOSED, outcome.getOrThrow())
            val proposal = checkNotNull(written.captured.proposal)
            assertEquals(
                RequestStart(
                    kind = RequestKind.SPLIT,
                    ref = proposal.proposedAtMillis.toString(),
                    familyId = FAMILY,
                    responderUid = PARTNER,
                    answerByMillis = started.captured.answerByMillis,
                    capMillis = null
                ),
                started.captured
            )
            assertTrue(started.captured.answerByMillis > proposal.proposedAtMillis, "the deadline lies ahead")
            assertEquals(
                RequestDeadlineId.forSplit(proposal, FAMILY),
                clockFor(started.captured).id,
                "the clock's id is the one every reader derives from the proposal"
            )
            coVerifyOrder {
                dataSource.setSettings(FAMILY, any())
                requestDeadlines.start(any())
                announcer.announce(any(), any(), any(), PARTNER)
                fcmService.queueNotificationForUser(PARTNER, any())
            }
            assertEquals(
                ActivityAnnouncement(
                    kind = ActivityKind.SPLIT_PROPOSED,
                    entityType = ActivityEntityType.SPLIT_PROPOSAL,
                    entityId = FAMILY,
                    title = "",
                    amount = "7000"
                ),
                postedCard()
            )
            assertEquals(
                mapOf(
                    PushPayload.TYPE to PushPayload.SPLIT_RATIO_PROPOSED,
                    PushPayload.ANSWER_BY to started.captured.answerByMillis.toString()
                ),
                queuedPush()
            )
        }

    @Test
    fun `a proposal without a deadline starts no clock, and its push names none`() = runTest {
        coEvery { dataSource.getSettings(FAMILY) } returns agreedEven()

        repository.submitRatio(SEVENTY_THIRTY, FAMILY).getOrThrow()

        coVerify(exactly = 0) { requestDeadlines.start(any()) }
        assertEquals(ActivityKind.SPLIT_PROPOSED, postedCard().kind)
        assertEquals(mapOf(PushPayload.TYPE to PushPayload.SPLIT_RATIO_PROPOSED), queuedPush())
    }

    @Test
    fun `a clock the rules refused leaves the push without a deadline, and the card is still posted`() = runTest {
        coEvery { dataSource.getSettings(FAMILY) } returns agreedEven()
        coEvery { requestDeadlines.start(any()) } returns null

        repository.submitRatio(SEVENTY_THIRTY, FAMILY, AnswerByPick.DEFAULT).getOrThrow()

        assertEquals(ActivityKind.SPLIT_PROPOSED, postedCard().kind)
        assertEquals(mapOf(PushPayload.TYPE to PushPayload.SPLIT_RATIO_PROPOSED), queuedPush())
    }

    @Test
    fun `a pair's first split applies outright and starts no clock, whatever deadline was asked`() = runTest {
        coEvery { dataSource.getSettings(FAMILY) } returns null

        val outcome = repository.submitRatio(SEVENTY_THIRTY, FAMILY, AnswerByPick.DEFAULT)

        assertEquals(RatioSubmission.APPLIED, outcome.getOrThrow())
        coVerify(exactly = 0) { requestDeadlines.start(any()) }
        coVerify(exactly = 0) { announcer.announce(any(), any(), any(), any()) }
        assertEquals(mapOf(PushPayload.TYPE to PushPayload.SPLIT_RATIO_AGREED), queuedPush())
    }

    @Test
    fun `a refused proposal starts nothing and tells nobody`() = runTest {
        // The co-parent's own proposal waits: answering it is the way past it.
        coEvery { dataSource.getSettings(FAMILY) } returns agreedEven().copy(proposal = proposalBy(PARTNER))

        assertTrue(repository.submitRatio(SEVENTY_THIRTY, FAMILY, AnswerByPick.DEFAULT).isFailure)

        coVerify(exactly = 0) { dataSource.setSettings(any(), any()) }
        coVerify(exactly = 0) { requestDeadlines.start(any()) }
        coVerify(exactly = 0) { announcer.announce(any(), any(), any(), any()) }
        coVerify(exactly = 0) { fcmService.queueNotificationForUser(any(), any()) }
    }

    // ---- The answer and a withdrawal ----------------------------------------------------------

    @Test
    fun `an answer is posted to the proposer as a card, beside the push it always had`() = runTest {
        coEvery { dataSource.getSettings(FAMILY) } returns agreedEven().copy(proposal = proposalBy(PARTNER))

        repository.acceptProposal(FAMILY).getOrThrow()
        assertEquals(ActivityKind.SPLIT_ACCEPTED, postedCard().kind)
        assertEquals(ActivityEntityType.SPLIT_PROPOSAL, postedCard().entityType)
        assertEquals(mapOf(PushPayload.TYPE to PushPayload.SPLIT_RATIO_ACCEPTED), queuedPush())

        repository.declineProposal(FAMILY).getOrThrow()
        assertEquals(ActivityKind.SPLIT_DECLINED, postedCard().kind)
        assertEquals(mapOf(PushPayload.TYPE to PushPayload.SPLIT_RATIO_DECLINED), queuedPush())
    }

    @Test
    fun `a withdrawal the proposer makes is a card in the thread and no push`() = runTest {
        coEvery { dataSource.getSettings(FAMILY) } returns agreedEven().copy(proposal = proposalBy(ME))

        repository.withdrawProposal(FAMILY).getOrThrow()

        assertNull(written.captured.proposal)
        assertEquals(ActivityKind.SPLIT_WITHDRAWN, postedCard().kind)
        assertEquals(FAMILY, postedCard().entityId)
        coVerify(exactly = 0) { fcmService.queueNotificationForUser(any(), any()) }
    }

    // ---- The end of a proposal nobody answered -------------------------------------------------

    @Test
    fun `the end withdraws the same proposal in a transaction, keeps the agreed split and posts nothing`() =
        runTest {
            transactingOver(agreedEven().copy(proposal = proposalBy(ME, at = PROPOSED_AT)))

            assertTrue(repository.withdrawUnanswered(FAMILY, PROPOSED_AT))

            val after = checkNotNull(transacted)
            assertNull(after.proposal, "the proposal is gone")
            assertEquals(SplitRatio.EVEN, after.ratio, "the agreed split stays")
            // The end posts its own "No answer" card; a withdrawal card here would tell twice.
            coVerify(exactly = 0) { announcer.announce(any(), any(), any(), any()) }
            coVerify(exactly = 0) { fcmService.queueNotificationForUser(any(), any()) }
        }

    @Test
    fun `the end leaves a newer, answered or co-parent's proposal alone`() = runTest {
        transactingOver(agreedEven().copy(proposal = proposalBy(ME, at = PROPOSED_AT + 1)))
        assertFalse(repository.withdrawUnanswered(FAMILY, PROPOSED_AT), "replaced by a newer one")

        transactingOver(agreedEven())
        assertFalse(repository.withdrawUnanswered(FAMILY, PROPOSED_AT), "answered or withdrawn meanwhile")

        transactingOver(agreedEven().copy(proposal = proposalBy(PARTNER, at = PROPOSED_AT)))
        assertFalse(repository.withdrawUnanswered(FAMILY, PROPOSED_AT), "the co-parent's")

        assertNull(transacted, "nothing was written")
    }

    @Test
    fun `the end acts in its own family only, and a failed transaction is tried again`() = runTest {
        // A family this parent is no longer in is not theirs to write.
        assertFalse(repository.withdrawUnanswered(CustodyKey.of(ME, "uid-ex"), PROPOSED_AT))
        coVerify(exactly = 0) { dataSource.transact(any(), any()) }

        coEvery { dataSource.transact(FAMILY, any()) } throws IOException("offline")
        assertFalse(repository.withdrawUnanswered(FAMILY, PROPOSED_AT))
    }

    // ---- The Requests source ------------------------------------------------------------------

    @Test
    fun `a family's settings by id are unknown first, then the document, and unknown after a failure`() =
        runTest {
            val settings = agreedEven().copy(proposal = proposalBy(PARTNER))
            every { dataSource.observeSettings(FAMILY) } returns flowOf(settings)

            repository.observeSettingsOf(FAMILY).test {
                assertNull(awaitItem())
                assertEquals(settings, awaitItem())
                awaitComplete()
            }

            every { dataSource.observeSettings(FAMILY) } returns flow { throw IOException("denied") }
            repository.observeSettingsOf(FAMILY).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

    // ---- Helpers ------------------------------------------------------------------------------

    /** What the last transaction wrote, or null while none wrote anything. */
    private var transacted: FamilySettings? = null

    /** Runs every transaction's transform over [current], as Firestore would over the stored document. */
    private fun transactingOver(current: FamilySettings) {
        coEvery { dataSource.transact(FAMILY, any()) } answers {
            secondArg<(FamilySettings?) -> FamilySettings?>().invoke(current)?.also { transacted = it }
        }
    }

    /** The last card posted to the co-parent. */
    private fun postedCard(): ActivityAnnouncement {
        val cards = mutableListOf<ActivityAnnouncement>()
        coVerify { announcer.announce(capture(cards), any(), any(), PARTNER) }
        return cards.last()
    }

    /** The last push queued for the co-parent. */
    private fun queuedPush(): Map<String, String> {
        val pushes = mutableListOf<Map<String, String>>()
        coVerify { fcmService.queueNotificationForUser(PARTNER, capture(pushes)) }
        return pushes.last()
    }

    /** The clock [start] would be written as, by this parent. */
    private fun clockFor(start: RequestStart) = RequestDeadline(
        id = RequestDeadlineId.of(start.kind, ME, start.ref, start.familyId),
        kind = start.kind,
        ref = start.ref,
        familyId = start.familyId,
        proposerUid = ME,
        responderUid = start.responderUid,
        answerByMillis = start.answerByMillis
    )

    /** The pair's settings with half each agreed and nothing pending. */
    private fun agreedEven() = FamilySettings(
        ratio = SplitRatio.EVEN,
        participants = listOf(ME, PARTNER).sorted(),
        lastModifiedBy = PARTNER,
        lastModifiedAtMillis = 1L
    )

    /** A 70/30 proposal standing in [by]'s name. */
    private fun proposalBy(by: String, at: Long = PROPOSED_AT) =
        SplitRatioProposal(ratio = SEVENTY_THIRTY, proposedBy = by, proposedAtMillis = at)

    private companion object {
        const val ME = "uid-zuzana"
        const val PARTNER = "uid-pavel"
        val FAMILY = CustodyKey.of(ME, PARTNER)
        val SEVENTY_THIRTY = SplitRatio(7_000)
        const val PROPOSED_AT = 1_792_488_600_000L
    }
}
