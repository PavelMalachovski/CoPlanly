package com.coparently.app.wire

import java.util.TimeZone

/**
 * Every wire contract this build has, by collection. A new collection's contract is added here.
 *
 * The harness is `:shared-data`'s (`shared-data/src/commonTest/.../wire/`), compiled into these unit
 * tests too (see this module's build file), and so are the contracts whose writers moved there;
 * this module runs all of them, which is what the `upgrade` job's run of the base build's
 * `WireContractTest` relies on.
 */
internal object WireContracts : WireSuite {

    override val all: List<WireContract> = listOf(
        EventWireContract,
        CustodyWireContract,
        MessageWireContract,
        ChildInfoWireContract,
        PetWireContract,
        ExpenseWireContract,
        EventVersionWireContract,
        RequestDeadlineWireContract
    )

    override val complete: Boolean = true

    /** In UTC, because a legacy chat timestamp is read in the reading phone's zone. */
    override fun <T> inReaderZone(block: () -> T): T {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        return try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
