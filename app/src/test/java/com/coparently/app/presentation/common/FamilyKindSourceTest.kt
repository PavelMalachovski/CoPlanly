package com.coparently.app.presentation.common

import app.cash.turbine.test
import com.coparently.app.domain.model.FamilyKind
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Which sections the app offers: the union of this parent's answer and the co-parent's — the
 * co-parent of the family on screen, which with two families is not always the first one the
 * server's link names (tester report #11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FamilyKindSourceTest {

    private val me = User(
        id = "u1",
        email = "zuzana@example.com",
        name = "Zuzana",
        role = "mom",
        colorCode = "#FF4081",
        caresFor = setOf(FamilyKind.CHILDREN)
    )
    private val barbora = PartnerSummary(
        id = "u2",
        name = "Barbora",
        email = "barbora@example.com",
        pairedSinceMillis = 1L,
        role = "dad",
        caresFor = setOf(FamilyKind.CHILDREN)
    )
    private val pavel = PartnerSummary(
        id = "u3",
        name = "Pavel",
        email = "pavel@example.com",
        pairedSinceMillis = null,
        role = "dad",
        caresFor = setOf(FamilyKind.PETS)
    )

    /** Zuzana's row, projected onto [partnerUid]'s family. */
    private fun users(partnerUid: String?): UserRepository = mockk {
        every { observeCurrentUserId() } returns MutableStateFlow<String?>("u1")
        every { getAllUsers() } returns MutableStateFlow(listOf(me.copy(partnerId = partnerUid)))
    }

    private fun pairing(state: PairingState = PairingState.Paired(barbora)): PairingRepository = mockk {
        every { observePairingState() } returns MutableStateFlow(state)
        every { observeCoParentProfile("u3") } returns MutableStateFlow(pavel)
    }

    @Test
    fun `the co-parent's answer is the family on screen's`() = runTest {
        // Pavel has a dog; Barbora, the first co-parent the server names, does not.
        val source = FamilyKindSource(users(partnerUid = "u3"), pairing())

        source.observeTheirs().test {
            var theirs = awaitItem()
            // Empty only while Pavel's profile is read, right after his family is selected.
            while (theirs.isEmpty()) theirs = awaitItem()
            assertEquals(setOf(FamilyKind.PETS), theirs)
            cancelAndIgnoreRemainingEvents()
        }
        source.observe().test {
            var shown = awaitItem()
            while (FamilyKind.PETS !in shown) shown = awaitItem()
            assertEquals(setOf(FamilyKind.CHILDREN, FamilyKind.PETS), shown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the first family's co-parent answers for it, with no listener of its own`() = runTest {
        val pairing = pairing()
        val source = FamilyKindSource(users(partnerUid = "u2"), pairing)

        source.observeTheirs().test {
            assertEquals(setOf(FamilyKind.CHILDREN), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { pairing.observeCoParentProfile(any()) }
    }

    @Test
    fun `an unpaired account has no co-parent answer, whatever a stale projection says`() = runTest {
        val pairing = pairing(PairingState.NotPaired())
        val source = FamilyKindSource(users(partnerUid = "u3"), pairing)

        source.observeTheirs().test {
            assertEquals(emptySet<FamilyKind>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { pairing.observeCoParentProfile(any()) }
    }
}
