package com.coparently.app.data.files

import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.domain.files.RecordPhotoKind
import javax.inject.Inject

/**
 * A record as this phone stores it, for a photograph uploaded without its family.
 *
 * @property familyId The family the stored record names, or null for none.
 */
data class StoredRecordFamily(val familyId: String?)

/**
 * Which family a record already on this phone names — the question a photograph upload cannot
 * answer from what its caller passes (`RecordPhotoStorage.upload`).
 *
 * A caller passes the record's stored family, and null for a record it is creating, so null alone
 * cannot tell "a new record, about to be stamped with the family on screen" from "a stored record
 * that names no family". The second must not be filed under the family on screen while the parent
 * has two (`RecordPhotoPaths.uploadFamily`), and the four record kinds live in four tables, so this
 * reads the one the photograph's kind names.
 */
class StoredRecordFamilies @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val childInfoDao: ChildInfoDao,
    private val petDao: PetDao,
    private val eventDao: EventDao
) {

    /**
     * The stored record [recordId] of [kind], or null when this phone stores no such record yet.
     * A tombstoned row still counts as stored: it is the record being deleted, not a new one.
     */
    suspend fun of(kind: RecordPhotoKind, recordId: String): StoredRecordFamily? = when (kind) {
        RecordPhotoKind.RECEIPT -> expenseDao.getExpenseById(recordId)?.let { StoredRecordFamily(it.familyId) }
        RecordPhotoKind.MEDICAL -> childInfoDao.getChildInfoById(recordId)?.let { StoredRecordFamily(it.familyId) }
        RecordPhotoKind.PET -> petDao.getPetById(recordId)?.let { StoredRecordFamily(it.familyId) }
        RecordPhotoKind.EVENT -> eventDao.getEventById(recordId)?.let { StoredRecordFamily(it.familyId) }
    }
}
