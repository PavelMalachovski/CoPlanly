package com.coparently.app.presentation.common

import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

/**
 * A real [ParentsSource] over stubbed repositories, for ViewModel tests that only need the
 * dependency to exist.
 *
 * Deliberately the real class rather than a mock of it: the derivation it performs — matching
 * the signed-in row *by uid*, and dropping a co-parent whose slot is unknown — is the part that
 * must not silently change, so a test that stubs it away would keep passing if it did.
 *
 * @param me The signed-in user's stored row, or null when the profile has not loaded.
 * @param partner The paired co-parent the server's link names, or null when unpaired.
 * @param onScreen The co-parent of the family on screen: [partner], unless a test describes a
 *   parent in two families who switched to the second one, whose profile is then read through
 *   `PairingRepository.observeCoParentProfile` as on a phone.
 */
fun testParentsSource(
    me: User? = null,
    partner: PartnerSummary? = null,
    onScreen: PartnerSummary? = partner
): ParentsSource {
    // The signed-in row must carry the family on screen, or the two halves of this double
    // describe different accounts: `coParentUid()` and the names both read `partnerId` off this
    // row while `partner` below is wired only into the pairing side, so a fake without it reports
    // every paired account as unpaired — and a test would conclude the save path is fine when it
    // is not.
    val row = me?.let { it.copy(partnerId = onScreen?.id ?: it.partnerId) }
    val userRepository = mockk<UserRepository> {
        every { observeCurrentUserId() } returns flowOf(me?.id)
        every { getAllUsers() } returns flowOf(listOfNotNull(row))
        // The cheap path ParentsSource.signedInSlot() takes; it must not need the pairing side.
        coEvery { getCurrentUserId() } returns me?.id
        coEvery { getUserById(any()) } returns row
    }
    val pairingRepository = mockk<PairingRepository> {
        every { observePairingState() } returns flowOf(
            partner?.let { PairingState.Paired(it) } ?: PairingState.NotPaired()
        )
        if (onScreen != null && onScreen.id != partner?.id) {
            every { observeCoParentProfile(onScreen.id) } returns flowOf(onScreen)
        }
    }
    return ParentsSource(userRepository, pairingRepository)
}
