package com.coparently.app.data.repository

import app.cash.turbine.test
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirestoreFamilySettingsDataSource
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.custody.CustodyKey
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a split agreed before there was anybody to agree with reaches the pair.
 *
 * The gap this pins was silent and complete: the unpaired branch of `submitRatio` can only write
 * `EncryptedPreferences`, its comment claimed "the first write after pairing publishes it", and
 * no such write existed anywhere. A parent who set 70/30 in the wizard paired, saw 70/30 in
 * Settings, and had both phones go on dividing every expense evenly — while the onboarding step
 * told them their co-parent would be asked to confirm it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FamilySettingsRepositoryTest {

    private val dataSource = mockk<FirestoreFamilySettingsDataSource>()
    private val preferences = mockk<EncryptedPreferences>()

    /** The chat cards; `FamilySettingsSplitRequestTest` reads what they say. */
    private val announcer = mockk<ActivityAnnouncer>(relaxed = true)

    /** The request clocks; no test here gives a deadline, so nothing starts one. */
    private val requestDeadlines = mockk<RequestDeadlineRepository>(relaxed = true)

    private fun repository(
        cachedBasisPoints: Int?,
        partnerId: String?,
        // The signed-in slot by default, so the common case publishes the number unchanged.
        capturedSlot: String? = "mom"
    ): FamilySettingsRepository {
        every { preferences.getSplitRatioBasisPoints() } returns cachedBasisPoints
        every { preferences.putSplitRatioBasisPoints(any()) } returns Unit
        every { preferences.putSplitRatioSlot(any()) } returns Unit
        every { preferences.getSplitRatioSlot() } returns capturedSlot
        stubFamilyCache()
        val userRepository = mockk<UserRepository> {
            coEvery { getCurrentUserId() } returns ME
            coEvery { getUserById(ME) } returns User(
                id = ME,
                email = "olya@example.test",
                name = "Olya",
                role = "mom",
                colorCode = "#FF4081",
                partnerId = partnerId
            )
        }
        return FamilySettingsRepository(
            dataSource,
            userRepository,
            preferences,
            mockk<FcmService>(relaxed = true),
            announcer,
            requestDeadlines
        )
    }

    @Test
    fun `a ratio cached before pairing is published once there is a pair`() = runTest {
        coEvery { dataSource.getSettings(any()) } returns null
        coEvery { dataSource.setSettings(any(), any()) } returns Unit

        repository(cachedBasisPoints = SEVENTY_IN_BASIS_POINTS, partnerId = PARTNER)
            .publishCachedRatioIfMissing()

        val documentId = slot<String>()
        val written = slot<FamilySettings>()
        coVerify { dataSource.setSettings(capture(documentId), capture(written)) }
        assertEquals(SplitRatio(SEVENTY_IN_BASIS_POINTS), written.captured.ratio)
        // The three things `firestore.rules` gates the create on, and none of them is implied by
        // the ratio: the id must be the canonical pair id or the document can be squatted, the
        // participants must be sorted or `update`'s order-sensitive equality denies every later
        // write, and `lastModifiedBy` must be the caller. The two uids are deliberately named so
        // that sorting reorders them — with `me` < `partner` the sorted assertion would pass for
        // an implementation that never sorted at all.
        assertEquals(CustodyKey.of(ME, PARTNER), documentId.captured)
        assertEquals(listOf(ME, PARTNER).sorted(), written.captured.participants)
        assertEquals(ME, written.captured.lastModifiedBy)
    }

    @Test
    fun `a ratio captured in the other slot is flipped before it is published`() = runTest {
        // What the parent set was *their* share; the stored form is slot 1's. An unpaired account
        // is slot 1 by default and `assignSlots` can move it to slot 2, so publishing the number
        // as-is would hand the co-parent the share this parent meant to take.
        coEvery { dataSource.getSettings(any()) } returns null
        coEvery { dataSource.setSettings(any(), any()) } returns Unit

        repository(
            cachedBasisPoints = SEVENTY_IN_BASIS_POINTS,
            partnerId = PARTNER,
            capturedSlot = "dad"
        ).publishCachedRatioIfMissing()

        val written = slot<FamilySettings>()
        coVerify { dataSource.setSettings(any(), capture(written)) }
        assertEquals(SplitRatio(THIRTY_IN_BASIS_POINTS), written.captured.ratio)
    }

    @Test
    fun `a figure that already belongs to a pair is never republished to the next one`() =
        runTest {
            // The cache is one device-wide integer with no pair key, and nothing clears it on
            // unpair. Every *paired* write clears the capture slot, so its absence marks a figure
            // that is already some pair's agreement of record — republishing it would hand the
            // next co-parent a split neither of them made, and would revive on the client the
            // very document `unpairCoParent` deletes on the server to prevent exactly that.
            coEvery { dataSource.getSettings(any()) } returns null

            repository(
                cachedBasisPoints = SEVENTY_IN_BASIS_POINTS,
                partnerId = PARTNER,
                capturedSlot = null
            ).publishCachedRatioIfMissing()

            coVerify(exactly = 0) { dataSource.setSettings(any(), any()) }
        }

    @Test
    fun `an agreement the pair already has is never overwritten`() = runTest {
        // Safe to run on every sync tick is the whole design: the co-parent's own cached ratio
        // may have published first, or the two of them may have renegotiated since.
        coEvery { dataSource.getSettings(any()) } returns FamilySettings(
            ratio = SplitRatio.EVEN,
            participants = listOf(ME, PARTNER).sorted(),
            lastModifiedBy = PARTNER,
            lastModifiedAtMillis = 1L
        )

        repository(cachedBasisPoints = SEVENTY_IN_BASIS_POINTS, partnerId = PARTNER)
            .publishCachedRatioIfMissing()

        coVerify(exactly = 0) { dataSource.setSettings(any(), any()) }
    }

    @Test
    fun `an unpaired account publishes nothing, because there is no pair to publish to`() =
        runTest {
            repository(cachedBasisPoints = SEVENTY_IN_BASIS_POINTS, partnerId = null)
                .publishCachedRatioIfMissing()

            coVerify(exactly = 0) { dataSource.setSettings(any(), any()) }
        }

    @Test
    fun `a parent who never chose a ratio has none invented for them`() = runTest {
        // Null cache is not "even split": publishing one would put an agreement in the pair's
        // document that neither of them ever made.
        coEvery { dataSource.getSettings(any()) } returns null

        repository(cachedBasisPoints = null, partnerId = PARTNER).publishCachedRatioIfMissing()

        coVerify(exactly = 0) { dataSource.setSettings(any(), any()) }
    }

    // ---- UX-18: the first agreement is announced -----------------------------------------

    @Test
    fun `a ratio agreed before pairing is announced to the co-parent`() = runTest {
        // It becomes the pair's agreement of record the moment it is written, priced onto every
        // expense from then on. Until this, the co-parent learned of it only by opening Settings.
        val fcmService = mockk<FcmService>(relaxed = true)
        coEvery { dataSource.getSettings(any()) } returns null
        coEvery { dataSource.setSettings(any(), any()) } returns Unit

        repositoryWith(fcmService, cachedBasisPoints = SEVENTY_IN_BASIS_POINTS)
            .publishCachedRatioIfMissing()

        val queued = slot<Map<String, String>>()
        coVerify { fcmService.queueNotificationForUser(PARTNER, capture(queued)) }
        assertEquals("split_ratio_agreed", queued.captured["type"])
    }

    @Test
    fun `the pair's first agreement is announced whichever screen writes it`() = runTest {
        // `submitRatio` used to announce only a *proposal*; the first write of a pair that had
        // no document yet applied outright and told nobody. With the co-parent link made first,
        // a second parent's wizard reaches that branch routinely, so the co-parent learned of
        // the split that prices every expense only by opening Settings.
        val fcmService = mockk<FcmService>(relaxed = true)
        coEvery { dataSource.getSettings(any()) } returns null
        coEvery { dataSource.setSettings(any(), any()) } returns Unit

        val outcome = repositoryWith(fcmService, cachedBasisPoints = null)
            .submitRatio(SplitRatio.ofMomPercent(60))

        assertEquals(RatioSubmission.APPLIED, outcome.getOrThrow())
        val queued = slot<Map<String, String>>()
        coVerify { fcmService.queueNotificationForUser(PARTNER, capture(queued)) }
        assertEquals("split_ratio_agreed", queued.captured["type"])
    }

    @Test
    fun `the announcement carries a type and no figure`() = runTest {
        // SEC-3's shape, and `PushPayload` states the reason for this family of types: a push
        // saying "the split is now 70/30" puts a number a reader may act on onto a lock screen,
        // written by the other side and unverifiable until the app is opened.
        val fcmService = mockk<FcmService>(relaxed = true)
        coEvery { dataSource.getSettings(any()) } returns null
        coEvery { dataSource.setSettings(any(), any()) } returns Unit

        repositoryWith(fcmService, cachedBasisPoints = SEVENTY_IN_BASIS_POINTS)
            .publishCachedRatioIfMissing()

        val queued = slot<Map<String, String>>()
        coVerify { fcmService.queueNotificationForUser(PARTNER, capture(queued)) }
        assertEquals(setOf("type"), queued.captured.keys)
    }

    @Test
    fun `nothing is announced when nothing is published`() = runTest {
        // The pair already has an agreement, so this pass writes nothing — and a push about a
        // write that did not happen is worse than silence.
        val fcmService = mockk<FcmService>(relaxed = true)
        coEvery { dataSource.getSettings(any()) } returns FamilySettings(
            ratio = SplitRatio.EVEN,
            participants = listOf(ME, PARTNER),
            lastModifiedBy = PARTNER,
            lastModifiedAtMillis = 1L
        )

        repositoryWith(fcmService, cachedBasisPoints = SEVENTY_IN_BASIS_POINTS)
            .publishCachedRatioIfMissing()

        coVerify(exactly = 0) { fcmService.queueNotificationForUser(any(), any()) }
    }

    // ---- UX-17: taking a proposal back ---------------------------------------------------

    @Test
    fun `the proposer may withdraw their own pending proposal`() = runTest {
        // `SplitRatioTransition.withdraw` was written and unit-tested when the feature landed,
        // and then called by nothing: a parent who proposed 70/30 by mistake could only wait for
        // the co-parent to answer it.
        coEvery { dataSource.getSettings(any()) } returns settingsWithProposalBy(ME)
        val written = slot<FamilySettings>()
        coEvery { dataSource.setSettings(any(), capture(written)) } returns Unit

        val result = repository(cachedBasisPoints = null, partnerId = PARTNER).withdrawProposal(FAMILY)

        assertTrue(result.isSuccess)
        assertNull(written.captured.proposal, "the proposal is gone")
        assertEquals(
            SplitRatio.EVEN.momShareBasisPoints,
            written.captured.ratio.momShareBasisPoints,
            "withdrawing decides nothing, so the agreed ratio does not move"
        )
    }

    @Test
    fun `a parent may not withdraw the co-parent's proposal`() = runTest {
        // Enforced by the transition, not by the screen: the co-parent's device could otherwise
        // cancel an answer they were owed, which is a decision disguised as a tidy-up.
        coEvery { dataSource.getSettings(any()) } returns settingsWithProposalBy(PARTNER)

        val result = repository(cachedBasisPoints = null, partnerId = PARTNER).withdrawProposal(FAMILY)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dataSource.setSettings(any(), any()) }
    }

    @Test
    fun `withdrawing pushes nothing of its own`() = runTest {
        // The other three answers announce a decision; a withdrawal decides nothing, and the
        // co-parent's banner is derived from the document. A push type for it would cost the
        // four places SEC-3 requires to agree, to say that something stopped existing. Its chat
        // card is `FamilySettingsSplitRequestTest`'s.
        val fcmService = mockk<FcmService>(relaxed = true)
        coEvery { dataSource.getSettings(any()) } returns settingsWithProposalBy(ME)
        coEvery { dataSource.setSettings(any(), any()) } returns Unit

        repositoryWith(fcmService).withdrawProposal(FAMILY)

        coVerify(exactly = 0) { fcmService.queueNotificationForUser(any(), any()) }
    }

    @Test
    fun `there is nothing to withdraw when no proposal is pending`() = runTest {
        coEvery { dataSource.getSettings(any()) } returns null

        val result = repository(cachedBasisPoints = null, partnerId = PARTNER).withdrawProposal(FAMILY)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dataSource.setSettings(any(), any()) }
    }

    // ---- One agreement per family (October 2026 isolation audit) ----------------------------

    @Test
    fun `the split follows a switch, and a family without its own agreement divides evenly`() = runTest {
        // Zuzana agreed 70/30 with Igor and nothing with Carol. The listener used to be pinned to
        // the family on screen when it started, and the cache was one number for the device: after
        // a switch the family with Carol showed 70/30 and priced every expense at Igor's split.
        val rows = MutableStateFlow(listOf(zuzana(onScreen = IGOR)))
        every { dataSource.observeSettings(IGOR_FAMILY) } returns flowOf(agreed(SEVENTY, IGOR_FAMILY))
        every { dataSource.observeSettings(CAROL_FAMILY) } returns flowOf(null)

        twoFamilyRepository(rows).observeSplit().test {
            assertEquals(FamilySplit(IGOR_FAMILY, null, SplitRatio.EVEN, answered = false), awaitItem())
            val withIgor = awaitItem()
            assertEquals(IGOR_FAMILY, withIgor.familyId)
            assertEquals(SplitRatio(SEVENTY), withIgor.ratio)

            rows.value = listOf(zuzana(onScreen = CAROL))
            assertEquals(FamilySplit(CAROL_FAMILY, null, SplitRatio.EVEN, answered = false), awaitItem())
            assertEquals(FamilySplit(CAROL_FAMILY, null, SplitRatio.EVEN), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SEVENTY, familyCache[IGOR_FAMILY], "the family with Igor keeps its own agreement")
        assertNull(familyCache[CAROL_FAMILY], "nothing was cached for a family that agreed nothing")
    }

    @Test
    fun `a new expense is priced at its own family's agreement and never another's`() {
        val repository = twoFamilyRepository(MutableStateFlow(listOf(zuzana(onScreen = CAROL))))
        familyCache[IGOR_FAMILY] = SEVENTY

        assertEquals(SplitRatio(SEVENTY), repository.agreedRatioOrDefault(IGOR_FAMILY))
        assertEquals(SplitRatio.EVEN, repository.agreedRatioOrDefault(CAROL_FAMILY))
    }

    @Test
    fun `an answer goes to the family whose proposal was shown, not the family on screen`() = runTest {
        // Igor's proposal was on screen; a push tap then switched to the family with Carol. The
        // answer used to read the family again at tap time and could accept Carol's proposal,
        // which the parent had never seen.
        val fcmService = mockk<FcmService>(relaxed = true)
        coEvery { dataSource.getSettings(IGOR_FAMILY) } returns proposalIn(IGOR_FAMILY, by = IGOR)
        coEvery { dataSource.getSettings(CAROL_FAMILY) } returns proposalIn(CAROL_FAMILY, by = CAROL)
        val written = slot<String>()
        coEvery { dataSource.setSettings(capture(written), any()) } returns Unit

        val result = twoFamilyRepository(MutableStateFlow(listOf(zuzana(onScreen = CAROL))), fcmService)
            .acceptProposal(IGOR_FAMILY)

        assertTrue(result.isSuccess)
        assertEquals(IGOR_FAMILY, written.captured)
        coVerify(exactly = 0) { dataSource.setSettings(CAROL_FAMILY, any()) }
        coVerify { fcmService.queueNotificationForUser(IGOR, any()) }
        assertEquals(SEVENTY, familyCache[IGOR_FAMILY])
    }

    @Test
    fun `a change goes to the family the screen showed, even after a switch`() = runTest {
        coEvery { dataSource.getSettings(any()) } returns null
        coEvery { dataSource.setSettings(any(), any()) } returns Unit

        val outcome = twoFamilyRepository(MutableStateFlow(listOf(zuzana(onScreen = CAROL))))
            .submitRatio(SplitRatio(SEVENTY), IGOR_FAMILY)

        assertEquals(RatioSubmission.APPLIED, outcome.getOrThrow())
        coVerify { dataSource.setSettings(IGOR_FAMILY, any()) }
        coVerify(exactly = 0) { dataSource.setSettings(CAROL_FAMILY, any()) }
    }

    @Test
    fun `a family the parent is no longer in takes no answer`() = runTest {
        val ended = CustodyKey.of(ME, "uid-ex")

        val repository = twoFamilyRepository(MutableStateFlow(listOf(zuzana(onScreen = CAROL))))

        assertTrue(repository.acceptProposal(ended).isFailure)
        assertTrue(repository.withdrawProposal(ended).isFailure)
        assertTrue(repository.submitRatio(SplitRatio.EVEN, ended).isFailure)
        coVerify(exactly = 0) { dataSource.setSettings(any(), any()) }
    }

    /** The per-family cache as a map, so a test can read what was cached for whom. */
    private val familyCache = mutableMapOf<String, Int>()

    private fun stubFamilyCache() {
        every { preferences.getFamilySplitRatioBasisPoints(any()) } answers { familyCache[firstArg()] }
        every { preferences.putFamilySplitRatioBasisPoints(any(), any()) } answers {
            familyCache[firstArg()] = secondArg()
        }
    }

    /** Zuzana, in a family with Igor and one with Carol, with [onScreen]'s on screen. */
    private fun zuzana(onScreen: String) = User(
        id = ME,
        email = "zuzana@example.test",
        name = "Zuzana",
        role = "mom",
        colorCode = "#FF4081",
        partnerId = onScreen,
        partnerIds = listOf(IGOR, CAROL)
    )

    /** A repository over [rows], the signed-in parent's Room row as a stream. */
    private fun twoFamilyRepository(
        rows: MutableStateFlow<List<User>>,
        fcmService: FcmService = mockk(relaxed = true)
    ): FamilySettingsRepository {
        every { preferences.getSplitRatioBasisPoints() } returns null
        every { preferences.putSplitRatioSlot(any()) } returns Unit
        stubFamilyCache()
        val userRepository = mockk<UserRepository> {
            coEvery { getCurrentUserId() } returns ME
            coEvery { getUserById(ME) } answers { rows.value.single() }
            every { observeCurrentUserId() } returns flowOf(ME)
            every { getAllUsers() } returns rows
        }
        return FamilySettingsRepository(
            dataSource,
            userRepository,
            preferences,
            fcmService,
            announcer,
            requestDeadlines
        )
    }

    /** [familyId]'s settings with [basisPoints] agreed and nothing pending. */
    private fun agreed(basisPoints: Int, familyId: String) = FamilySettings(
        ratio = SplitRatio(basisPoints),
        participants = familyId.split("__"),
        lastModifiedBy = ME,
        lastModifiedAtMillis = 1L
    )

    /** [familyId]'s settings with an even split agreed and [by]'s 70/30 proposal pending. */
    private fun proposalIn(familyId: String, by: String) = agreed(SplitRatio.EVEN.momShareBasisPoints, familyId).copy(
        proposal = SplitRatioProposal(ratio = SplitRatio(SEVENTY), proposedBy = by, proposedAtMillis = 1L)
    )

    /** The pair's settings with a proposal standing in [proposedBy]'s name. */
    private fun settingsWithProposalBy(proposedBy: String) = FamilySettings(
        ratio = SplitRatio.EVEN,
        participants = listOf(ME, PARTNER),
        lastModifiedBy = proposedBy,
        lastModifiedAtMillis = 1L,
        proposal = SplitRatioProposal(
            ratio = SplitRatio.ofMomPercent(70),
            proposedBy = proposedBy,
            proposedAtMillis = 1L
        )
    )

    /** A repository whose only interesting collaborator is [fcmService]. */
    private fun repositoryWith(
        fcmService: FcmService,
        cachedBasisPoints: Int? = null
    ): FamilySettingsRepository {
        every { preferences.getSplitRatioBasisPoints() } returns cachedBasisPoints
        every { preferences.putSplitRatioBasisPoints(any()) } returns Unit
        every { preferences.putSplitRatioSlot(any()) } returns Unit
        every { preferences.getSplitRatioSlot() } returns "mom"
        stubFamilyCache()
        val userRepository = mockk<UserRepository> {
            coEvery { getCurrentUserId() } returns ME
            coEvery { getUserById(ME) } returns User(
                id = ME,
                email = "olya@example.test",
                name = "Olya",
                role = "mom",
                colorCode = "#FF4081",
                partnerId = PARTNER
            )
        }
        return FamilySettingsRepository(
            dataSource,
            userRepository,
            preferences,
            fcmService,
            announcer,
            requestDeadlines
        )
    }

    private companion object {
        const val ME = "uid-zoe"
        const val PARTNER = "uid-alex"
        const val SEVENTY_IN_BASIS_POINTS = 7_000
        const val THIRTY_IN_BASIS_POINTS = 3_000

        /** The one family of the single-family cases: [ME] and [PARTNER]. */
        val FAMILY = CustodyKey.of(ME, PARTNER)

        const val IGOR = "uid-igor"
        const val CAROL = "uid-carol"
        val IGOR_FAMILY = CustodyKey.of(ME, IGOR)
        val CAROL_FAMILY = CustodyKey.of(ME, CAROL)
        const val SEVENTY = 7_000
    }
}
