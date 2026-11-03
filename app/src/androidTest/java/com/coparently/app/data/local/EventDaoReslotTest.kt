package com.coparently.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.data.local.entity.EventEntity
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The parent-slot re-stamp's two statements on a real Room database, in one transaction
 * (`EventDao.reslotOwnerAndPickup`, MON-24 phase 2, Task 8g).
 *
 * `ParentSlotMigrator` used to open the transaction itself with Android's
 * `RoomDatabase.withTransaction`; it moved to common code and calls the DAO's `@Transaction`
 * method instead. What has to stay true is that the owner's and the pickup's re-stamp land
 * together or not at all, so a trigger refuses the second statement here, and the first must be
 * taken back with it. The last test is the control: the same trigger with the two statements run
 * one by one keeps the first, so the trigger refuses only the pickup and the transaction is what
 * rolls the owner back.
 *
 * In memory, like `CustodyModelDaoTest`: nothing here is about the file.
 */
@RunWith(AndroidJUnit4::class)
class EventDaoReslotTest {

    private lateinit var database: CoPlanlyDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            CoPlanlyDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun bothReStampsLandTogether() = runBlocking<Unit> {
        val dao = database.eventDao()
        dao.insertEvent(event())

        val changed = dao.reslotOwnerAndPickup(from = "mom", to = "dad", myUid = ALICE)

        assertEquals(2, changed)
        val stored = requireNotNull(dao.getEventById(EVENT_ID))
        assertEquals("dad", stored.parentOwner)
        assertEquals("dad", stored.pickupConfirmedBy)
        assertFalse(stored.syncedToFirestore)
    }

    @Test
    fun aRefusedPickupReStampTakesTheOwnerReStampBackWithIt() = runBlocking<Unit> {
        val dao = database.eventDao()
        dao.insertEvent(event())
        refusePickupReStamps()

        val result = runCatching { dao.reslotOwnerAndPickup(from = "mom", to = "dad", myUid = ALICE) }

        assertTrue("the trigger refuses the pickup's re-stamp", result.isFailure)
        val stored = requireNotNull(dao.getEventById(EVENT_ID))
        assertEquals("the owner's re-stamp is rolled back with it", "mom", stored.parentOwner)
        assertEquals("mom", stored.pickupConfirmedBy)
        assertTrue(stored.syncedToFirestore)
    }

    @Test
    fun withoutTheTransactionTheOwnerReStampWouldStay() = runBlocking<Unit> {
        val dao = database.eventDao()
        dao.insertEvent(event())
        refusePickupReStamps()

        assertEquals(1, dao.reslotOwner(from = "mom", to = "dad", myUid = ALICE))
        assertTrue(runCatching { dao.reslotPickup(from = "mom", to = "dad", myUid = ALICE) }.isFailure)

        assertEquals("dad", requireNotNull(dao.getEventById(EVENT_ID)).parentOwner)
    }

    /** Refuses every statement that writes `dad` into `pickupConfirmedBy`, and nothing else. */
    private fun refusePickupReStamps() {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_pickup_restamp BEFORE UPDATE OF pickupConfirmedBy ON events " +
                "WHEN NEW.pickupConfirmedBy = 'dad' BEGIN SELECT RAISE(ABORT, 'refused by the test'); END"
        )
    }

    private fun event() = EventEntity(
        id = EVENT_ID,
        title = "Swimming",
        startDateTime = LocalDateTime(2026, 8, 3, 16, 0),
        eventType = "activity",
        parentOwner = "mom",
        createdAt = LocalDateTime(2026, 8, 1, 9, 0),
        updatedAt = LocalDateTime(2026, 8, 1, 9, 0),
        updatedAtMillis = 1_785_578_400_000L,
        syncedToFirestore = true,
        createdByFirebaseUid = ALICE,
        pickupConfirmedBy = "mom"
    )

    private companion object {
        const val ALICE = "alice-uid"
        const val EVENT_ID = "event-1"
    }
}
