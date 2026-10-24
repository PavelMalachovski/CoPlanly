package com.coparently.app.presentation.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.home.MonthMoney
import com.coparently.app.presentation.home.MonthSpendDependencies
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** How long the Family tab keeps its sources after the screen stops collecting. */
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The Family tab (new-user review, October 2026): the month's money card and the one hint the
 * tab gives — "Add your children" while the family has none. Everything else the tab lists is a
 * row into a screen that already exists.
 *
 * The money is [MonthMoney], the same computation as Home's spend tile, over the family on
 * screen's expenses, so the tab and Home never disagree about a month.
 *
 * @param monthSpendDependencies The expenses, the default currency and the family on screen
 * @param parentsSource The two parents, to attribute who paid what
 * @param userRepository The signed-in parent, whose side of the balance is reported
 * @param childInfoRepository The family on screen's children
 */
@HiltViewModel
class FamilyViewModel @Inject constructor(
    monthSpendDependencies: MonthSpendDependencies,
    parentsSource: ParentsSource,
    userRepository: UserRepository,
    childInfoRepository: ChildInfoRepository
) : ViewModel() {

    private val userId = flow { emit(userRepository.getCurrentUserId().orEmpty()) }

    /** This month's money in the family on screen, or null until it is known. */
    val money: StateFlow<MonthMoney?> = combine(
        monthSpendDependencies.expenseRepository.getAllExpenses(),
        monthSpendDependencies.familyViewSource.observe(),
        monthSpendDependencies.preferencesRepository.getDefaultCurrencyFlow(),
        userId,
        // Nobody known yet is an answer, as on Home: the spend shows at once and the balance
        // joins when the profiles load, rather than the whole card waiting on them.
        parentsSource.observe().onStart { emit(Parents()) }
    ) { expenses, view, currency, uid, parents ->
        MonthMoney.of(expenses, view, LocalDate.now(), currency, uid, parents.roleByUid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Whether the family on screen has a child record; the children row asks for one until then. */
    val hasChildren: StateFlow<Boolean> = childInfoRepository.getAllChildInfo()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)
}
