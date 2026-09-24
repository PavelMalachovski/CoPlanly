package com.coparently.app.wire

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.TimeZone

/**
 * "A co-parent on the previous build", without a second phone: every wire fixture, through this
 * build's production mappers.
 *
 * For each file under `app/src/test/resources/wire/` (see [WireFixtures]) it checks:
 * 1. **This build reads it** — without throwing — and what it reads is what the fixture's
 *    `reads` says; or, when the fixture says `skipped`, that this build skips it.
 * 2. **A read-then-write keeps what this build does not own.** The document goes through the
 *    reader and back out through the writer the sync uses; every path that changes must be either
 *    one the writer owns ([WireContract.ownedByWriter]) or one the fixture declares in
 *    `notPreserved`, with its reason. For a hand-written fixture the declared set must match the
 *    loss exactly, so a fixed loss is noticed too. Unknown `FamilyMemberRef`s, unreadable codec
 *    entries and a proposal's citation are how a newer build's data survives this one — those are
 *    the cases that must come out with nothing declared.
 * 3. The writer writes every key it must ([WireContract.alwaysWrites]), and the contract's own
 *    [WireContract.invariants] hold.
 *
 * **The other direction runs in CI.** The `upgrade` job copies this build's
 * `wire/current/` into the base commit's checkout and runs the *base's* copy of this class over
 * it (`--tests '*WireContractTest*'`), so "the previous build reads what this build writes" is the
 * previous build's own code answering. For a `current/` fixture this class is therefore lenient in
 * the two ways an older reader must be: it compares only the `reads` keys it knows, and a loss is
 * allowed when the newer build declared it (`notPreserved`, from [CurrentWrite.olderBuildsMayDrop]).
 * A collection it has no contract for is skipped. `CurrentWireFixturesTest` is the strict half
 * for the current build, and deliberately has a name this filter does not match.
 *
 * Runs in UTC, because a legacy chat timestamp is read in the reading phone's zone.
 */
@RunWith(Parameterized::class)
class WireContractTest(private val fixture: WireFixture) {

    private lateinit var originalZone: TimeZone

    @Before
    fun inUtc() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun `this build reads the document`() {
        val contract = contract()
        val read = contract.read(fixture.document)
        if (fixture.reads == null) {
            assertNotNull("${fixture.name}: a fixture with no reads must say why it is skipped", fixture.skipped)
            assertNull("${fixture.name}: expected this build to skip it (${fixture.skipped})", read)
            return
        }
        assertNotNull("${fixture.name}: this build skipped a document it must read", read)
        val expected = WireCompare.normalize(fixture.reads) as Map<*, *>
        val actual = WireCompare.normalize(read) as Map<*, *>
        if (fixture.current) {
            // An older reader compares what it knows. A read field it no longer has, or a newer
            // one it never had, is not a disagreement.
            val known = expected.keys.intersect(actual.keys)
            assertEquals(
                "${fixture.name}: this build reads the document differently from the build that wrote it",
                expected.filterKeys { it in known },
                actual.filterKeys { it in known }
            )
        } else {
            assertEquals("${fixture.name}: what this build reads changed", expected, actual)
        }
    }

    @Test
    fun `a read-then-write keeps what this build does not own`() {
        val contract = contract()
        // A document this build skips is never written back: there is nothing to round-trip.
        if (fixture.reads == null) return
        val written = contract.roundTrip(fixture.document) ?: return
        val lost = WireCompare.lostPaths(fixture.document, written, contract.blankMeansAbsent)
            .filterNot { path -> contract.ownedByWriter.keys.any { path == it || path.startsWith("$it.") } }
            .toSortedSet()
        val declared = fixture.notPreserved.keys.toSortedSet()
        if (fixture.current) {
            assertTrue(
                "${fixture.name}: this build, writing the document back, loses ${lost - declared}, which the " +
                    "build that wrote it did not declare as droppable (notPreserved). Written back: $written",
                declared.containsAll(lost)
            )
        } else {
            assertEquals(
                "${fixture.name}: the paths a read-then-write loses changed. Written back: $written",
                declared,
                lost
            )
        }
        val missing = contract.alwaysWrites - written.keys
        assertTrue("${fixture.name}: the writer left out $missing", missing.isEmpty())
    }

    @Test
    fun `the collection's own invariants hold`() {
        val contract = contract()
        if (fixture.reads == null) return
        val failures = contract.invariants(fixture.document)
        assertTrue("${fixture.name}: ${failures.joinToString("; ")}", failures.isEmpty())
    }

    private fun contract(): WireContract {
        val contract = WireContracts.forCollection(fixture.collection)
        // Only a newer build's `current/` fixture can name a collection this build has no contract
        // for — the previous build running over this build's output. A hand-written one cannot.
        if (fixture.current) assumeTrue("no contract for ${fixture.collection} in this build", contract != null)
        return checkNotNull(contract) { "${fixture.name}: no WireContract for ${fixture.collection}" }
    }

    companion object {
        /** Every fixture file, hand-written and current. */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<WireFixture> = WireFixtures.all()
    }
}
