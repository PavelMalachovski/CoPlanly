package com.coparently.app.data.local.dao

import android.app.Application
import androidx.room.Room
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.local.entity.KnownFamilyEntity
import com.coparently.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the DAOs answer when there is nothing to answer with (MON-24, Room's Kotlin output).
 *
 * Room's Java output handed a missing row back as null whatever the declared type said; its
 * Kotlin output throws for a single-row query declared non-null. Every such query here is declared
 * nullable, because its callers read null as "no such row", and this test holds that on the code
 * Room generates: a lookup or an observed row on an empty table is null, a count is zero, a
 * nullable column reads back null, and a DAO's own transaction body still runs. A declaration that
 * loses its `?` fails here rather than on the first launch with an empty table.
 *
 * Room in memory on Robolectric's framework SQLite: SQLCipher is the file's business, not the
 * queries' (`EncryptedDatabaseTest` covers it on a device).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DaoEmptyResultTest {

    private lateinit var database: CoPlanlyDatabase

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            CoPlanlyDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun `a lookup by id on an empty table is null`() = runBlocking<Unit> {
        assertNull(database.budgetDao().getBudgetById(MISSING))
        assertNull(database.changeRequestDao().getChangeRequestById(MISSING))
        assertNull(database.childInfoDao().getChildInfoById(MISSING))
        assertNull(database.custodyModelDao().getModelById(MISSING))
        assertNull(database.custodyScheduleDao().getScheduleById(MISSING))
        assertNull(database.eventDao().getEventById(MISSING))
        assertNull(database.expenseDao().getExpenseById(MISSING))
        assertNull(database.journalDao().getEntry(MISSING, ALICE))
        assertNull(database.knownFamilyDao().get(MISSING))
        assertNull(database.messageDao().getConversationById(MISSING))
        assertNull(database.parentingPlanDao().getEntry(MISSING, ALICE))
        assertNull(database.petDao().getPetById(MISSING))
        assertNull(database.userDao().getUserById(MISSING))
        assertNull(database.userDao().getUserByEmail("nobody@example.com"))
    }

    @Test
    fun `an observed row that does not exist emits null`() = runBlocking<Unit> {
        assertNull(database.childInfoDao().observeChildInfoById(MISSING).first())
        assertNull(database.knownFamilyDao().observe(MISSING).first())
        assertNull(database.messageDao().observeConversationById(MISSING).first())
        assertNull(database.petDao().observePetById(MISSING).first())
        assertNull(database.userDao().observeUserById(MISSING).first())
    }

    @Test
    fun `a count over no rows is zero`() = runBlocking<Unit> {
        val start = LocalDateTime.of(2026, 5, 1, 0, 0)
        val end = LocalDateTime.of(2026, 5, 31, 23, 59)

        assertEquals(0, database.eventDao().getEventsCountForParent("mom", start, end))
        assertEquals(0, database.eventDao().countRowsOfOtherFamilies(FAMILY, ALICE))
        assertEquals(0, database.childInfoDao().countRowsOfOtherFamilies(FAMILY, ALICE))
        assertEquals(0, database.expenseDao().countRowsOfOtherFamilies(FAMILY, ALICE))
        assertEquals(0, database.petDao().countRowsOfOtherFamilies(FAMILY, ALICE))
        assertEquals(0, database.messageDao().countMessagesSince(MISSING, 0L))
        assertEquals(0, database.messageDao().observeUnreadCount(MISSING, ALICE, 0L).first())
        assertEquals(0, database.userDao().setHealthConsent(MISSING, version = null, atMillis = null))
    }

    @Test
    fun `family ids leave out the rows that name no family`() = runBlocking<Unit> {
        val events = database.eventDao()
        events.insertEvent(event(id = "none", familyId = null))
        events.insertEvent(event(id = "blank", familyId = ""))
        events.insertEvent(event(id = "named", familyId = FAMILY))

        assertEquals(listOf(FAMILY), events.observeFamilyIds().first())
        assertEquals(emptyList(), database.childInfoDao().observeFamilyIds().first())
        assertEquals(emptyList(), database.petDao().observeFamilyIds().first())
    }

    @Test
    fun `null columns read back as null`() = runBlocking<Unit> {
        val stored = event(id = "e1")
        database.eventDao().insertEvent(stored)
        assertEquals(stored, database.eventDao().getEventById("e1"))

        val user = UserEntity(
            id = ALICE,
            email = "alice@example.com",
            name = "Alice",
            role = "mom",
            colorCode = "#FF4081"
        )
        database.userDao().insertUser(user)
        assertEquals(user, database.userDao().getUserById(ALICE))
        assertEquals(1, database.userDao().setHealthConsent(ALICE, version = null, atMillis = null))
        assertNull(database.userDao().getUserById(ALICE)?.healthConsentVersion)
        assertNull(database.userDao().observeUserById(ALICE).first()?.healthConsentAtMillis)

        // A null family is bound as NULL: it takes only rows that name no family out of force.
        database.custodyModelDao().deactivateFamily(null)
        assertEquals(emptyList(), database.custodyModelDao().getActiveModelsSync())
    }

    @Test
    fun `a DAO's own transaction body runs on an empty table`() = runBlocking<Unit> {
        val families = database.knownFamilyDao()
        families.rememberName(FAMILY, coParentUid = BOB, name = "Bob", atMillis = 1L)
        families.rememberSlots(FAMILY, coParentUid = BOB, mySlot = "mom", coParentSlot = "dad", atMillis = 2L)

        assertEquals(
            KnownFamilyEntity(
                familyId = FAMILY,
                coParentUid = BOB,
                coParentName = "Bob",
                mySlot = "mom",
                coParentSlot = "dad",
                updatedAtMillis = 2L
            ),
            families.get(FAMILY)
        )

        database.familyDocumentCacheDao().replaceFamily(FAMILY, emptyList())
        assertEquals(emptyList(), database.familyDocumentCacheDao().liveIn(FAMILY))
    }

    private fun event(id: String, familyId: String? = null): EventEntity {
        val at = LocalDateTime.of(2026, 5, 4, 9, 0)
        return EventEntity(
            id = id,
            title = "Dentist",
            startDateTime = at,
            eventType = "appointment",
            parentOwner = "mom",
            createdAt = at,
            updatedAt = at,
            updatedAtMillis = 1L,
            familyId = familyId
        )
    }

    private companion object {
        const val MISSING = "missing"
        const val ALICE = "alice"
        const val BOB = "bob"
        const val FAMILY = "alice_bob"
    }
}
