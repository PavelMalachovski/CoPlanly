package com.coparently.app.presentation.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.ai.AiConsentManager
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.HolidayFairness
import com.coparently.app.domain.custody.HolidayFairnessCalculator
import com.coparently.app.domain.holidays.HolidayLocation
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.review.MonthReview
import com.coparently.app.domain.review.MonthReviewCalculator
import com.coparently.app.domain.review.MonthStatsWire
import com.coparently.app.presentation.ai.AiAssistSession
import com.coparently.app.presentation.ai.AiAssistState
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

/** Keeps the flows warm across a configuration change. */
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The records a month's review is computed from, bundled for the constructor the way
 * `MonthSpendDependencies` bundles Home's: every one of them feeds [MonthReviewViewModel.state]
 * and nothing else.
 */
data class MonthReviewSources @Inject constructor(
    val custodyModelRepository: CustodyModelRepository,
    val eventRepository: EventRepository,
    val expenseRepository: ExpenseRepository,
    val userRepository: UserRepository,
    val childInfoRepository: ChildInfoRepository,
    val parentsSource: ParentsSource
)

/**
 * The AI half of the screen, bundled for the same reason: the summary button and nothing else.
 *
 * @property availability Whether this build offers the assist at all
 * @property consentManager The consent, asked before the first request
 * @property repository The `aiAssist` callable
 */
data class MonthReviewAssist @Inject constructor(
    val availability: AiAssistAvailability,
    val consentManager: AiConsentManager,
    val repository: AiAssistRepository
)

/**
 * What the screen shows.
 *
 * @property month The month shown
 * @property canGoForward Whether a later month can be opened; never past the current one
 * @property review The figures, or null until the records have answered
 */
data class MonthReviewUiState(
    val month: YearMonth,
    val canGoForward: Boolean,
    val review: MonthReview? = null
)

/** Custody inputs of one month, joined before the rest. */
private data class CustodyInputs(
    val model: CustodyModel?,
    val overrides: Map<String, DayOverride>,
    val fairness: HolidayFairness?
)

/**
 * Settings → Family → *Month in review*: a month in figures, and — when the build offers it and
 * the parent agrees — a short AI-written summary of those figures above them.
 *
 * The figures stand on their own; the summary is an addition, never a replacement, and it reads
 * **only the numbers** ([MonthStatsWire]), each parent by name.
 *
 * @param sources The records the figures come from
 * @param assist The summary's availability, consent and callable
 */
@HiltViewModel
class MonthReviewViewModel @Inject constructor(
    sources: MonthReviewSources,
    assist: MonthReviewAssist
) : ViewModel() {

    private val repository = assist.repository

    /** Whether the summary button is drawn at all. */
    val aiAvailable: Boolean = assist.availability.enabled

    private val currentMonth: YearMonth = YearMonth.now()
    private val selectedMonth = MutableStateFlow(currentMonth)

    /** Both parents, for naming slots and payers. */
    val parents: StateFlow<Parents> = sources.parentsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Parents())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val holidayLocation: Flow<HolidayLocation> = sources.userRepository.observeCurrentUserId()
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else sources.userRepository.observeUserById(uid) }
        .map { HolidayLocation.of(it?.countryCode, it?.regionCode) }
        .onStart { emit(HolidayLocation.Default) }

    private val custody: Flow<CustodyInputs> = combine(
        sources.custodyModelRepository.getActiveModel(),
        sources.custodyModelRepository.observeDayOverrides().onStart { emit(emptyMap()) },
        holidayLocation,
        sources.childInfoRepository.getAllChildInfo().onStart { emit(emptyList()) },
        selectedMonth
    ) { model, overrides, location, children, month ->
        CustodyInputs(
            model = model,
            overrides = overrides,
            fairness = model?.let {
                HolidayFairnessCalculator.of(
                    year = month.year,
                    custodyFor = CustodyResolver.resolver(it, overrides) { null },
                    provider = location.provider,
                    birthdays = children.mapNotNull { child ->
                        child.dateOfBirth?.let { born -> child.childName to born.toLocalDate() }
                    }
                )
            }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val events: Flow<List<Event>> = selectedMonth.flatMapLatest { month ->
        sources.eventRepository.getEventsByDateRange(
            month.atDay(1).atStartOfDay(),
            month.atEndOfMonth().atTime(LocalTime.MAX)
        ).onStart { emit(emptyList()) }
    }

    private val expenses: Flow<List<Expense>> = sources.expenseRepository.getAllExpenses().onStart { emit(emptyList()) }

    /** The screen's state. */
    val state: StateFlow<MonthReviewUiState> = combine(
        selectedMonth,
        custody,
        events,
        expenses,
        parents
    ) { month, inputs, monthEvents, allExpenses, family ->
        MonthReviewUiState(
            month = month,
            canGoForward = month < currentMonth,
            review = MonthReviewCalculator.of(
                month = month,
                custodyFor = CustodyResolver.resolver(inputs.model, inputs.overrides) { null },
                hasSchedule = inputs.model != null,
                overrides = inputs.overrides,
                fairness = inputs.fairness,
                events = monthEvents,
                expenses = allExpenses,
                currentUserId = family.me?.uid.orEmpty(),
                roleByUid = family.roleByUid
            )
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        MonthReviewUiState(currentMonth, canGoForward = false)
    )

    private val _summary = MutableStateFlow<String?>(null)

    /** The AI-written summary of the month shown, or null when none was asked for. */
    val summary: StateFlow<String?> = _summary.asStateFlow()

    private val session = AiAssistSession(viewModelScope, assist.consentManager) { text -> _summary.value = text }

    /** Where a summary request stands. */
    val summaryState: StateFlow<AiAssistState> = session.state

    /** Shows the month before. */
    fun previousMonth() = showMonth(selectedMonth.value.minusMonths(1))

    /** Shows the month after, never past the current one. */
    fun nextMonth() {
        if (selectedMonth.value < currentMonth) showMonth(selectedMonth.value.plusMonths(1))
    }

    /**
     * Asks for a short summary of the month on screen, written in [locale], naming each parent as
     * [names] does. Does nothing until the figures have loaded.
     */
    fun summarize(locale: String, names: ParentNames) {
        if (!aiAvailable) return
        val review = state.value.review?.takeIf { MonthStatsWire.canSend(it) } ?: return
        val stats = MonthStatsWire.of(review, names::labelFor, names::labelForUid)
        session.request { repository.summarizeMonth(review.month, locale, stats) }
    }

    /** "I agree" on the consent dialog. */
    fun agree() = session.agree()

    /** "Cancel" on the consent dialog. */
    fun decline() = session.decline()

    /** The failure was read. */
    fun dismissFailure() = session.dismissFailure()

    /** A summary belongs to one month: another month drops it and anything in flight. */
    private fun showMonth(month: YearMonth) {
        session.cancel()
        _summary.value = null
        selectedMonth.value = month
    }
}
