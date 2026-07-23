package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreEventDataSource
import com.coparently.app.domain.model.Event
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * Unit tests for [EventRepositoryImpl], focused on the entity<->domain mappers.
 *
 * Guards the 2026-07 review §2.1 data-loss regression: the mappers must round-trip
 * [Event.sharedWith], [Event.permissions] and [Event.lastModifiedBy] through Room,
 * otherwise editing an event silently drops its sharing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventRepositoryImplTest {

    private val gson = Gson()

    private lateinit var eventDao: EventDao
    private lateinit var firebaseAuthService: FirebaseAuthService
    private lateinit var firestoreEventDataSource: FirestoreEventDataSource
    private lateinit var repository: EventRepositoryImpl

    private val now = LocalDateTime.of(2026, 7, 23, 10, 0)

    @Before
    fun setup() {
        eventDao = mockk(relaxed = true)
        firestoreEventDataSource = mockk(relaxed = true)
        firebaseAuthService = mockk()
        // No signed-in user -> insert/update stay local, so the Firestore path doesn't
        // interfere with what we assert about the persisted entity.
        every { firebaseAuthService.getCurrentUser() } returns null
        repository = EventRepositoryImpl(eventDao, firebaseAuthService, firestoreEventDataSource)
    }

    @Test
    fun `getEventById maps sharedWith, permissions and lastModifiedBy back to domain`() = runTest {
        val entity = baseEntity().copy(
            sharedWithJson = gson.toJson(listOf("uidA", "uidB")),
            permissions = "read_only",
            lastModifiedBy = "uidX"
        )
        coEvery { eventDao.getEventById("e1") } returns entity

        val event = repository.getEventById("e1")

        assertEquals(listOf("uidA", "uidB"), event?.sharedWith)
        assertEquals("read_only", event?.permissions)
        assertEquals("uidX", event?.lastModifiedBy)
    }

    @Test
    fun `insertEvent persists sharedWith, permissions and lastModifiedBy`() = runTest {
        val event = baseDomain().copy(
            sharedWith = listOf("uidA", "uidB"),
            permissions = "read_only",
            lastModifiedBy = "uidX"
        )
        val captured = slot<EventEntity>()
        coEvery { eventDao.insertEvent(capture(captured)) } returns Unit

        repository.insertEvent(event)

        coVerify { eventDao.insertEvent(any()) }
        val stored = captured.captured
        assertEquals(listOf("uidA", "uidB"), gson.fromJson(stored.sharedWithJson, Array<String>::class.java).toList())
        assertEquals("read_only", stored.permissions)
        assertEquals("uidX", stored.lastModifiedBy)
    }

    @Test
    fun `getEventById tolerates blank sharedWith json`() = runTest {
        coEvery { eventDao.getEventById("e1") } returns baseEntity().copy(sharedWithJson = "")

        val event = repository.getEventById("e1")

        assertEquals(emptyList(), event?.sharedWith)
    }

    private fun baseEntity() = EventEntity(
        id = "e1",
        title = "Soccer",
        startDateTime = now,
        endDateTime = now.plusHours(1),
        eventType = "sports",
        parentOwner = "mom",
        createdAt = now,
        updatedAt = now
    )

    private fun baseDomain() = Event(
        id = "e1",
        title = "Soccer",
        startDateTime = now,
        endDateTime = now.plusHours(1),
        eventType = "sports",
        parentOwner = "mom",
        createdAt = now,
        updatedAt = now
    )
}
