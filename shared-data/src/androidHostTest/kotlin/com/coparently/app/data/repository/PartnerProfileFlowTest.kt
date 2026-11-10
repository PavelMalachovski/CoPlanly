package com.coparently.app.data.repository

import app.cash.turbine.test
import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.FirestoreErrorCode
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient
import com.coparently.app.data.remote.firebase.StoredDocument
import com.coparently.app.domain.model.PartnerSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The co-parent's profile as a live stream (October 2026).
 *
 * It used to be a one-shot read repeated only when the signed-in account's own listeners moved,
 * so the co-parent's new colour or name waited for the app to be opened again. These tests pin
 * the listener's three promises: every change is delivered, a failure never blanks what was
 * already read, and nothing is left attached when the caller leaves.
 *
 * Over a [RecordingFirestoreClient] since MON-24 phase 2 (Task 8d), whose listener stands for the
 * SDK's: a snapshot is a send, a failure closes it with the error, as the client's flow fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PartnerProfileFlowTest {

    /** Every listener attached to `users/u2`, in order — a re-attachment adds one. */
    private val listeners = mutableListOf<ProducerScope<StoredDocument>>()

    /** How many of them were removed. */
    private var removed = 0

    private val firestore = RecordingFirestoreClient().apply {
        documentFlow = { path ->
            check(path == DocumentPath("users", PARTNER)) { "unexpected listener on $path" }
            callbackFlow {
                listeners += this
                awaitClose { removed++ }
            }
        }
    }

    @Test
    fun `every change to the co-parent's profile is delivered`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().trySend(profile(name = "Bob", colorCode = "#1976D2"))
            assertEquals("#1976D2", awaitItem().colorCode)

            listeners.last().trySend(profile(name = "Bob", colorCode = "hue1|30"))
            assertEquals("hue1|30", awaitItem().colorCode)

            listeners.last().trySend(profile(name = "Robert", colorCode = "hue1|30"))
            assertEquals("Robert", awaitItem().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a profile is read field by field, blanks as absent`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().trySend(
                profile(name = "Bob", email = "bob@example.com", photoUrl = "", role = "dad", colorCode = "")
            )

            assertEquals(
                PartnerSummary(
                    id = PARTNER,
                    name = "Bob",
                    email = "bob@example.com",
                    pairedSinceMillis = PAIRED_AT,
                    photoUrl = null,
                    role = "dad",
                    colorCode = null
                ),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a field of another type reads as absent, and the rest of the profile still arrives`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            // The co-parent writes their own profile, and the rules type only its name: a number
            // where the colour belongs must read as "not chosen" — read with `getString`, it threw
            // inside the listener, on the main thread, on every launch.
            listeners.last().trySend(
                fakeDocument(mapOf("name" to "Bob", "colorCode" to 42L, "email" to true))
            )

            val summary = awaitItem()
            assertEquals("Bob", summary.name)
            assertNull(summary.colorCode)
            assertEquals("", summary.email)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure before anything was read reports the bare partner`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().close(failure())

            assertEquals(BARE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure after a read keeps the profile, and the listener is attached again`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().trySend(profile(name = "Bob"))
            assertEquals("Bob", awaitItem().name)

            listeners.last().close(failure())
            expectNoEvents()

            testScheduler.advanceTimeBy(FIRST_BACKOFF_MILLIS + 1)
            testScheduler.runCurrent()
            assertEquals(2, listeners.size, "re-attached after the first backoff")

            listeners.last().trySend(profile(name = "Robert"))
            assertEquals("Robert", awaitItem().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `after the last re-attachment fails the stream ends quietly with what it had`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().close(failure())
            assertEquals(BARE, awaitItem())

            repeat(RETRIES) {
                testScheduler.advanceUntilIdle()
                listeners.last().close(failure())
            }
            testScheduler.advanceUntilIdle()

            awaitComplete()
            assertEquals(RETRIES + 1, listeners.size)
        }
    }

    @Test
    fun `the listener is removed when the collector leaves`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().trySend(profile(name = "Bob"))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        testScheduler.runCurrent()

        assertEquals(1, removed)
    }

    /** A profile document; a field not given is absent. */
    private fun profile(
        name: String? = null,
        email: String? = null,
        photoUrl: String? = null,
        role: String? = null,
        colorCode: String? = null
    ): StoredDocument = fakeDocument(
        mapOf(
            "name" to name,
            "email" to email,
            "profilePhotoUrl" to photoUrl,
            "role" to role,
            "colorCode" to colorCode
        )
    )

    private fun failure() = RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED)

    /** A profile document holding exactly [fields]. */
    private fun fakeDocument(fields: Map<String, Any?>) = StoredDocument(PARTNER, fields)

    private companion object {
        const val PARTNER = "u2"
        const val PAIRED_AT = 123L
        const val FIRST_BACKOFF_MILLIS = 2_000L
        const val RETRIES = 3
        val BARE = PartnerSummary(id = PARTNER, name = "", email = "", pairedSinceMillis = PAIRED_AT)
    }
}
