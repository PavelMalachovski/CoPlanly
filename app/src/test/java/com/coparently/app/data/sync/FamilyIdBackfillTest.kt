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
 * Unit tests for [FamilyIdBackfill]: it stamps only where the family is not a guess — a
 * co-parent's rows with the family with them, this parent's own with their one family while there
 * is exactly one and no earlier one (October 2026 isolation audit, F6). What is *not* tested here
 * is the SQL itself, which needs a real database.
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
    fun `the first pairing stamps own rows and the co-parent's with the pair's id`() = runTest {
        every { preferences.getString(key) } returns null

        backfill.run(ALICE, listOf(BOB))

        // Spelled out rather than built with `FamilyKey.of`: the id is the name of a stored
        // document, so a test that computed it the same way the code does would agree with any
        // future change to the format instead of catching it.
        coVerify(exactly = 1) { eventDao.stampFamilyIdOfAuthor(FAMILY, ALICE) }
        coVerify(exactly = 1) { eventDao.stampFamilyIdOfAuthor(FAMILY, BOB) }
        coVerify(exactly = 1) { expenseDao.stampFamilyIdOfAuthor(FAMILY, ALICE) }
        coVerify(exactly = 1) { childInfoDao.stampFamilyIdOfAuthor(FAMILY, ALICE) }
        coVerify(exactly = 1) { petDao.stampFamilyIdOfAuthor(FAMILY, ALICE) }
        coVerify(exactly = 1) { budgetDao.stampFamilyId(FAMILY) }
        coVerify(exactly = 1) { changeRequestDao.stampFamilyIdOfCounterpart(FAMILY, BOB) }
        verify { preferences.putString(key, BOB) }
    }

    @Test
    fun `the id does not depend on which of the two is signed in`() = runTest {
        every { preferences.getString(any()) } returns null

        backfill.run(BOB, listOf(ALICE))

        coVerify { eventDao.stampFamilyIdOfAuthor(FAMILY, BOB) }
        coVerify { eventDao.stampFamilyIdOfAuthor(FAMILY, ALICE) }
    }

    @Test
    fun `a second pass repeats harmlessly and leaves the marker as it is`() = runTest {
        // Every statement is `WHERE familyId IS NULL`; a re-download can blank a row again, so the
        // pass runs every sync instead of once per co-parent.
        every { preferences.getString(key) } returns BOB

        backfill.run(ALICE, listOf(BOB))

        coVerify(exactly = 1) { eventDao.stampFamilyIdOfAuthor(FAMILY, ALICE) }
        verify(exactly = 0) { preferences.putString(any(), any()) }
    }

    @Test
    fun `an unpaired account stamps nothing and disarms the marker`() = runTest {
        every { preferences.getString(key) } returns BOB

        backfill.run(ALICE, emptyList())

        coVerify(exactly = 0) { eventDao.stampFamilyIdOfAuthor(any(), any()) }
        coVerify(exactly = 0) { budgetDao.stampFamilyId(any()) }
        verify { preferences.putString(key, "") }
    }

    @Test
    fun `an account that was never paired does not rewrite an already blank marker`() = runTest {
        every { preferences.getString(key) } returns ""

        backfill.run(ALICE, emptyList())

        verify(exactly = 0) { preferences.putString(any(), any()) }
    }

    @Test
    fun `a partner id equal to the signed-in uid is not a relationship`() = runTest {
        every { preferences.getString(key) } returns null

        backfill.run(ALICE, listOf(ALICE))

        coVerify(exactly = 0) { eventDao.stampFamilyIdOfAuthor(any(), any()) }
    }

    @Test
    fun `with two co-parents each one's rows join their family and the own rows stay unassigned`() = runTest {
        // Nothing on this phone says which family Alice's own unstamped row is about; Bob's and
        // Carol's can only be about the family with them.
        every { preferences.getString(key) } returns BOB

        backfill.run(ALICE, listOf(BOB, CAROL))

        coVerify(exactly = 1) { eventDao.stampFamilyIdOfAuthor(FAMILY, BOB) }
        coVerify(exactly = 1) { eventDao.stampFamilyIdOfAuthor(CAROL_FAMILY, CAROL) }
        coVerify(exactly = 1) { childInfoDao.stampFamilyIdOfAuthor(CAROL_FAMILY, CAROL) }
        coVerify(exactly = 1) { changeRequestDao.stampFamilyIdOfCounterpart(CAROL_FAMILY, CAROL) }
        coVerify(exactly = 0) { eventDao.stampFamilyIdOfAuthor(any(), ALICE) }
        coVerify(exactly = 0) { childInfoDao.stampFamilyIdOfAuthor(any(), ALICE) }
        coVerify(exactly = 0) { budgetDao.stampFamilyId(any()) }
        verify { preferences.putString(key, "") }
    }

    @Test
    fun `an earlier family on the phone keeps the own rows unassigned`() = runTest {
        // Bob unpaired; Alice wrote "Ema glasses" alone, then paired with Carol. The row may be
        // about Bob's family — the server answers `priorRelationship` for exactly this — so it is
        // not moved into Carol's. Carol's own rows still join her family.
        every { preferences.getString(key) } returns null
        coEvery { eventDao.countRowsOfOtherFamilies(CAROL_FAMILY, ALICE) } returns 3

        backfill.run(ALICE, listOf(CAROL))

        coVerify(exactly = 0) { eventDao.stampFamilyIdOfAuthor(any(), ALICE) }
        coVerify(exactly = 0) { expenseDao.stampFamilyIdOfAuthor(any(), ALICE) }
        coVerify(exactly = 0) { budgetDao.stampFamilyId(any()) }
        coVerify(exactly = 1) { eventDao.stampFamilyIdOfAuthor(CAROL_FAMILY, CAROL) }
        verify(exactly = 0) { preferences.putString(any(), any()) }
    }

    @Test
    fun `an ex-partner's rows are never stamped into the next family`() = runTest {
        every { preferences.getString(key) } returns null

        backfill.run(ALICE, listOf(CAROL))

        coVerify(exactly = 0) { eventDao.stampFamilyIdOfAuthor(any(), BOB) }
        coVerify(exactly = 0) { changeRequestDao.stampFamilyIdOfCounterpart(any(), BOB) }
    }

    private companion object {
        const val ALICE = "alice-uid"
        const val BOB = "bob-uid"
        const val CAROL = "carol-uid"
        const val FAMILY = "alice-uid__bob-uid"
        const val CAROL_FAMILY = "alice-uid__carol-uid"
    }
}
