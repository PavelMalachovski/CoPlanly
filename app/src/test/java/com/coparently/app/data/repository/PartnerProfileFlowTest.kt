package com.coparently.app.data.repository

import app.cash.turbine.test
import com.coparently.app.domain.model.PartnerSummary
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PartnerProfileFlowTest {

    /** Every listener attached to `users/u2`, in order — a re-attachment adds one. */
    private val listeners = mutableListOf<EventListener<DocumentSnapshot>>()
    private val registration = mockk<ListenerRegistration>(relaxed = true)

    private val firestore = mockk<FirebaseFirestore>().also { firestore ->
        val users = mockk<CollectionReference>()
        val partnerDocument = mockk<DocumentReference>()
        every { firestore.collection("users") } returns users
        every { users.document(PARTNER) } returns partnerDocument
        every { partnerDocument.addSnapshotListener(any<EventListener<DocumentSnapshot>>()) } answers {
            listeners += firstArg<EventListener<DocumentSnapshot>>()
            registration
        }
    }

    @Test
    fun `every change to the co-parent's profile is delivered`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().onEvent(profile(name = "Bob", colorCode = "#1976D2"), null)
            assertEquals("#1976D2", awaitItem().colorCode)

            listeners.last().onEvent(profile(name = "Bob", colorCode = "hue1|30"), null)
            assertEquals("hue1|30", awaitItem().colorCode)

            listeners.last().onEvent(profile(name = "Robert", colorCode = "hue1|30"), null)
            assertEquals("Robert", awaitItem().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a profile is read field by field, blanks as absent`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().onEvent(
                profile(name = "Bob", email = "bob@example.com", photoUrl = "", role = "dad", colorCode = ""),
                null
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
            listeners.last().onEvent(
                fakeDocument(mapOf("name" to "Bob", "colorCode" to 42L, "email" to true)),
                null
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
            listeners.last().onEvent(null, failure())

            assertEquals(BARE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure after a read keeps the profile, and the listener is attached again`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().onEvent(profile(name = "Bob"), null)
            assertEquals("Bob", awaitItem().name)

            listeners.last().onEvent(null, failure())
            expectNoEvents()

            testScheduler.advanceTimeBy(FIRST_BACKOFF_MILLIS + 1)
            testScheduler.runCurrent()
            assertEquals(2, listeners.size, "re-attached after the first backoff")

            listeners.last().onEvent(profile(name = "Robert"), null)
            assertEquals("Robert", awaitItem().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `after the last re-attachment fails the stream ends quietly with what it had`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().onEvent(null, failure())
            assertEquals(BARE, awaitItem())

            repeat(RETRIES) {
                testScheduler.advanceUntilIdle()
                listeners.last().onEvent(null, failure())
            }
            testScheduler.advanceUntilIdle()

            awaitComplete()
            assertEquals(RETRIES + 1, listeners.size)
        }
    }

    @Test
    fun `the listener is removed when the collector leaves`() = runTest {
        firestore.partnerProfile(PARTNER, PAIRED_AT).test {
            listeners.last().onEvent(profile(name = "Bob"), null)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        testScheduler.runCurrent()

        verify(exactly = 1) { registration.remove() }
    }

    /** A profile document; a field not given is absent. */
    private fun profile(
        name: String? = null,
        email: String? = null,
        photoUrl: String? = null,
        role: String? = null,
        colorCode: String? = null
    ): DocumentSnapshot = fakeDocument(
        mapOf(
            "name" to name,
            "email" to email,
            "profilePhotoUrl" to photoUrl,
            "role" to role,
            "colorCode" to colorCode
        )
    )

    private fun failure() = mockk<FirebaseFirestoreException>(relaxed = true)

    private companion object {
        const val PARTNER = "u2"
        const val PAIRED_AT = 123L
        const val FIRST_BACKOFF_MILLIS = 2_000L
        const val RETRIES = 3
        val BARE = PartnerSummary(id = PARTNER, name = "", email = "", pairedSinceMillis = PAIRED_AT)
    }
}
