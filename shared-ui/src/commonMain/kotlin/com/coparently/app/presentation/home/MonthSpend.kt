package com.coparently.app.presentation.home

import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.PreferencesRepository

/** A per-currency subtotal of this month's spending. */
data class CurrencyAmount(val currency: String, val amount: Double)

/**
 * This month's shared spending, kept split by currency. The app does no FX conversion, so
 * amounts in different currencies must never be added into one figure — [byCurrency] carries a
 * separate subtotal for each, largest first.
 */
data class MonthSpend(val byCurrency: List<CurrencyAmount>)

/**
 * The two repositories `HomeViewModel.monthSpend` is derived from, bundled into one
 * constructor-injected value.
 *
 * `HomeViewModel` (still in the app) already sat at the constructor-parameter limit before the
 * currency fallback (finding 4) needed [PreferencesRepository] alongside the existing
 * [ExpenseRepository] — both of which only ever feed `monthSpend`. Bundling them keeps the real
 * dependency count visible (no `@Suppress`, no widened detekt threshold) while still giving
 * `monthSpend` everything it needs.
 *
 * [familyViewSource] joined for the same reason: it decides which of the month's expenses the
 * tile adds up — the family on screen's own (owner decision, October 2026).
 *
 * In `:shared-ui` since MON-24 phase 2 (Task 10, slice 10d), with [MonthMoney], because the Family
 * tab's `FamilyViewModel` reads the same bundle.
 */
data class MonthSpendDependencies(
    val expenseRepository: ExpenseRepository,
    val preferencesRepository: PreferencesRepository,
    val familyViewSource: FamilyViewSource
)
