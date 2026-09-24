package com.coparently.app.wire

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.TimeZone

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
 * `UPDATE_WIRE_FIXTURES=1 ./gradlew testDebugUnitTest --tests '*CurrentWireFixturesTest*' --rerun`
 * and review the diff. A missing file is written on any run (and the run fails, so it gets
 * committed). A key an older build will drop when it writes the document back goes in
 * [CurrentWrite.olderBuildsMayDrop], with the reason — the previous build's check fails on any
 * loss not declared there.
 *
 * Deliberately not named `*WireContractTest*`: the CI step that runs the base build's tests over
 * this build's fixtures must not run this one, which would compare the base's writer to them.
 */
class CurrentWireFixturesTest {

    private lateinit var originalZone: TimeZone
    private val update = System.getenv(WireFixtures.UPDATE_ENV).orEmpty().let { it == "1" || it == "true" }

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
    fun `every current fixture is what this build writes`() {
        val problems = mutableListOf<String>()
        WireContracts.all.forEach { contract ->
            val dir = File(WireFixtures.currentRoot, contract.collection)
            val cases = contract.currentWrites()
            cases.forEach { write -> check(contract, write, File(dir, "${write.case}.json"))?.let(problems::add) }
            val stale = dir.listFiles().orEmpty()
                .filter { it.extension == "json" && it.nameWithoutExtension !in cases.map(CurrentWrite::case) }
            stale.forEach { file ->
                if (update) file.delete() else problems += "$file is not written by any ${contract.collection} case"
            }
        }
        if (problems.isNotEmpty()) {
            fail(
                problems.joinToString("\n", postfix = "\n") +
                    "If the change is intended, run: ${WireFixtures.UPDATE_ENV}=1 ./gradlew testDebugUnitTest " +
                    "--tests '*CurrentWireFixturesTest*' --rerun, then review and commit the diff under " +
                    "app/src/test/resources/wire/current/."
            )
        }
    }

    @Test
    fun `every contract has hand-written fixtures, and every fixture directory a contract`() {
        val directories = WireFixtures.root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "current" }
            .map { it.name }
            .toSet()
        val collections = WireContracts.all.map { it.collection }.toSet()
        assertTrue(
            "Contracts with no hand-written fixtures: ${collections - directories}",
            directories.containsAll(collections)
        )
        assertTrue(
            "Fixture directories with no contract: ${directories - collections}",
            collections.containsAll(directories)
        )
    }

    /** Null when [file] matches [write]; otherwise what is wrong, after rewriting it if asked to. */
    private fun check(contract: WireContract, write: CurrentWrite, file: File): String? {
        val expected = WireFixtures.currentJson(write, contract.read(write.document))
        val committed = if (file.exists()) WireJson.parse(file.readText()) else null
        val matches = committed != null && WireCompare.normalize(committed) == WireCompare.normalize(expected)
        if (!matches && (update || committed == null)) {
            file.parentFile?.mkdirs()
            file.writeText(WireJson.write(expected))
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
