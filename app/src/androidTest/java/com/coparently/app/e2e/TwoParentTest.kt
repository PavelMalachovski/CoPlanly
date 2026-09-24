package com.coparently.app.e2e

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.rules.Timeout
import java.util.TimeZone

/**
 * Two parents on the Firebase emulators, paired through the real callable before each test.
 *
 * Every test gets two **fresh** accounts, so nothing is shared between tests and nothing has to
 * be cleared from the emulators: a leftover document from a previous test names uids no later
 * test will ever sign in as.
 *
 * `runBlocking`, never `runTest`: these waits are on real network round trips to the host, and
 * `runTest`'s virtual clock would expire a `withTimeout` before the first packet left.
 */
abstract class TwoParentTest {

    /** Every thread's stack in logcat when a test fails; outside [timeout], so it sees the hang. */
    @get:Rule(order = 0)
    val threadDump: TestWatcher = EmulatorEnvironment.threadDumpOnFailure()

    /** Fails a stuck test with the stuck thread's stack rather than hanging the job. */
    @get:Rule(order = 1)
    val timeout: Timeout = EmulatorEnvironment.testTimeout()

    protected val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val started = mutableListOf<EmulatorParent>()
    private val originalZone: TimeZone = TimeZone.getDefault()

    /** The inviter: mints the code, keeps (or is given) slot 1. */
    protected lateinit var alice: EmulatorParent

    /** The accepter: redeems Alice's code through `acceptPairingInvitation`. */
    protected lateinit var bob: EmulatorParent

    @Before
    fun startParents() {
        EmulatorEnvironment.assumeEmulators()
        runBlocking {
            alice = newParent("Alice")
            bob = newParent("Bob")
            pair(inviter = alice, accepter = bob)
        }
    }

    @After
    fun stopParents() {
        TimeZone.setDefault(originalZone)
        started.forEach { it.close() }
    }

    /** Starts one more phone; closed after the test like the first two. */
    protected suspend fun newParent(name: String): EmulatorParent =
        EmulatorParent.create(context, name).also { started += it }

    /**
     * Pairs [accepter] with [inviter] the way two phones do, and waits for **both** to see it.
     *
     * The inviter's half matters as much as the accepter's: its phone never calls anything and
     * learns about the pairing only from its own snapshot listener, which is where
     * `PairingRepositoryImpl` mirrors the co-parent into Room and creates the conversation.
     */
    protected suspend fun pair(inviter: EmulatorParent, accepter: EmulatorParent) {
        val invite = inviter.pairingRepository.createOrReuseInviteCode().getOrThrow()
        accepter.pairingRepository.redeem(invite.code).getOrThrow()
        accepter.awaitPairedWith(inviter.uid)
        inviter.awaitPairedWith(accepter.uid)
    }

    /**
     * Runs [block] with the JVM's default zone set to [zoneId], as if on a phone set to it.
     *
     * `TimeZone.setDefault` is what both `java.util` and `ZoneId.systemDefault()` read, so this is
     * the whole of "the phone is in another zone" as far as the code under test can tell.
     */
    protected inline fun <T> inZone(zoneId: String, block: () -> T): T {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        return try {
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
