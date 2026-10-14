package com.coparently.app.data.sync

import com.coparently.app.data.local.dao.BudgetDao
import com.coparently.app.data.local.dao.ChangeRequestDao
import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [FamilyIdBackfill].
 *
 * The marker rules are the whole of this class's risk, and each of the three has already failed
 * once in the two audience backfills this one copies: a boolean marker that never re-arms, a
 * marker left naming an ex-partner, and a marker shared between backfills so the second never
 * runs. What is *not* tested here is the SQL — `WHERE familyId IS NULL` needs a real database,
 * and `app/schemas/` stops at v14 (CQ-1), so there is no `MigrationTestHelper` fixture to build
 * a v30 one from.
 */
class FamilyIdBackfillTest {

    private lateinit var eventDao: EventDao
    private lateinit var expenseDao: ExpenseDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var childInfoDao: ChildInfoDao
    private lateinit var petDao: PetDao
    private lateinit var changeRequestDao: ChangeRequestDao
    private lateinit var preferences: EncryptedPreferences
    private lateinit var backfill: FamilyIdBackfill

    private val key = "${PreferenceKeys.FAMILY_ID_BACKFILL_PREFIX}$ALICE"

    @Before
    fun setUp() {
        eventDao = mockk(relaxed = true)
        expenseDao = mockk(relaxed = true)
        budgetDao = mockk(relaxed = true)
        childInfoDao = mockk(relaxed = true)
        petDao = mockk(relaxed = true)
        changeRequestDao = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        backfill = FamilyIdBackfill(
            eventDao,
            expenseDao,
            budgetDao,
            childInfoDao,
            petDao,
            changeRequestDao,
            preferences
        )
    }

    @Test
    fun `the first pairing stamps every shared table with the pair's id`() = runTest {
        every { preferences.getString(key) } returns null
        stubStamps()

        backfill.run(ALICE, listOf(BOB))

        // Spelled out rather than built with `FamilyKey.of`: the id is the name of a stored
        // document, so a test that computed it the same way the code does would agree with any
        // future change to the format instead of catching it.
        val familyId = FAMILY
        coVerify(exactly = 1) { eventDao.stampFamilyId(familyId) }
        coVerify(exactly = 1) { expenseDao.stampFamilyId(familyId) }
        coVerify(exactly = 1) { budgetDao.stampFamilyId(familyId) }
        coVerify(exactly = 1) { childInfoDao.stampFamilyId(familyId) }
        coVerify(exactly = 1) { petDao.stampFamilyId(familyId) }
        coVerify(exactly = 1) { changeRequestDao.stampFamilyId(familyId) }
        verify { preferences.putString(key, BOB) }
    }

    @Test
    fun `the id does not depend on which of the two is signed in`() = runTest {
        every { preferences.getString(any()) } returns null
        stubStamps()

        backfill.run(BOB, listOf(ALICE))

        coVerify { eventDao.stampFamilyId(FAMILY) }
    }

    @Test
    fun `a second pass for the same co-parent does nothing`() = runTest {
        every { preferences.getString(key) } returns BOB

        backfill.run(ALICE, listOf(BOB))

        coVerify(exactly = 0) { eventDao.stampFamilyId(any()) }
        coVerify(exactly = 0) { changeRequestDao.stampFamilyId(any()) }
        verify(exactly = 0) { preferences.putString(any(), any()) }
    }

    @Test
    fun `an unpaired account stamps nothing and disarms the marker`() = runTest {
        // The rule this pins: a marker left naming the ex-partner would make re-pairing with
        // that same person read as already done, and everything from before the unpair would
        // stay unstamped for good.
        every { preferences.getString(key) } returns BOB

        backfill.run(ALICE, emptyList())

        coVerify(exactly = 0) { eventDao.stampFamilyId(any()) }
        verify { preferences.putString(key, "") }
    }

    @Test
    fun `an account that was never paired does not rewrite an already blank marker`() = runTest {
        every { preferences.getString(key) } returns ""

        backfill.run(ALICE, emptyList())

        verify(exactly = 0) { preferences.putString(any(), any()) }
    }

    @Test
    fun `a blank marker re-arms the backfill for the same co-parent`() = runTest {
        // Unpair blanked it; pairing with the same person again must stamp, not skip. A boolean
        // marker is what gets this wrong, which is why the value is the partner's uid.
        every { preferences.getString(key) } returns ""
        stubStamps()

        backfill.run(ALICE, listOf(BOB))

        coVerify { eventDao.stampFamilyId(FAMILY) }
        verify { preferences.putString(key, BOB) }
    }

    @Test
    fun `a partner id equal to the signed-in uid is not a relationship`() = runTest {
        every { preferences.getString(key) } returns null

        backfill.run(ALICE, listOf(ALICE))

        coVerify(exactly = 0) { eventDao.stampFamilyId(any()) }
    }

    @Test
    fun `with two co-parents nothing is stamped and the marker is left as it was`() = runTest {
        // The guess this used to make: every unstamped row went into whichever family the phone
        // was showing, and a switch armed the marker for the other co-parent and did it again. A
        // child entered before Alice paired is not the Carol family's because Carol's family
        // happens to be on screen — nothing on this phone says which family it is about.
        every { preferences.getString(key) } returns BOB
        stubStamps()

        backfill.run(ALICE, listOf(BOB, CAROL))

        coVerify(exactly = 0) { eventDao.stampFamilyId(any()) }
        coVerify(exactly = 0) { expenseDao.stampFamilyId(any()) }
        coVerify(exactly = 0) { budgetDao.stampFamilyId(any()) }
        coVerify(exactly = 0) { childInfoDao.stampFamilyId(any()) }
        coVerify(exactly = 0) { petDao.stampFamilyId(any()) }
        coVerify(exactly = 0) { changeRequestDao.stampFamilyId(any()) }
        verify(exactly = 0) { preferences.putString(any(), any()) }
    }

    @Test
    fun `two co-parents on a phone that never stamped stamp nothing either`() = runTest {
        // Paired twice before the first sync ran: the rows written while unpaired stay "mine
        // alone", the value the server leaves them at too (`stampOwnBlankFamilyIds`, ambiguous).
        every { preferences.getString(key) } returns null
        stubStamps()

        backfill.run(ALICE, listOf(CAROL, BOB))

        coVerify(exactly = 0) { eventDao.stampFamilyId(any()) }
        coVerify(exactly = 0) { childInfoDao.stampFamilyId(any()) }
        verify(exactly = 0) { preferences.putString(any(), any()) }
    }

    @Test
    fun `back to one co-parent, the rows are stamped for that family`() = runTest {
        // Alice unpaired from Bob while Carol stayed: one family again, so the rows that stayed
        // null are unambiguous once more — exactly the case a re-pairing is.
        every { preferences.getString(key) } returns BOB
        stubStamps()

        backfill.run(ALICE, listOf(CAROL))

        coVerify(exactly = 1) { eventDao.stampFamilyId(CAROL_FAMILY) }
        coVerify(exactly = 1) { childInfoDao.stampFamilyId(CAROL_FAMILY) }
        verify { preferences.putString(key, CAROL) }
    }

    @Test
    fun `the marker is written only after every table has been stamped`() = runTest {
        // A crash partway through must repeat the whole pass. Every statement is
        // `WHERE familyId IS NULL`, so repeating it is free — writing the marker early is not.
        every { preferences.getString(key) } returns null
        stubStamps()
        coEvery { changeRequestDao.stampFamilyId(any()) } throws IllegalStateException("db gone")

        runCatching { backfill.run(ALICE, listOf(BOB)) }

        verify(exactly = 0) { preferences.putString(any(), any()) }
    }

    private fun stubStamps() {
        coEvery { eventDao.stampFamilyId(any()) } returns 1
        coEvery { expenseDao.stampFamilyId(any()) } returns 1
        coEvery { budgetDao.stampFamilyId(any()) } returns 0
        coEvery { childInfoDao.stampFamilyId(any()) } returns 2
        coEvery { petDao.stampFamilyId(any()) } returns 0
        coEvery { changeRequestDao.stampFamilyId(any()) } returns 0
    }

    private companion object {
        const val ALICE = "alice-uid"
        const val BOB = "bob-uid"
        const val CAROL = "carol-uid"
        const val FAMILY = "alice-uid__bob-uid"
        const val CAROL_FAMILY = "alice-uid__carol-uid"
    }
}
