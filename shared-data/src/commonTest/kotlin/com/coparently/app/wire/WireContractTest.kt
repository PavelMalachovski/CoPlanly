package com.coparently.app.wire

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

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
 * A collection it has no contract for is passed over. `CurrentWireFixturesTest` is the strict half
 * for the current build, and deliberately has a name this filter does not match.
 *
 * **Which contracts run is the module's** ([WireContracts]): `:shared-data` runs the contracts whose
 * writers it holds, on the JVM and the iOS simulator, and passes over the other collections; the
 * app runs every contract, as it always did. One test per property, over every fixture, with every
 * failure named by its fixture — common code has no parameterised runner.
 */
class WireContractTest {

    @Test
    fun `this build reads every document`() = everyFixture { fixture, contract ->
        val read = contract.read(fixture.document)
        if (fixture.reads == null) {
            return@everyFixture when {
                fixture.skipped == null -> "a fixture with no reads must say why it is skipped"
                read != null -> "expected this build to skip it (${fixture.skipped})"
                else -> null
            }
        }
        if (read == null) return@everyFixture "this build skipped a document it must read"
        val expected = WireCompare.normalize(fixture.reads) as Map<*, *>
        val actual = WireCompare.normalize(read) as Map<*, *>
        if (fixture.current) {
            // An older reader compares what it knows. A read field it no longer has, or a newer
            // one it never had, is not a disagreement.
            val known = expected.keys.intersect(actual.keys)
            val olderSays = expected.filterKeys { it in known }
            val thisSays = actual.filterKeys { it in known }
            if (olderSays != thisSays) {
                "this build reads the document differently from the build that wrote it: expected " +
                    "$olderSays, read $thisSays"
            } else {
                null
            }
        } else if (expected != actual) {
            "what this build reads changed: expected $expected, read $actual"
        } else {
            null
        }
    }

    @Test
    fun `a read-then-write keeps what this build does not own`() = everyFixture { fixture, contract ->
        // A document this build skips is never written back: there is nothing to round-trip.
        if (fixture.reads == null) return@everyFixture null
        val written = contract.roundTrip(fixture.document) ?: return@everyFixture null
        val lost = WireCompare.lostPaths(fixture.document, written, contract.blankMeansAbsent)
            .filterNot { path -> contract.ownedByWriter.keys.any { path == it || path.startsWith("$it.") } }
            .sorted()
        val declared = fixture.notPreserved.keys.sorted()
        val missing = contract.alwaysWrites - written.keys
        when {
            fixture.current && !declared.containsAll(lost) ->
                "this build, writing the document back, loses ${lost - declared.toSet()}, which the " +
                    "build that wrote it did not declare as droppable (notPreserved). Written back: $written"
            !fixture.current && declared != lost ->
                "the paths a read-then-write loses changed: declared $declared, lost $lost. Written back: $written"
            missing.isNotEmpty() -> "the writer left out $missing"
            else -> null
        }
    }

    @Test
    fun `the collection's own invariants hold`() = everyFixture { fixture, contract ->
        if (fixture.reads == null) return@everyFixture null
        contract.invariants(fixture.document).takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    @Test
    fun `there are fixtures for every contract this module runs`() {
        val collections = WireFixtures.all().map { it.collection }.toSet()
        val without = WireContracts.all.map { it.collection }.filterNot { it in collections }
        assertTrue(without.isEmpty(), "Contracts with no fixtures: $without")
    }

    /**
     * Runs [check] over every fixture this module has a contract for, in the reader's zone, and
     * fails once with every fixture's problem. A hand-written fixture of a collection no contract
     * here covers is the other module's when this module's list is partial, and an orphan when it is
     * complete; a `current/` one is a newer build's collection, passed over.
     */
    private fun everyFixture(check: (WireFixture, WireContract) -> String?) {
        val problems = WireContracts.inReaderZone {
            WireFixtures.all().mapNotNull { fixture ->
                val contract = WireContracts.forCollection(fixture.collection)
                when {
                    contract != null -> runCatching { check(fixture, contract) }
                        .getOrElse { "threw $it" }
                        ?.let { "${fixture.name}: $it" }
                    !fixture.current && WireContracts.complete ->
                        "${fixture.name}: no WireContract for ${fixture.collection}"
                    else -> null
                }
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
