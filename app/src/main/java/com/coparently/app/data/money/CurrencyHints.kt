package com.coparently.app.data.money

import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.UserRepository
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this device knows that says which currency a parent pays in, for the default currency of a
 * new expense when they have not chosen one (release audit R-5).
 *
 * Two facts, in the order the owner ranked them: the country the parent gave for the calendar
 * (MON-13), then the currency of the family's most recent expense. The device region comes last
 * and is `PreferencesRepositoryImpl`'s own. A Czech account on an English (United States) phone
 * used to be offered dollars on every form while every expense it held was in crowns.
 *
 * The repositories are [Lazy] because the preferences repository is below both in the graph —
 * reading them on first collection keeps construction free of the question of who needs whom.
 */
@Singleton
open class CurrencyHints @Inject constructor(
    private val users: Lazy<UserRepository>,
    private val expenses: Lazy<ExpenseRepository>
) {

    /** The signed-in parent's country code, or null while nobody is signed in. */
    @OptIn(ExperimentalCoroutinesApi::class)
    open fun countryCode(): Flow<String?> =
        users.get().observeCurrentUserId()
            .flatMapLatest { uid ->
                if (uid == null) flowOf(null) else users.get().observeUserById(uid).map { it?.countryCode }
            }
            .distinctUntilChanged()

    /** The currency code of the most recently recorded expense this device holds, or null. */
    open fun lastExpenseCurrency(): Flow<String?> =
        expenses.get().getAllExpenses()
            .map { list -> list.maxByOrNull { it.createdAt }?.currency }
            .distinctUntilChanged()
}
