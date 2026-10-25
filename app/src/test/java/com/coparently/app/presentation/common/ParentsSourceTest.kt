package com.coparently.app.presentation.common

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.coparently.app.domain.family.FamilySlots
import com.coparently.app.domain.family.FamilySlotsSource
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.presentation.theme.ParentColorCode
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The sharing contract, and the cheap-question path.
 *
 * Both matter for cost rather than correctness, which is exactly why they need tests: nothing
 * about the rendered UI changes if this regresses, only the number of Firestore listeners.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParentsSourceTest {

    private val me = User(
        id = "u1",
        email = "olya@example.com",
        name = "Olya",
        role = "mom",
        colorCode = "#FF4081"
    )
    private val partner = PartnerSummary(
        id = "u2",
        name = "Pavel",
        email = "pavel@example.com",
        pairedSinceMillis = 1L,
        role = "dad"
    )

    private fun userRepository(): UserRepository = mockk {
        every { observeCurrentUserId() } returns MutableStateFlow<String?>("u1")
        every { getAllUsers() } returns MutableStateFlow(listOf(me))
        coEvery { getCurrentUserId() } returns "u1"
        coEvery { getUserById("u1") } returns me
    }

    private fun pairingRepository(): PairingRepository = mockk {
        every { observePairingState() } returns MutableStateFlow(PairingState.Paired(partner))
    }

    @Test
    fun `both parents resolve from their own sources`() = runTest {
        val source = ParentsSource(userRepository(), pairingRepository())

        source.observe().test {
            val parents = awaitItem()
            // `colorCode` rides along now that a parent picks their own colour: the signed-in
            // parent's comes off their Room row, the co-parent's off their profile document —
            // null here, because that fixture predates anyone choosing.
            assertEquals(NamedParent("u1", "mom", "Olya", colorCode = "#FF4081"), parents.me)
            assertEquals(NamedParent("u2", "dad", "Pavel"), parents.coParent)
            assertEquals(true, parents.isPaired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the family's own slots win over both profiles, here and on the cheap path`() = runTest {
        // Per-family slots (25 October 2026): Olya is slot 1 with her first co-parent and slot 2
        // with Pavel, and only `families/{id}.slots` says so; her profile keeps her first slot.
        val slots = object : FamilySlotsSource {
            val known = FamilySlots(mapOf("u1" to "dad", "u2" to "mom"))
            override fun observe(familyId: String) = flowOf(known.takeIf { familyId == "u1__u2" })
            override suspend fun get(familyId: String) = known.takeIf { familyId == "u1__u2" }
        }
        val users = mockk<UserRepository> {
            every { observeCurrentUserId() } returns MutableStateFlow<String?>("u1")
            every { getAllUsers() } returns MutableStateFlow(listOf(me.copy(partnerId = "u2")))
            coEvery { getCurrentUserId() } returns "u1"
            coEvery { getUserById("u1") } returns me.copy(partnerId = "u2")
        }
        val source = ParentsSource(users, pairingRepository(), slots)

        source.observe().test {
            val parents = awaitItem()
            assertEquals("dad", parents.me?.slot)
            assertEquals("mom", parents.coParent?.slot)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("dad", source.signedInSlot())
    }

    @Test
    fun `isPaired is true for a legacy pair even though coParent is null`() = runTest {
        // The partner document exists and is Paired, it just predates slot assignment - the
        // case a screen must still recognise "there is a co-parent to choose between" for
        // (AddEditEventScreen's selector), even though coParent itself is null because their
        // slot cannot be resolved.
        val legacyPartner = partner.copy(role = null)
        val pairing: PairingRepository = mockk {
            every { observePairingState() } returns MutableStateFlow(PairingState.Paired(legacyPartner))
        }
        val source = ParentsSource(userRepository(), pairing)

        source.observe().test {
            val parents = awaitItem()
            assertNull(parents.coParent)
            assertEquals(true, parents.isPaired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isPaired is false when there is no co-parent at all`() = runTest {
        val pairing: PairingRepository = mockk {
            every { observePairingState() } returns MutableStateFlow(PairingState.NotPaired())
        }
        val source = ParentsSource(userRepository(), pairing)

        source.observe().test {
            val parents = awaitItem()
            assertNull(parents.coParent)
            assertEquals(false, parents.isPaired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `two collectors share one upstream subscription`() = runTest {
        // observePairingState attaches four Firestore snapshot listeners per subscription while
        // paired, the co-parent's profile among them, so a per-collector copy is expensive.
        // Five ViewModels expose `parents`; without sharing, opening a second tab doubled it.
        val pairing = pairingRepository()
        val source = ParentsSource(userRepository(), pairing)

        source.observe().test {
            awaitItem()
            source.observe().test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) { pairing.observePairingState() }
    }

    // ---- the family on screen (tester report #11) --------------------------------------------

    private val barbora = PartnerSummary(
        id = "u2",
        name = "Barbora",
        email = "barbora@example.com",
        pairedSinceMillis = 1L,
        role = "dad"
    )
    private val pavel = PartnerSummary(
        id = "u3",
        name = "Pavel",
        email = "pavel@example.com",
        pairedSinceMillis = null,
        role = "dad",
        colorCode = "hue1|200"
    )

    /** Olya's row, projected onto [partnerUid]'s family — what the family switcher writes. */
    private fun showing(partnerUid: String?) = listOf(me.copy(partnerId = partnerUid))

    private fun usersShowing(rows: MutableStateFlow<List<User>>): UserRepository = mockk {
        every { observeCurrentUserId() } returns MutableStateFlow<String?>("u1")
        every { getAllUsers() } returns rows
    }

    /**
     * The server's link names Barbora — `partnerId` stays on the first co-parent — and Pavel, the
     * second, is read through a listener of his own.
     */
    private fun pairedWithBarbora(pavelsProfile: MutableStateFlow<PartnerSummary> = MutableStateFlow(pavel)) =
        mockk<PairingRepository> {
            every { observePairingState() } returns MutableStateFlow(PairingState.Paired(barbora))
            every { observeCoParentProfile("u3") } returns pavelsProfile
        }

    /**
     * The next answer that names a co-parent. A switch first answers "paired, not named yet"
     * while the new co-parent's profile is read — and must never answer with the previous one.
     */
    private suspend fun ReceiveTurbine<Parents>.awaitCoParent(): Parents {
        while (true) {
            val parents = awaitItem()
            if (parents.coParent != null) return parents
            assertEquals(true, parents.isPaired, "a family switch is not an unpair")
        }
    }

    @Test
    fun `the co-parent is the family on screen's, not the first one the server names`() = runTest {
        // Tester report #11: with Pavel's family switched to, the new-event form named Barbora
        // and drew her colour, because the names came from the server's `partnerId` alone.
        val source = ParentsSource(usersShowing(MutableStateFlow(showing("u3"))), pairedWithBarbora())

        source.observe().test {
            val parents = awaitCoParent()
            assertEquals(NamedParent("u3", "dad", "Pavel", colorCode = "hue1|200"), parents.coParent)
            assertEquals(true, parents.isPaired)
            // The expense balance attributes payments by this map: Pavel's must count as his.
            assertEquals(mapOf("u1" to "mom", "u3" to "dad"), parents.roleByUid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching families renames the co-parent, and never names one with the other's uid`() = runTest {
        val rows = MutableStateFlow(showing("u2"))
        val source = ParentsSource(usersShowing(rows), pairedWithBarbora())

        source.observe().test {
            assertEquals(NamedParent("u2", "dad", "Barbora"), awaitCoParent().coParent)

            rows.value = showing("u3")
            assertEquals(NamedParent("u3", "dad", "Pavel", colorCode = "hue1|200"), awaitCoParent().coParent)

            rows.value = showing("u2")
            assertEquals(NamedParent("u2", "dad", "Barbora"), awaitCoParent().coParent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the co-parent on screen renaming themselves reaches the names as it happens`() = runTest {
        val pavelsProfile = MutableStateFlow(pavel)
        val source = ParentsSource(usersShowing(MutableStateFlow(showing("u3"))), pairedWithBarbora(pavelsProfile))

        source.observe().test {
            assertEquals("Pavel", awaitCoParent().coParent?.name)

            pavelsProfile.value = pavel.copy(name = "Pavlík")

            assertEquals("Pavlík", awaitCoParent().coParent?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the family the pairing listener already follows costs no listener of its own`() = runTest {
        val pairing = pairedWithBarbora()
        val source = ParentsSource(usersShowing(MutableStateFlow(showing("u2"))), pairing)

        source.observe().test {
            assertEquals("Barbora", awaitItem().coParent?.name)
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { pairing.observeCoParentProfile(any()) }
    }

    @Test
    fun `an unpaired account names no co-parent, whatever a stale projection says`() = runTest {
        // The server decides *whether* there is a co-parent; the projection only decides which.
        val pairing = mockk<PairingRepository> {
            every { observePairingState() } returns MutableStateFlow(PairingState.NotPaired())
        }
        val source = ParentsSource(usersShowing(MutableStateFlow(showing("u3"))), pairing)

        source.observe().test {
            val parents = awaitItem()
            assertNull(parents.coParent)
            assertEquals(false, parents.isPaired)
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { pairing.observeCoParentProfile(any()) }
    }

    @Test
    fun `signedInSlot never touches the pairing side`() = runTest {
        // Asking for your own slot must not stand up a pairing subscription or fetch the
        // co-parent's document. confirmPickup used to collect the whole flow for this.
        val pairing = mockk<PairingRepository>() // no stubs: any call is an error
        val users = userRepository()
        val source = ParentsSource(users, pairing)

        assertEquals("mom", source.signedInSlot())

        verify { pairing wasNot Called }
        coVerify(exactly = 1) { users.getCurrentUserId() }
    }

    @Test
    fun `signedInSlot is null when signed out`() = runTest {
        val users = mockk<UserRepository> {
            every { observeCurrentUserId() } returns flowOf(null)
            every { getAllUsers() } returns flowOf(emptyList())
            coEvery { getCurrentUserId() } returns null
        }
        assertNull(ParentsSource(users, mockk()).signedInSlot())
    }

    @Test
    fun `Parents() the synthetic starting value every stateIn seeds with is not loaded`() {
        // Every ViewModel's `parents` StateFlow starts from this value before the upstream has
        // emitted. It must be indistinguishable from nothing only in what it names, never in
        // whether it is a real answer - otherwise a gate like AddEditEventScreen's selector
        // cannot tell "nobody to choose between" from "we don't know yet".
        assertEquals(false, Parents().loaded)
    }

    @Test
    fun `both phones draw one palette, whichever of the two parents is holding the phone`() {
        // Each phone is `me` to itself, so the same two people arrive the other way round on the
        // other phone. Two near-identical hues, and a pick against the other slot's unchosen
        // default, are the collisions that have to resolve the same on both.
        val alex = NamedParent("u1", "mom", "Alex", colorCode = ParentColorCode.custom(30))
        val sam = NamedParent("u2", "dad", "Sam", colorCode = ParentColorCode.custom(40))
        val families = listOf(
            alex to sam,
            alex.copy(colorCode = null) to sam.copy(colorCode = ParentColorChoice.PINK.storedCode)
        )

        families.forEach { (a, b) ->
            assertEquals(
                Parents(me = a, coParent = b, isPaired = true, loaded = true).palette,
                Parents(me = b, coParent = a, isPaired = true, loaded = true).palette,
                "${a.colorCode} and ${b.colorCode}"
            )
        }
    }

    @Test
    fun `observe emits loaded = true on its first real emission`() = runTest {
        val source = ParentsSource(userRepository(), pairingRepository())

        source.observe().test {
            val parents = awaitItem()
            assertEquals(true, parents.loaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `coParentUid answers without the pairing side, so a save path need not subscribe`() =
        runTest {
            // The reason this exists: `parents` is shared with `WhileSubscribed`, so a route
            // that never collects it reads `Parents()` forever — and a save path that asked it
            // who the co-parent is wrote every shared expense as a claim on nobody.
            val pairing = mockk<PairingRepository>() // no stubs: any call is an error
            val users = mockk<UserRepository> {
                coEvery { getCurrentUserId() } returns "u1"
                coEvery { getUserById("u1") } returns me.copy(partnerId = "u2")
            }

            assertEquals("u2", ParentsSource(users, pairing).coParentUid())

            verify { pairing wasNot Called }
        }

    @Test
    fun `coParentUid is null for an unpaired account, and for one with no local row`() = runTest {
        val unpaired = mockk<UserRepository> {
            coEvery { getCurrentUserId() } returns "u1"
            coEvery { getUserById("u1") } returns me
        }
        assertNull(ParentsSource(unpaired, mockk()).coParentUid())

        // The window `UserRepositoryImpl` leaves open on a fresh install: signed in, no row yet.
        val rowless = mockk<UserRepository> {
            coEvery { getCurrentUserId() } returns "u1"
            coEvery { getUserById("u1") } returns null
        }
        assertNull(ParentsSource(rowless, mockk()).coParentUid())
    }

    @Test
    fun `coParentUid refuses a row that names itself as its own partner`() = runTest {
        // Not hypothetical: a partnerId equal to the signed-in uid would put the payer twice
        // into `splitBetween` and charge them their own share on top of the whole amount.
        val selfPaired = mockk<UserRepository> {
            coEvery { getCurrentUserId() } returns "u1"
            coEvery { getUserById("u1") } returns me.copy(partnerId = "u1")
        }
        assertNull(ParentsSource(selfPaired, mockk()).coParentUid())
    }
}
