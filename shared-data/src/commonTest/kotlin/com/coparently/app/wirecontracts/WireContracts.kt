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
 * `PetWireContract` joined in Task 8d, once the pet mappers left `PetRepositoryImpl` for top-level
 * functions; it reads its one wall clock in the zone it runs in (see its KDoc). `ExpenseWireContract`
 * joined in Task 8f, with the expense mappers.
 */
internal object WireContracts : WireSuite {

    override val all: List<WireContract> = listOf(
        CustodyWireContract,
        ExpenseWireContract,
        PetWireContract,
        RequestDeadlineWireContract
    )

    override val complete: Boolean = false

    /**
     * These contracts read no wall clock: custody and clock times are UTC on the wire, an expense's
     * dates are naive on both sides, and the pets contract makes its wall clock from a UTC instant
     * in the zone it runs in.
     */
    override fun <T> inReaderZone(block: () -> T): T = block()
}
