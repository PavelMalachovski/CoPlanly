package com.coparently.app.wire

/**
 * The wire contracts `:shared-data` runs: those whose readers and writers have moved here (MON-24
 * phase 2). A contract joins this list when its writer moves, and leaves the app's list then; until
 * every writer has moved, the app's own `WireContracts` runs all of them, these included.
 *
 * Kept outside `wire/`, which the app's unit tests compile too, so each module has its own.
 *
 * One exception: `MessageWireContract` moved to `wire/` with its writer (Task 8b) but runs from the
 * app's list alone. A message written by an older build carries a naive timestamp that is read in
 * the reading phone's zone, and only the app's suite, on the JVM, can set that zone to UTC for the
 * fixtures; here the simulator's zone would decide.
 *
 * And one contract stays in the app although its writer moved: `PetWireContract` (Task 8c). Its
 * reader and writer are member extensions of `PetRepositoryImpl`, and building a repository in
 * common code would take fakes of the Room DAOs and of the data source, which the app's tests get
 * from MockK. It runs from the app's list until those mappers leave the repository.
 */
internal object WireContracts : WireSuite {

    override val all: List<WireContract> = listOf(
        CustodyWireContract,
        RequestDeadlineWireContract
    )

    override val complete: Boolean = false

    /** These contracts read no wall clock: custody and clock times are UTC on the wire. */
    override fun <T> inReaderZone(block: () -> T): T = block()
}
