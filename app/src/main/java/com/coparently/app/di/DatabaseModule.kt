package com.coparently.app.di

import android.content.Context
import androidx.room.Room
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.BudgetDao
import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.CustodyScheduleDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.MessageDao
import com.coparently.app.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module providing database and DAO dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the Room database instance.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): CoPlanlyDatabase {
        return Room.databaseBuilder(
            context,
            CoPlanlyDatabase::class.java,
            "coparently_database"
        )
            .addMigrations(*com.coparently.app.data.local.DatabaseMigrations.ALL_MIGRATIONS)
            .fallbackToDestructiveMigration() // Allow destructive migration for missing migration paths (e.g., 3->6)
            .fallbackToDestructiveMigrationOnDowngrade() // Allow destructive migration when downgrading database version
            .build()
    }

    /**
     * Provides EventDao.
     */
    @Provides
    fun provideEventDao(database: CoPlanlyDatabase): EventDao {
        return database.eventDao()
    }

    /**
     * Provides UserDao.
     */
    @Provides
    fun provideUserDao(database: CoPlanlyDatabase): UserDao {
        return database.userDao()
    }

    /**
     * Provides CustodyScheduleDao.
     */
    @Provides
    fun provideCustodyScheduleDao(database: CoPlanlyDatabase): CustodyScheduleDao {
        return database.custodyScheduleDao()
    }

    /**
     * Provides ChildInfoDao.
     */
    @Provides
    fun provideChildInfoDao(database: CoPlanlyDatabase): ChildInfoDao {
        return database.childInfoDao()
    }

    /**
     * Provides MessageDao.
     */
    @Provides
    fun provideMessageDao(database: CoPlanlyDatabase): MessageDao {
        return database.messageDao()
    }

    /**
     * Provides ExpenseDao.
     */
    @Provides
    fun provideExpenseDao(database: CoPlanlyDatabase): ExpenseDao {
        return database.expenseDao()
    }

    /**
     * Provides BudgetDao.
     */
    @Provides
    fun provideBudgetDao(database: CoPlanlyDatabase): BudgetDao {
        return database.budgetDao()
    }

    /**
     * Provides CustodyModelDao.
     */
    @Provides
    fun provideCustodyModelDao(database: CoPlanlyDatabase): com.coparently.app.data.local.dao.CustodyModelDao {
        return database.custodyModelDao()
    }

    /**
     * Provides ChangeRequestDao.
     */
    @Provides
    fun provideChangeRequestDao(database: CoPlanlyDatabase): com.coparently.app.data.local.dao.ChangeRequestDao {
        return database.changeRequestDao()
    }
}

