package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.coparently.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for expenses.
 */
@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE deletedAtMillis IS NULL ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expenses WHERE deletedAtMillis IS NULL " +
            "AND date BETWEEN :start AND :end ORDER BY date DESC"
    )
    fun getExpensesForPeriod(start: LocalDate, end: LocalDate): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expenses WHERE deletedAtMillis IS NULL " +
            "AND category = :category ORDER BY date DESC"
    )
    fun getExpensesByCategory(category: String): Flow<List<ExpenseEntity>>

    /**
     * Gets an expense by id, **including a pending tombstone** — see
     * [EventDao.getEventById] for why the sync and delete paths need the unfiltered answer.
     * A caller answering a user's question filters at the repository boundary.
     */
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    /** Removes the row for real. Called once the deletion has reached Firestore. */
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    /**
     * Marks an expense deleted and queues the deletion for upload — see [EventDao.markDeleted],
     * which this mirrors exactly.
     *
     * @return 1 if this call is what deleted the expense, 0 if it was already deleted or absent.
     */
    @Query(
        "UPDATE expenses SET deletedAtMillis = :deletedAtMillis, syncedToFirestore = 0 " +
            "WHERE id = :id AND deletedAtMillis IS NULL"
    )
    suspend fun markDeleted(id: String, deletedAtMillis: Long): Int

    /** The outbox — **tombstones included**, for the reason [EventDao.getUnsyncedEvents] states. */
    @Query("SELECT * FROM expenses WHERE syncedToFirestore = 0")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>

    /**
     * Stamps [familyId] on every expenses row [authorUid] wrote that names no family yet
     * (`FamilyIdBackfill`).
     *
     * By author, never across the table: a co-parent's row of no family belongs to the family with
     * them, and this parent's own belongs to their one family only while nothing says there was an
     * earlier one ([countRowsOfOtherFamilies]). Stamping every null row with one family is how a
     * parent's records written after an unpair were moved into the next family (isolation audit,
     * October 2026). A row that already names a family keeps it.
     *
     * @return How many rows were stamped.
     */
    @Query("UPDATE expenses SET familyId = :familyId WHERE familyId IS NULL AND createdByFirebaseUid = :authorUid")
    suspend fun stampFamilyIdOfAuthor(familyId: String, authorUid: String): Int

    /**
     * How many expenses rows name a family of [myUid]'s other than [familyId] — evidence of an
     * earlier family, which makes this parent's own rows of no family ambiguous.
     */
    @Query(
        "SELECT COUNT(*) FROM expenses WHERE familyId IS NOT NULL AND familyId != '' " +
            "AND familyId != :familyId AND instr(familyId, :myUid) > 0"
    )
    suspend fun countRowsOfOtherFamilies(familyId: String, myUid: String): Int
}
