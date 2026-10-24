package com.coparently.app.presentation.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.holidays.HolidayCountry
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.money.currencyOfRegion
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.theme.ParentColorChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject

/**
 * What the wizard knows about the co-parent link.
 *
 * Three answers rather than a boolean, because "not yet known" and "nobody" call for different
 * screens: the first must not claim the parent is alone, and the second must offer them a way
 * to stop being.
 */
sealed interface CoParentLink {
    /** The pairing listener has not answered yet. */
    data object Unknown : CoParentLink

    /** No co-parent linked. */
    data object None : CoParentLink

    /**
     * Linked.
     *
     * @property name The co-parent's display name, possibly blank — an email/password account
     *   that never set one. The screen substitutes its translated fallback for a blank.
     */
    data class Linked(val name: String) : CoParentLink
}

/**
 * What the link brought back from the co-parent's side.
 *
 * Counts and flags rather than the records themselves: the records land in Room and reach the
 * screens that show them through the same flows every other screen reads. This is only what the
 * first step says about them.
 *
 * @property children Child records the co-parent created
 * @property pets Pet records the co-parent created
 * @property hasCustodySchedule Whether an active custody pattern exists — the pair's, or one
 *   this device already had
 * @property hasSplitAgreement Whether the pair has an agreed expense split
 */
data class CoParentData(
    val children: Int = 0,
    val pets: Int = 0,
    val hasCustodySchedule: Boolean = false,
    val hasSplitAgreement: Boolean = false
) {
    /** True when nothing has arrived yet. */
    val isEmpty: Boolean
        get() = children == 0 && pets == 0 && !hasCustodySchedule && !hasSplitAgreement
}

/**
 * Where the fetch of the co-parent's records stands.
 *
 * The fetch is bounded: it asks for a sync, watches Room for anything the co-parent created, and
 * gives up after a while with whatever it found — including nothing, which is a real answer
 * ("their phone has not synced yet") rather than an error.
 */
sealed interface CoParentFetch {
    /** Not linked, or not started. */
    data object Idle : CoParentFetch

    /** A sync has been requested and Room is being watched. */
    data object Running : CoParentFetch

    /**
     * Finished, with what was found. [found] may be empty: nothing had arrived within the wait.
     */
    data class Done(val found: CoParentData) : CoParentFetch
}

/**
 * Everything the wizard has collected so far, plus which step is showing it.
 *
 * The parent's fields are held flat rather than as a `User` because the wizard owns no record
 * wholesale: it edits a few of the parent's fields, written onto a **freshly read** row at save
 * time. Holding the whole object would invite the mistake `ProfileViewModel` documents at length —
 * a stale `User` carries `partnerId`, and writing it back resurrects a pairing the co-parent has
 * since ended.
 *
 * @property step Which step is currently showing
 * @property name The parent's own name — the one field that blocks progress
 * @property parentColor The colour this parent wants to be drawn in, or null until they touch the
 *   swatches; an untouched profile keeps whatever colour its slot has always drawn in
 * @property country The country whose public holidays the calendar draws (MON-13)
 * @property region The region within [country] whose own holidays are added, or null for the
 *   nationwide calendar; only ever one of [country]'s regions
 * @property coParent What is known about the link
 * @property fetch Where the fetch of the co-parent's records stands
 * @property custodyType The active custody pattern's type, or null while there is none
 * @property isSaving True while [OnboardingViewModel.finish]'s write is in flight
 * @property isFinished True once onboarding is recorded as complete and the host may leave
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.CoParent,
    val name: String = "",
    val parentColor: ParentColorChoice? = null,
    val country: HolidayCountry = HolidayCountry.Default,
    val region: String? = null,
    val coParent: CoParentLink = CoParentLink.Unknown,
    val fetch: CoParentFetch = CoParentFetch.Idle,
    val custodyType: CustodyModelType? = null,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false
) {
    /** The steps this run walks. */
    val steps: List<OnboardingStep> get() = OnboardingStep.steps

    /** This step's 1-based position in [steps], for the progress indicator. */
    val displayIndex: Int get() = steps.indexOf(step).coerceAtLeast(0) + 1

    /** How many steps this run has. */
    val stepCount: Int get() = steps.size

    /** True on the first step, which has nowhere to go back to. */
    val isFirstStep: Boolean get() = step == steps.firstOrNull()

    /** True on the step that ends the wizard; leaving it, by any button, finishes onboarding. */
    val isLastStep: Boolean get() = step == steps.lastOrNull()

    /** Only a blank parent name blocks progress: it is the one thing the app cannot work without. */
    val canAdvance: Boolean
        get() = step != OnboardingStep.Profile || name.isNotBlank()

    /**
     * Whether this step offers a Skip.
     *
     * The co-parent step loses its Skip once there is a co-parent: "Not now" would be declining
     * a link that already exists, and the only honest button left is the one that moves on.
     */
    val canSkip: Boolean
        get() = step.isSkippable &&
            !(step == OnboardingStep.CoParent && coParent is CoParentLink.Linked)

    /** The co-parent's name once linked, or null. May be blank — see [CoParentLink.Linked]. */
    val coParentName: String? get() = (coParent as? CoParentLink.Linked)?.name
}

/**
 * Drives the first-run wizard: which of its four steps is showing, what has been typed into it,
 * and when that reaches Room and Firestore.
 *
 * **The co-parent link comes first**, and the steps after it open on what the link brought back.
 * When the pairing listener reports a co-parent, [startFetch] asks for a sync and watches, for a
 * bounded while, for what the co-parent set up, and the first step says what came across. The
 * custody step reads the shared schedule itself (`CustodySetupViewModel`).
 *
 * **Saving happens on Next.** [skip] deliberately does not save: skipping means "I am not
 * answering this", and writing a blank answer over a value the account already had would turn a
 * skip into a deletion. The parent's fields are copied onto a **freshly read** row, for the reason
 * `ProfileViewModel.save` documents.
 *
 * @param userRepository Reads and writes the signed-in parent's own record
 * @param childInfoRepository The child records, counted to say what the co-parent set up
 * @param petRepository The pet records, counted likewise
 * @param familySettingsRepository Whether the pair has agreed an expense split
 * @param pairingRepository Whether there is a co-parent, and who
 * @param custodyModelRepository The active custody pattern; collecting it mirrors the pair's
 * @param syncRequester Asks for the sync that brings a new co-parent's records across
 * @param preferencesRepository Takes the confirmed country's currency as the default for new
 *   expenses, unless the parent already chose one
 */
@HiltViewModel
// Eight collaborators, all injected: a Hilt graph edge list, not a call signature anybody writes
// by hand, and a wrapper type would only hide which dependencies this screen actually has.
@Suppress("LongParameterList")
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val childInfoRepository: ChildInfoRepository,
    private val petRepository: PetRepository,
    private val familySettingsRepository: FamilySettingsRepository,
    private val pairingRepository: PairingRepository,
    private val custodyModelRepository: CustodyModelRepository,
    private val syncRequester: SyncRequester,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Serializes every write this wizard makes, so a read-modify-write cannot interleave with
     * another: the profile step's save and [finish]'s marker touch the same row, and losing the
     * marker means the wizard reappears on the next launch over data already entered.
     */
    private val writeLock = Mutex()

    init {
        prefill()
        observeCustody()
        observeCoParent()
    }

    /**
     * Loads whatever this account already holds into the form.
     *
     * Every field is filled **only while it is still untouched**, and a blank stored name is never
     * applied. The country opens on the phone's own when this app has its holidays and the account
     * has neither finished the wizard nor moved off the stored default (new-user review: this used
     * to happen only on the invited parent's short path; the stored default is Czechia).
     */
    private fun prefill() {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                val stored = HolidayCountry.fromCode(user?.countryCode)
                val guess = HolidayCountry.ofCodeOrNull(Locale.getDefault().country)
                    .takeIf { user?.onboardingCompletedAt == null && stored == HolidayCountry.Default }
                _uiState.update { state ->
                    val country = state.country.takeIf { it != HolidayCountry.Default } ?: guess ?: stored
                    state.copy(
                        name = state.name.ifBlank { user?.name?.takeIf { it.isNotBlank() } ?: state.name },
                        country = country,
                        // Read against whichever country won: a stored region for a country the
                        // parent has just moved away from is dropped.
                        region = state.region ?: country.regionOrNull(user?.regionCode)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                // An empty form is a survivable outcome; a crashed wizard on first launch is not.
                Log.e(TAG, "Failed to prefill the wizard from the existing account", e)
            }
        }
    }

    /**
     * Follows the active custody pattern, so the first step can say whether one exists.
     *
     * Collecting [CustodyModelRepository.getActiveModel] is also what folds the pair's shared
     * document into Room — no other screen is open during the wizard.
     */
    private fun observeCustody() {
        viewModelScope.launch {
            try {
                custodyModelRepository.getActiveModel().collect { model ->
                    _uiState.update { it.copy(custodyType = model?.modelType) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.e(TAG, "The wizard stopped following the custody pattern", e)
            }
        }
    }

    /** Follows the pairing state and starts the fetch the moment a co-parent appears. */
    private fun observeCoParent() {
        viewModelScope.launch {
            try {
                pairingRepository.observePairingState().collect { pairing ->
                    val link = when (pairing) {
                        PairingState.Loading -> CoParentLink.Unknown
                        is PairingState.NotPaired -> CoParentLink.None
                        is PairingState.Paired -> CoParentLink.Linked(pairing.partner.name.trim())
                    }
                    _uiState.update { it.copy(coParent = link) }
                    if (link is CoParentLink.Linked) startFetch()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.e(TAG, "The wizard stopped following the pairing state", e)
            }
        }
    }

    /**
     * Asks for a sync and watches, for a bounded while, for anything the co-parent created.
     *
     * Runs once per wizard: the pairing state re-emits on every invite-list change, and a fetch
     * that restarted on each of them would never report. Ending with nothing found is a real
     * answer, not a failure: the co-parent's phone widens the audience of its records on *its*
     * next sync.
     */
    private fun startFetch() {
        if (_uiState.value.fetch != CoParentFetch.Idle) return
        _uiState.update { it.copy(fetch = CoParentFetch.Running) }
        syncRequester.requestSyncNow()
        viewModelScope.launch {
            val found = try {
                withTimeoutOrNull(FETCH_TIMEOUT_MS) { awaitCoParentData() } ?: coParentData()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.e(TAG, "The fetch of the co-parent's records failed; reporting none", e)
                CoParentData()
            }
            _uiState.update { it.copy(fetch = CoParentFetch.Done(found)) }
        }
    }

    /** Polls until something from the co-parent is on this phone. Bounded by the caller. */
    private suspend fun awaitCoParentData(): CoParentData {
        var waited = 0L
        var nudged = false
        while (true) {
            val data = coParentData()
            if (!data.isEmpty) return data
            delay(FETCH_POLL_MS)
            waited += FETCH_POLL_MS
            // One more request part-way through: the first one may have run before the
            // co-parent's phone widened its audience, and a run that finds nothing new is cheap.
            if (!nudged && waited >= FETCH_NUDGE_AFTER_MS) {
                nudged = true
                syncRequester.requestSyncNow()
            }
        }
    }

    /** What is on this phone from the co-parent's side, right now. */
    private suspend fun coParentData(): CoParentData {
        val myUid = userRepository.getCurrentUserId()
        val byCoParent: (String?) -> Boolean = { createdBy -> !createdBy.isNullOrBlank() && createdBy != myUid }
        val split = try {
            familySettingsRepository.observeSettings().first()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "Could not read the pair's expense split", e)
            null
        }
        return CoParentData(
            children = childInfoRepository.getAllChildInfo().first().count { byCoParent(it.createdByFirebaseUid) },
            pets = petRepository.getAllPets().first().count { byCoParent(it.createdByFirebaseUid) },
            hasCustodySchedule = _uiState.value.custodyType != null,
            hasSplitAgreement = split != null
        )
    }

    /** Updates the parent's name. */
    fun updateName(name: String) = _uiState.update { it.copy(name = name) }

    /** Records the colour picked on the profile step. */
    fun updateParentColor(choice: ParentColorChoice) =
        _uiState.update { it.copy(parentColor = choice) }

    /** Records the country picked on the profile step. */
    fun updateCountry(country: HolidayCountry) =
        _uiState.update { it.copy(country = country, region = country.regionOrNull(it.region)) }

    /** Records the region picked on the profile step, or null for "nationwide only". */
    fun updateRegion(region: String?) =
        _uiState.update { it.copy(region = it.country.regionOrNull(region)) }

    /**
     * Saves this step's answers and moves to the next one.
     *
     * The step advances immediately and the write runs behind it, onto a fresh row; a failed write
     * is logged, not surfaced — the profile screen later writes the same fields again.
     */
    fun next() {
        val state = _uiState.value
        if (!state.canAdvance) return
        if (state.step == OnboardingStep.Profile) persist { saveProfile(state) }
        leaveStep(state.step)
    }

    /**
     * Moves on without saving: skipping means "I am not answering this", and a blank answer
     * written over a stored value would turn a skip into a deletion.
     */
    fun skip() {
        val state = _uiState.value
        if (!state.canSkip) return
        leaveStep(state.step)
    }

    /** Steps back. A no-op on the first step, which has nowhere to go. */
    fun back() {
        _uiState.update { state ->
            val steps = state.steps
            val previous = steps.getOrNull(steps.indexOf(state.step) - 1)
            previous?.let { state.copy(step = it) } ?: state
        }
    }

    /**
     * Records that onboarding is done and signals the host to leave.
     *
     * Unlike [next] this one waits: [OnboardingUiState.isFinished] is what navigates away, and
     * navigating before the marker reaches Room would let the launch check re-read a null marker
     * and hand the parent the same wizard again. The marker is stamped even when the write to
     * Firestore fails, and the host is released even when the local write throws, because trapping
     * someone in a wizard they have finished is worse than asking again on the next launch.
     */
    fun finish() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                writeLock.withLock {
                    val fresh = userRepository.getCurrentUser()
                    if (fresh != null) {
                        userRepository.updateUser(
                            fresh.copy(onboardingCompletedAt = LocalDateTime.now().toString())
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.e(TAG, "Failed to record that onboarding finished", e)
            }
            _uiState.update { it.copy(isSaving = false, isFinished = true) }
        }
    }

    /**
     * Moves on from [step] — to the next one, or out of the wizard when there is no next one.
     * Both Next and Skip come through here, because on the last step they mean the same thing.
     */
    private fun leaveStep(step: OnboardingStep) {
        if (_uiState.value.isLastStep) {
            finish()
            return
        }
        _uiState.update { state ->
            val steps = state.steps
            steps.getOrNull(steps.indexOf(step) + 1)?.let { state.copy(step = it) } ?: state
        }
    }

    /** Runs [block] off the UI, logging rather than surfacing a failure. See [next]. */
    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                writeLock.withLock { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.e(TAG, "Failed to save a wizard step", e)
            }
        }
    }

    /**
     * Copies the fields this wizard owns onto the parent's current row: the name, a colour only
     * when one was chosen, the country and region. Nothing else — `partnerId`, `role`, the date of
     * birth and the phone come from the fresh read.
     */
    private suspend fun saveProfile(state: OnboardingUiState) {
        val fresh = userRepository.getCurrentUser() ?: return
        userRepository.updateUser(
            fresh.copy(
                name = state.name.trim(),
                colorCode = state.parentColor?.storedCode ?: fresh.colorCode,
                countryCode = state.country.code,
                regionCode = state.country.regionOrNull(state.region)
            )
        )
        // The first default currency came from the device region; the country confirmed here is
        // the better guess. A currency picked in Settings is kept.
        currencyOfRegion(state.country.code)?.let { preferencesRepository.suggestDefaultCurrency(it) }
    }

    private companion object {
        const val TAG = "OnboardingViewModel"

        /** How long the first step watches for the co-parent's records before reporting. */
        const val FETCH_TIMEOUT_MS = 30_000L

        /** How often the fetch re-reads what has landed. */
        const val FETCH_POLL_MS = 2_500L

        /** When the fetch asks for its one further sync. */
        const val FETCH_NUDGE_AFTER_MS = 10_000L
    }
}
