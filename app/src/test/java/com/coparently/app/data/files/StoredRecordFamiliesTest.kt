package com.coparently.app.data.files

import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.entity.ChildInfoEntity
import com.coparently.app.data.local.entity.ExpenseEntity
import com.coparently.app.data.local.entity.PetEntity
import com.coparently.app.domain.files.RecordPhotoKind
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A photograph's upload tells a record being created from a stored record that names no family
 * (October 2026 isolation audit): only the second must stay out of the family on screen's folder.
 */
class StoredRecordFamiliesTest {

    private val expenseDao = mockk<ExpenseDao> {
        coEvery { getExpenseById(any()) } returns null
        coEvery { getExpenseById("receipt-of") } returns mockk<ExpenseEntity> { every { familyId } returns FAMILY }
    }
    private val childInfoDao = mockk<ChildInfoDao> {
        coEvery { getChildInfoById(any()) } returns null
        coEvery { getChildInfoById("only-mine") } returns mockk<ChildInfoEntity> { every { familyId } returns null }
    }
    private val petDao = mockk<PetDao> {
        coEvery { getPetById(any()) } returns null
        coEvery { getPetById("rex") } returns mockk<PetEntity> { every { familyId } returns FAMILY }
    }
    private val eventDao = mockk<EventDao> {
        coEvery { getEventById(any()) } returns null
    }
    private val stored = StoredRecordFamilies(expenseDao, childInfoDao, petDao, eventDao)

    @Test
    fun `a stored record answers with the family it names, none included`() = runTest {
        assertEquals(StoredRecordFamily(FAMILY), stored.of(RecordPhotoKind.RECEIPT, "receipt-of"))
        assertEquals(StoredRecordFamily(FAMILY), stored.of(RecordPhotoKind.PET, "rex"))
        assertEquals(StoredRecordFamily(null), stored.of(RecordPhotoKind.MEDICAL, "only-mine"))
    }

    @Test
    fun `a record this phone does not store yet is a new one`() = runTest {
        assertNull(stored.of(RecordPhotoKind.EVENT, "new-event"))
        // The kind decides the table: a pet's id asked as a receipt is not a stored expense.
        assertNull(stored.of(RecordPhotoKind.RECEIPT, "rex"))
    }

    private companion object {
        const val FAMILY = "alice__bob"
    }
}
