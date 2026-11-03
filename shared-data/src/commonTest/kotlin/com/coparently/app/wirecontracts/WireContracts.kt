package com.coparently.app.wire

/**
 * The wire contracts `:shared-data` runs: those whose readers and writers have moved here (MON-24
 * phase 2). A contract joins this list when its writer moves, and leaves the app's list then; until
 * every writer has moved, the app's own `WireContracts` runs all of them, these included.
 *
 * Kept outside `wire/`, which the app's unit tests compile too, so each module has its own.
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
