package com.coparently.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.data.local.entity.CustodyModelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The custody rows' SQL on a real Room database (schema 45): one active row per family, and a
 * family's write never takes another family's schedule out of force.
 *
 * `CustodyModelRepositoryTest` mocks the DAO, so it proves what the repository asks for, not that
 * the queries do it. In memory, like the e2e parents' databases: nothing here is about the file.
 */
@RunWith(AndroidJUnit4::class)
class CustodyModelDaoTest {

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
    fun aFamilysWriteLeavesAnotherFamilysScheduleInForce() = runBlocking<Unit> {
        val dao = database.custodyModelDao()
        dao.insertModel(row("bob", BOB_FAMILY))
        dao.insertModel(row("carol", CAROL_FAMILY))
        dao.insertModel(row("before-rows-named-a-family", familyId = null))

        // What the repository does before inserting the Bob family's new schedule.
        dao.deactivateFamily(BOB_FAMILY)
        dao.insertModel(row("bob-2", BOB_FAMILY))

        assertEquals(setOf("bob-2", "carol"), dao.getActiveModelsSync().map { it.id }.toSet())
        assertEquals(setOf("bob-2", "carol"), dao.observeActiveModels().first().map { it.id }.toSet())
    }

    @Test
    fun aWriteWithNoFamilyTakesOnlyRowsThatNameNoneOutOfForce() = runBlocking<Unit> {
        val dao = database.custodyModelDao()
        dao.insertModel(row("bob", BOB_FAMILY))
        dao.insertModel(row("mine", familyId = null))

        // An unpaired parent's save.
        dao.deactivateFamily(null)

        assertEquals(listOf("bob"), dao.getActiveModelsSync().map { it.id })
    }

    @Test
    fun aRowKeepsTheFamilyItWasStoredWith() = runBlocking<Unit> {
        val dao = database.custodyModelDao()
        dao.insertModel(row("bob", BOB_FAMILY))

        assertEquals(BOB_FAMILY, dao.getModelById("bob")?.familyId)
    }

    private fun row(id: String, familyId: String?) = CustodyModelEntity(
        id = id,
        modelType = "week_on_week_off",
        patternDays = 14,
        momDaysPattern = "[0,1,2,3,4,5,6]",
        startDate = "2026-08-03",
        createdAt = "2026-08-01T09:00:00",
        lastModifiedAtMillis = 1_785_578_400_000L,
        familyId = familyId
    )

    private companion object {
        const val BOB_FAMILY = "alice__bob"
        const val CAROL_FAMILY = "alice__carol"
    }
}
