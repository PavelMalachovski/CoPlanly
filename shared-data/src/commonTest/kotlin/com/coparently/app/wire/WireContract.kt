package com.coparently.app.wire

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * One Firestore collection's wire format, as this build reads and writes it.
 *
 * Every function here calls the **production** mapper — the one `SyncService`, a repository or a
 * data source runs — never a copy of it, so a contract test that passes says something about the
 * code that ships. The fixtures they run over live in `app/src/test/resources/wire/` (see
 * [WireFixtures]); `WireContractTest` states the properties.
 *
 * **Where a contract lives** (MON-24 phase 2, Task 8a): this harness is common code in
 * `:shared-data`'s commonTest, and a contract moves here with its writer. `:shared-data` runs its own
 * contracts on the JVM and the iOS simulator; the app's unit tests compile this same directory and
 * run every contract, its own and these. Each module names its contracts in its own
 * `WireContracts` object.
 */
internal interface WireContract {

    /** The Firestore collection, which is also the fixture directory's name. */
    val collection: String

    /**
     * Keys this build's writer recomputes on every write, each with the reason, so their values
     * may legitimately differ after a read-then-write. Anything else that changes is a loss.
     */
    val ownedByWriter: Map<String, String> get() = emptyMap()

    /** Keys the writer must always put in the document, whatever the document it read lacked. */
    val alwaysWrites: Set<String>

    /** Top-level keys whose reader treats `""` as absent (see [WireCompare]). */
    val blankMeansAbsent: Set<String> get() = emptySet()

    /**
     * What this build reads from [document], as plain JSON-like values (strings, numbers,
     * booleans, lists, maps) so a fixture can state it; null when this build **skips** the
     * document, as its sync does with one it cannot parse. Must not throw for a skipped document.
     */
    fun read(document: Map<String, Any?>): Map<String, Any?>?

    /**
     * [document] read and written back by this build, exactly as the write would leave it on the
     * server (a `set()` replaces the document; an `update()` would merge), or null when this
     * build never rewrites such a document — a tombstone, a create-only revision.
     */
    fun roundTrip(document: Map<String, Any?>): Map<String, Any?>?

    /**
     * Collection-specific properties of [document] beyond the round trip, as failure messages;
     * empty when they hold.
     */
    fun invariants(document: Map<String, Any?>): List<String> = emptyList()

    /** What this build writes, from fixed inputs — the `wire/current/<collection>/` fixtures. */
    fun currentWrites(): List<CurrentWrite>
}

/**
 * One document this build writes, as `CurrentWireFixturesTest` pins it.
 *
 * @property case The fixture's file name, without `.json`.
 * @property about One sentence: which write path produced it.
 * @property document The document, from the production writer.
 * @property olderBuildsMayDrop Keys (dotted paths) an older build is allowed to lose when it reads
 *   this document and writes it back, each with the reason. A key a newer build adds to a
 *   collection older builds rewrite with `set()` belongs here, stated rather than discovered: the
 *   previous build's `WireContractTest` fails on any other loss.
 */
internal data class CurrentWrite(
    val case: String,
    val about: String,
    val document: Map<String, Any?>,
    val olderBuildsMayDrop: Map<String, String> = emptyMap()
)

/**
 * Runs [block] to its end on this thread, for a contract that calls a data source over
 * [com.coparently.app.data.remote.firebase.RecordingFirestoreClient], which answers without
 * suspending. Fails if [block] does suspend.
 */
internal fun <T> completedNow(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return checkNotNull(result) { "the block suspended; a recording client answers at once" }.getOrThrow()
}
