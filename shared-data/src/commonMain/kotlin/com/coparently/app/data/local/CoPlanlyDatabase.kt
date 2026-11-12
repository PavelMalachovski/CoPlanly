package com.coparently.app.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.coparently.app.data.local.dao.BudgetDao
import com.coparently.app.data.local.dao.ChangeRequestDao
import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.CustodyModelDao
import com.coparently.app.data.local.dao.CustodyScheduleDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.EventVersionOutboxDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.FamilyDocumentCacheDao
import com.coparently.app.data.local.dao.JournalDao
import com.coparently.app.data.local.dao.KnownFamilyDao
import com.coparently.app.data.local.dao.MessageDao
import com.coparently.app.data.local.dao.ParentingPlanDao
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.BudgetEntity
import com.coparently.app.data.local.entity.ChangeRequestEntity
import com.coparently.app.data.local.entity.ChildInfoEntity
import com.coparently.app.data.local.entity.ConversationEntity
import com.coparently.app.data.local.entity.CustodyModelEntity
import com.coparently.app.data.local.entity.CustodyScheduleEntity
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.local.entity.EventVersionOutboxEntity
import com.coparently.app.data.local.entity.ExpenseEntity
import com.coparently.app.data.local.entity.FamilyDocumentCacheEntity
import com.coparently.app.data.local.entity.JournalEntryEntity
import com.coparently.app.data.local.entity.KnownFamilyEntity
import com.coparently.app.data.local.entity.MessageEntity
import com.coparently.app.data.local.entity.ParentingPlanEntryEntity
import com.coparently.app.data.local.entity.PetEntity
import com.coparently.app.data.local.entity.UserEntity

/**
 * Room database for CoPlanly app.
 * Contains all entities and DAOs for local data storage.
 *
 * Common code since MON-24 phase 2 (Room for Kotlin Multiplatform): the entities, the DAOs and
 * [Converters] are the same on Android and on the iPhone, and so is the schema Room exports into
 * `app/schemas/`. How the file is opened is not: Android opens it through SQLCipher with the whole
 * migration chain (`buildCoPlanlyDatabase` in `androidMain`, `di/DatabaseBuilder.kt`), the iPhone
 * through the bundled SQLite driver under iOS Data Protection (`IosDatabase.kt`). A migration
 * from version 47 on is written in common code, against `SQLiteConnection` (`CommonMigrations.kt`),
 * so both platforms run it; the legacy chain up to 46 stays Android's (`DatabaseMigrations`).
 *
 * @see RoomDatabase
 */
@Database(
    entities = [
        EventEntity::class,
        UserEntity::class,
        CustodyScheduleEntity::class,
        CustodyModelEntity::class,
        ChildInfoEntity::class,
        PetEntity::class,
        MessageEntity::class,
        ConversationEntity::class,
        ExpenseEntity::class,
        BudgetEntity::class,
        ChangeRequestEntity::class,
        ParentingPlanEntryEntity::class,
        EventVersionOutboxEntity::class,
        JournalEntryEntity::class,
        FamilyDocumentCacheEntity::class,
        KnownFamilyEntity::class
    ],
    version = 47,
    exportSchema = true
)
@TypeConverters(Converters::class)
@ConstructedBy(CoPlanlyDatabaseConstructor::class)
abstract class CoPlanlyDatabase : RoomDatabase() {
    /**
     * Provides access to EventDao.
     */
    abstract fun eventDao(): EventDao

    /**
     * Provides access to UserDao.
     */
    abstract fun userDao(): UserDao

    /**
     * Provides access to CustodyScheduleDao.
     */
    abstract fun custodyScheduleDao(): CustodyScheduleDao

    /**
     * Provides access to CustodyModelDao.
     */
    abstract fun custodyModelDao(): CustodyModelDao

    /**
     * Provides access to ChildInfoDao.
     */
    abstract fun childInfoDao(): ChildInfoDao

    /**
     * Provides access to PetDao.
     */
    abstract fun petDao(): PetDao

    /**
     * Provides access to MessageDao.
     */
    abstract fun messageDao(): MessageDao

    /**
     * Provides access to ExpenseDao.
     */
    abstract fun expenseDao(): ExpenseDao

    /**
     * Provides access to BudgetDao.
     */
    abstract fun budgetDao(): BudgetDao

    /**
     * Provides access to ChangeRequestDao.
     */
    abstract fun changeRequestDao(): ChangeRequestDao

    /**
     * Provides access to ParentingPlanDao.
     */
    abstract fun parentingPlanDao(): ParentingPlanDao

    /**
     * Provides access to EventVersionOutboxDao — event revisions waiting for the server (MON-4).
     */
    abstract fun eventVersionOutboxDao(): EventVersionOutboxDao

    /**
     * Provides JournalDao — the private journal, which never leaves this phone (MON-22).
     */
    abstract fun journalDao(): JournalDao

    /**
     * Provides FamilyDocumentCacheDao — the last server answer for the vault index (MON-23).
     */
    abstract fun familyDocumentCacheDao(): FamilyDocumentCacheDao

    /**
     * Gets the KnownFamilyDao (schema 46).
     */
    abstract fun knownFamilyDao(): KnownFamilyDao
}

/**
 * Builds a [CoPlanlyDatabase] without reflection (Room KMP's `@ConstructedBy`). Room's processor
 * writes the `actual` for every target it runs on (KSP for Android and both iPhone targets, in
 * `shared-data/build.gradle.kts`); nothing here is written by hand.
 */
@Suppress("KotlinNoActualForExpect")
expect object CoPlanlyDatabaseConstructor : RoomDatabaseConstructor<CoPlanlyDatabase> {
    override fun initialize(): CoPlanlyDatabase
}
