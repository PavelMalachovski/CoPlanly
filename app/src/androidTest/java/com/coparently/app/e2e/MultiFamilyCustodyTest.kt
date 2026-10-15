package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.custody.SharedCustodyRead
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.CustodyModelType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * One parent in two families (M-8): each family keeps its own custody schedule on Alice's phone,
 * and neither family's schedule is ever written into the other family's document.
 *
 * The defect this pins (schema 45). The phone held one active custody row for every family. With
 * the Carol family on screen, three writers took the Bob family's row for Carol's: every sync's
 * `publishLocalIfMissing` created Carol's document from Bob's pattern, the mirror re-pushed the
 * newer row of one family over the other family's document, and the pattern editor carried one
 * family's seasonal layers and children's schedules into the other's. Each row now names its
 * family, and `CustodyModelRepository` reads and writes the family on screen's row only.
 *
 * What runs is what ships, against the real `firestore.rules`: Alice's `CustodyModelRepository`
 * and `SyncService` over her own Room, the repository's listener and Room mirror, and Bob's and
 * Carol's own clients reading their documents as their phones would. The layers and children's
 * schedules are `CustodyModelRepositoryTest`'s; the calendar that draws all this is a screen.
 */
@RunWith(AndroidJUnit4::class)
class MultiFamilyCustodyTest : TwoParentTest() {

    @Test
    fun eachOfAlicesFamiliesKeepsItsOwnScheduleAndNeitherReachesTheOther() = runBlocking<Unit> {
        val bobFamily = FamilyKey.of(alice.uid, bob.uid)
        EmulatorEnvironment.step("custody: Alice sets the Bob family's schedule")
        assertEquals(
            PatternSubmission.ACTIVATED,
            alice.custodyRepository.createWeekOnWeekOff(START, momFirst = true)
        )
        val bobPattern = checkNotNull(alice.custodyRepository.getActiveModelSync())

        val carol = joinCarolAndShowHerFamily()
        val carolFamily = FamilyKey.of(alice.uid, carol.uid)

        EmulatorEnvironment.step("custody: the Carol family reads no schedule, and a sync publishes none")
        assertNull("the Carol family read the Bob family's schedule", alice.custodyRepository.getActiveModelSync())
        // The sync's custody step is `publishLocalIfMissing`: it used to create the Carol family's
        // document from the Bob family's pattern.
        alice.syncService.performFullSync().getOrThrow()
        assertFalse(
            "a sync gave the Carol family the Bob family's schedule",
            EmulatorEnvironment.documentExists("custody_models/$carolFamily")
        )

        EmulatorEnvironment.step("custody: Alice sets the Carol family's own schedule")
        assertEquals(
            PatternSubmission.ACTIVATED,
            alice.custodyRepository.createTwoTwoThree(START, momStartsFirst = true)
        )
        val carolPattern = checkNotNull(alice.custodyRepository.getActiveModelSync())
        assertNotEquals(bobPattern.id, carolPattern.id)
        assertEquals(carolPattern.id, documentAsReadBy(carol).model.id)

        EmulatorEnvironment.step("custody: back to the Bob family, whose document stays his")
        assertNotNull(alice.selectedFamilySource.select(bobFamily))
        // The Bob family's document reaches Alice's mirror. The Carol family's row is newer, and
        // the mirror used to re-push it over this document as if it were the Bob family's.
        withTimeout(EmulatorParent.WAIT_MS) {
            alice.custodyRepository.observeSharedOf(bobFamily).first { it != null }
        }
        assertEquals(bobPattern.id, alice.custodyRepository.getActiveModelSync()?.id)
        val bobsDocument = documentAsReadBy(bob)
        assertEquals(bobPattern.id, bobsDocument.model.id)
        assertEquals(CustodyModelType.WEEK_ON_WEEK_OFF, bobsDocument.model.modelType)
        assertEquals(carolPattern.id, documentAsReadBy(carol).model.id)

        EmulatorEnvironment.step("custody: each family's schedule is in force on Alice's phone")
        assertNotNull(alice.selectedFamilySource.select(carolFamily))
        assertEquals(carolPattern.id, alice.custodyRepository.getActiveModelSync()?.id)
    }

    /**
     * Carol accepts Alice's second invitation, Alice's phone learns the family from its own
     * pairing listener, and the Carol family goes on screen — the state the defect needed.
     */
    private suspend fun joinCarolAndShowHerFamily(): EmulatorParent {
        val carol = newParent("Carol")
        val invite = alice.pairingRepository.createOrReuseInviteCode().getOrThrow()
        carol.pairingRepository.redeem(invite.code).getOrThrow()
        carol.awaitPairedWith(alice.uid)
        alice.awaitFamilyWith(carol.uid)
        assertNotNull(alice.selectedFamilySource.select(FamilyKey.of(alice.uid, carol.uid)))
        return carol
    }

    /** The custody document of [parent]'s family with Alice, read from the server by [parent]. */
    private suspend fun documentAsReadBy(parent: EmulatorParent): SharedCustody {
        val read = parent.custodyRepository.readShared()
        return checkNotNull((read as? SharedCustodyRead.Found)?.custody) {
            "${parent.name} could not read the family's custody document: $read"
        }
    }

    private companion object {
        /** A Monday; both patterns' anchor. */
        val START: LocalDate = LocalDate.of(2026, 10, 5)
    }
}
