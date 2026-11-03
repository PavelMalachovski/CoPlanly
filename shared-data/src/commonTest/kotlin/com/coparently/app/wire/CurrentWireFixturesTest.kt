package com.coparently.app.wire

import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `app/src/test/resources/wire/current/` is exactly what this build writes — the contract the
 * previous build is held to.
 *
 * Each [WireContract.currentWrites] document, with what this build reads back from it, must match
 * its committed file. A change to a writer therefore arrives as a visible fixture diff in review,
 * the way a UI change arrives as a screenshot diff, and the `upgrade` CI job then runs the *base*
 * build's `WireContractTest` over the new files: the previous build reading what this one writes.
 *
 * To regenerate after an intended format change, run
 * `UPDATE_WIRE_FIXTURES=1 ./gradlew :app:testDebugUnitTest --tests '*CurrentWireFixturesTest*' --rerun`
 * (every contract; `:shared-data:testDebugUnitTest` the same way rewrites only its own) and review
 * the diff. A missing file is written on any run (and the run fails, so it gets committed). A key
 * an older build will drop when it writes the document back goes in
 * [CurrentWrite.olderBuildsMayDrop], with the reason — the previous build's check fails on any
 * loss not declared there.
 *
 * Deliberately not named `*WireContractTest*`: the CI step that runs the base build's tests over
 * this build's fixtures must not run this one, which would compare the base's writer to them.
 */
class CurrentWireFixturesTest {

    private val update = WireFixtureLocation.UPDATE

    @Test
    fun `every current fixture is what this build writes`() {
        val problems = WireContracts.inReaderZone {
            WireContracts.all.flatMap { contract ->
                val dir = Path(WireFixtures.currentRoot, contract.collection)
                val cases = contract.currentWrites()
                val mismatches = cases.mapNotNull { write -> check(contract, write, Path(dir, "${write.case}.json")) }
                val stale = WireFixtures.jsonFiles(dir)
                    .filter { file -> file.name.removeSuffix(".json") !in cases.map(CurrentWrite::case) }
                mismatches + stale.mapNotNull { file ->
                    if (update) {
                        WireFixtures.delete(file)
                        null
                    } else {
                        "$file is not written by any ${contract.collection} case"
                    }
                }
            }
        }
        if (problems.isNotEmpty()) {
            fail(
                problems.joinToString("\n", postfix = "\n") +
                    "If the change is intended, run: ${WireFixtures.UPDATE_ENV}=1 ./gradlew :app:testDebugUnitTest " +
                    "--tests '*CurrentWireFixturesTest*' --rerun, then review and commit the diff under " +
                    "app/src/test/resources/wire/current/."
            )
        }
    }

    @Test
    fun `every contract has hand-written fixtures and every fixture directory a contract`() {
        val directories = WireFixtures.directories(WireFixtures.root)
            .map { it.name }
            .filter { it != "current" }
            .toSet()
        val collections = WireContracts.all.map { it.collection }.toSet()
        assertTrue(
            directories.containsAll(collections),
            "Contracts with no hand-written fixtures: ${collections - directories}"
        )
        // Only a module that runs every contract can call a directory an orphan.
        if (WireContracts.complete) {
            assertTrue(
                collections.containsAll(directories),
                "Fixture directories with no contract: ${directories - collections}"
            )
        }
    }

    /** Null when [file] matches [write]; otherwise what is wrong, after rewriting it if asked to. */
    private fun check(contract: WireContract, write: CurrentWrite, file: Path): String? {
        val expected = WireFixtures.currentJson(write, contract.read(write.document))
        val committed = if (WireFixtures.exists(file)) WireJson.parse(WireFixtures.read(file)) else null
        val matches = committed != null && WireCompare.normalize(committed) == WireCompare.normalize(expected)
        if (!matches && (update || committed == null)) {
            WireFixtures.write(file, WireJson.write(expected))
        }
        return when {
            matches || update -> null
            committed == null -> "$file was missing and has been written; review and commit it"
            else ->
                "$file no longer matches what this build writes.\n" +
                    "  committed: ${WireCompare.normalize(committed)}\n" +
                    "  now:       ${WireCompare.normalize(expected)}"
        }
    }
}
