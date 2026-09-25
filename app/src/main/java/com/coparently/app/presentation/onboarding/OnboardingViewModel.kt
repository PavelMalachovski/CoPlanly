package com.coparently.app.presentation.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.WHOLE_PERCENT
import com.coparently.app.domain.holidays.HolidayCountry
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.EmergencyContact
import com.coparently.app.domain.model.FamilyKind
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * What the family step opens pre-answered with.
 *
 * Children rather than nothing, so the step always has something to move on with and the very
 * first screen of the app is not a gate. A parent who has pets instead simply changes it.
 *
 * One definition, shared with the Settings dialog's seed: both are "what an unanswered parent is
 * offered", and the two drifting apart is how one of them ends up offering the co-parent's answer.
 */
private val DEFAULT_CARES_FOR = FamilyKind.DEFAULT

/** Half each, which is what a family splits by until they agree otherwise. */
private const val EVEN_SPLIT_PERCENT = 50

/** The second parent slot. Slot 1's share is what the split is stored as; slot 2's is the rest. */
private const val SLOT_TWO = "dad"

/**
 * One child the wizard is setting up.
 *
 * A **list** of these rather than the flat `childName`/`childDateOfBirth`/… fields this state
 * used to hold, because a family with two children could not say so: the wizard wrote one record
 * and the child list was the only place a second could be added, after the questionnaire had
 * already asked how the family works. It also silently mis-filed the emergency contacts — see
 * [relatives].
 *
 * **How many children a family has is never stored as an answer.** The wizard asks for names and
 * the count falls out of them. A stored "one or several" would be a fact that goes stale the
 * moment a second child arrives, and would then need a settings toggle to correct; a derived
 * count cannot disagree with the records. It is the same reasoning `FamilyKind` documents for
 * treating an unanswered account as "show everything".
 *
 * @property id The id the [ChildInfo] record will have, generated when the draft is created.
 *   Fixed up front rather than at save time so nothing has to be written back into a form the
 *   parent may be typing into, and so the child step and the relatives step address one record.
 * @property name The child's name; blank means this draft is never written
 * @property dateOfBirth The child's date of birth, or null while unanswered
 * @property allergies The child's allergies
 * @property medicalProfile The child's emergency medical profile
 * @property relatives Emergency contacts for **this** child. They live on the draft rather than
 *   beside it because that is where they live in the data model — `ChildInfo.emergencyContacts`
 *   — and a single flat list landed every contact on whichever child happened to be first.
 * @property byCoParent True when the record this draft was filled from was created by the
 *   co-parent. The step says so, because a form that opens full of somebody else's answers
 *   without saying whose reads as a glitch rather than as help.
 */
data class ChildDraft(
    val id: String,
    val name: String = "",
    val dateOfBirth: LocalDate? = null,
    val allergies: List<String> = emptyList(),
    val medicalProfile: MedicalProfile = MedicalProfile(),
    val relatives: List<EmergencyContact> = emptyList(),
    val byCoParent: Boolean = false
) {
    /** True while nothing has been entered, which is what makes a draft safe to replace. */
    val isBlank: Boolean
        get() = name.isBlank() && dateOfBirth == null && allergies.isEmpty() &&
            medicalProfile == MedicalProfile() && relatives.isEmpty()
}

/**
 * One pet the wizard is setting up, on the same terms as [ChildDraft].
 *
 * The pets screen has always been a genuine list — this is the wizard catching up with it.
 *
 * @property id The id the [Pet] record will have, generated when the draft is created
 * @property name The pet's name; blank means this draft is never written
 * @property species What kind of animal this is
 * @property byCoParent True when the record was created by the co-parent. See [ChildDraft].
 */
data class PetDraft(
    val id: String,
    val name: String = "",
    val species: PetSpecies = PetSpecies.DOG,
    val byCoParent: Boolean = false
) {
    /** True while nothing has been entered. See [ChildDraft.isBlank]. */
    val isBlank: Boolean get() = name.isBlank() && species == PetSpecies.DOG
}

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
 * steps that show them through the same flows every other screen reads. This is only what the
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
 * The parent's fields and the child's are held flat rather than as a `User` and a `ChildInfo`
 * because the wizard owns neither record wholesale: it edits a few of the parent's fields and
 * four of the child's, and each is written onto a **freshly read** row at save time. Holding
 * the whole objects would invite the mistake `ProfileViewModel` documents at length — a stale
 * `User` carries `partnerId`, and writing it back resurrects a pairing the co-parent has since
 * ended.
 *
 * @property step Which step is currently showing
 * @property name The parent's own name — the one field that blocks progress
 * @property dateOfBirth The parent's own date of birth, or null while unanswered
 * @property phone The parent's own phone, free text as typed
 * @property children The children being set up, one [ChildDraft] each. Never empty: the step
 *   always has one form to render, and a draft nobody names is never written.
 * @property pets The pets, on the same terms as [children]
 * @property relativesForId Which child the relatives step is collecting contacts for, or null
 *   to let [relativesChild] fall back to the first named one
 * @property chosenCaresFor The family answer given on this run, or null while untouched
 * @property storedCaresFor The answer this account already holds; empty when never answered
 * @property coParentCaresFor The co-parent's answer, once linked; empty when unknown
 * @property splitMyPercent **This parent's** share of a shared expense, as a whole percent. The
 *   slider shows this parent's share because that is the number a person has an opinion about;
 *   the stored form is slot 1's, and the two coincide only while this device holds slot 1 —
 *   which pairing can change. The conversion happens on the save path, from a fresh read.
 * @property splitTouched Whether the slider was moved on this run. Only a moved slider is
 *   written: an untouched step on a linked account must not create the pair's agreement out
 *   of a default, ahead of the ratio the co-parent chose before pairing.
 * @property agreedSplitMyPercent The pair's agreed ratio as this parent's share, or null while
 *   the pair has none (or there is no pair)
 * @property coParent What is known about the link
 * @property fetch Where the fetch of the co-parent's records stands
 * @property custodyType The active custody pattern's type, or null while there is none
 * @property isSaving True while [OnboardingViewModel.finish]'s write is in flight
 * @property isFinished True once onboarding is recorded as complete and the host may leave
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.CoParent,
    val name: String = "",
    val dateOfBirth: LocalDate? = null,
    val phone: String = "",
    /**
     * The colour this parent wants to be drawn in, or null until they touch the swatches.
     *
     * Null rather than a pre-selected default so the wizard does not claim an answer nobody
     * gave: an untouched profile keeps whatever colour its slot has always drawn in, and
     * [OnboardingViewModel.saveProfile] leaves the stored value alone.
     */
    val parentColor: ParentColorChoice? = null,
    /**
     * The country whose public holidays the calendar draws (MON-13).
     *
     * Not nullable, unlike [parentColor]: there is no "unanswered" state to preserve here,
     * because the profile column is `NOT NULL DEFAULT 'CZ'` and every account already has an
     * answer. The step opens on whatever the profile holds, which for anybody upgrading is
     * Czechia — exactly what they were being shown before they could choose.
     */
    val country: HolidayCountry = HolidayCountry.Default,
    /**
     * The region within [country] whose own public holidays are added (MON-13, regional half),
     * or null for the nationwide calendar. Only ever one of [country]'s regions: changing the
     * country clears it.
     */
    val region: String? = null,
    val children: List<ChildDraft> = emptyList(),
    val pets: List<PetDraft> = emptyList(),
    val relativesForId: String? = null,
    val chosenCaresFor: Set<FamilyKind>? = null,
    val storedCaresFor: Set<FamilyKind> = emptySet(),
    val coParentCaresFor: Set<FamilyKind> = emptySet(),
    val splitMyPercent: Int = EVEN_SPLIT_PERCENT,
    val splitTouched: Boolean = false,
    val agreedSplitMyPercent: Int? = null,
    val coParent: CoParentLink = CoParentLink.Unknown,
    val fetch: CoParentFetch = CoParentFetch.Idle,
    val custodyType: CustodyModelType? = null,
    /**
     * The step the parent last typed into, cleared whenever a step is left forwards.
     *
     * A flag of intent rather than a comparison of the drafts: those also fill in from Room — the
     * co-parent's children arriving after the link — and a Skip over records that are already
     * stored loses nothing, so a comparison would ask "discard your changes?" about changes
     * nobody made.
     */
    val editedStep: OnboardingStep? = null,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false
) {
    /**
     * The family answer in force: this run's, else the stored one, else the co-parent's, else
     * the default.
     *
     * The co-parent's answer stands in for an unanswered parent here and nowhere else. The
     * Settings dialog deliberately refuses to seed from it (see `FamilyKindSource.observeMine`),
     * because a dialog whose Save writes this parent's row must not put the other parent's words
     * in their mouth. The wizard is different in kind: a second parent who linked first did so
     * precisely to inherit what the first one set up, the step shows the answer as ticked chips
     * they can change, and Next is what turns it into their own. An untouched default of
     * "children" offered to a family that has already said "pets" would be the wrong guess made
     * with the right answer in hand.
     */
    val caresFor: Set<FamilyKind>
        get() = chosenCaresFor
            ?: storedCaresFor.ifEmpty { coParentCaresFor }.ifEmpty { DEFAULT_CARES_FOR }

    /**
     * The steps this run will walk, given the family answer.
     *
     * Derived rather than stored: the answer can change on the [OnboardingStep.Family] step
     * itself, and a list captured at construction would keep asking about a child the parent has
     * just said they do not have.
     */
    val steps: List<OnboardingStep> get() = OnboardingStep.stepsFor(caresFor)

    /** This step's 1-based position in [steps], for the progress indicator. */
    val displayIndex: Int get() = steps.indexOf(step).coerceAtLeast(0) + 1

    /** How many steps this run has. Never `OnboardingStep.entries.size` — most runs are shorter. */
    val stepCount: Int get() = steps.size

    /** True on the first step, which has nowhere to go back to. */
    val isFirstStep: Boolean get() = step == steps.firstOrNull()

    /** True on the step that ends the wizard; leaving it, by any button, finishes onboarding. */
    val isLastStep: Boolean get() = step == steps.lastOrNull()

    /**
     * Only a blank parent name blocks progress.
     *
     * The questionnaire asks for a great deal — a blood group, hereditary conditions, a phone
     * number for an aunt — and it is collected for the parent's own benefit in an emergency.
     * Data gathered for someone's benefit must not lock them out of their calendar, so the one
     * thing that blocks is the one thing the app cannot work without.
     */
    val canAdvance: Boolean
        get() = when (step) {
            OnboardingStep.Profile -> name.isNotBlank()
            // At least one kind, or the wizard cannot decide what to ask next.
            OnboardingStep.Family -> caresFor.isNotEmpty()
            else -> true
        }

    /**
     * Whether this step offers a Skip.
     *
     * The co-parent step loses its Skip once there is a co-parent: "Not now" would be declining
     * a link that already exists, and the only honest button left is the one that moves on.
     */
    val canSkip: Boolean
        get() = step.isSkippable &&
            !(step == OnboardingStep.CoParent && coParent is CoParentLink.Linked)

    /**
     * True when Skip would leave something this parent typed on this step unsaved.
     *
     * Skip writes nothing (see [OnboardingViewModel.skip]), which is right for an unanswered
     * question and wrong for an answered one the parent then pressed the wrong button on, so the
     * screen asks before it lets typing go.
     */
    val skipDiscardsEdits: Boolean get() = canSkip && editedStep == step

    /**
     * Whether the relatives step can accept contacts yet.
     *
     * An [EmergencyContact] belongs to a child — that is the record both parents may write, and
     * the only place the rest of the app reads contacts from. With no named child there is
     * nowhere honest to put one, so the step says so rather than collecting contacts it would
     * then drop on the floor.
     */
    val canEditRelatives: Boolean get() = namedChildren.isNotEmpty()

    /** The children that have been named, which are the only ones anything is written for. */
    val namedChildren: List<ChildDraft> get() = children.filter { it.name.isNotBlank() }

    /**
     * The child the relatives step is collecting contacts for.
     *
     * Resolved rather than read straight off [relativesForId] so the selection heals itself: a
     * child whose name is cleared, or who is removed from the wizard, must not leave the step
     * pointing at a draft that no longer takes contacts.
     */
    val relativesChild: ChildDraft?
        get() = namedChildren.firstOrNull { it.id == relativesForId } ?: namedChildren.firstOrNull()

    /** The co-parent's name once linked, or null. May be blank — see [CoParentLink.Linked]. */
    val coParentName: String? get() = (coParent as? CoParentLink.Linked)?.name

    /** True when at least one child on screen came from the co-parent's records. */
    val childrenFromCoParent: Boolean get() = children.any { it.byCoParent }

    /** True when at least one pet on screen came from the co-parent's records. */
    val petsFromCoParent: Boolean get() = pets.any { it.byCoParent }

    /** True when the pair already has an agreed split, which the slider opened on. */
    val splitAgreed: Boolean get() = agreedSplitMyPercent != null
}

/**
 * Drives the first-run questionnaire: which step is showing, what has been typed into it, and
 * when that reaches Room and Firestore.
 *
 * **The co-parent link comes first**, and the rest of the wizard is written to open on whatever
 * the link brought back. When the pairing listener reports a co-parent, [startFetch] asks for a
 * sync and watches Room for a bounded while; the child and pet steps fill their untouched drafts
 * from the records that land, the split step opens on the pair's agreement, and the custody step
 * reports the shared schedule. None of that is a separate code path: the steps read the same Room
 * flows whether the records are the parent's own or the co-parent's, and only *say* whose they
 * are.
 *
 * **Saving happens per step, on Next.** A parent who is interrupted after step 2 and force-stops
 * the app finds their answers already stored, and [prefill] puts them back into the form on the
 * next launch — the wizard resumes rather than restarting empty. [skip] deliberately does not
 * save: skipping means "I am not answering this", and writing a blank answer over a value the
 * account already had would turn a skip into a deletion.
 *
 * **Each write goes onto a freshly read row.** The parent's own fields are copied onto whatever
 * `users/{uid}` holds right now, and the child's four onto whatever `child_info` holds right now,
 * for the reason `ProfileViewModel.save` documents: a held snapshot carries `partnerId`,
 * `createdByFirebaseUid` and sync flags that belong to whoever last wrote them, not to this form.
 * A record nothing on the step changed is **not** written again: with the co-parent's children on
 * screen, an unconditional re-write on Next would re-upload their records under this parent's
 * name and announce an edit that never happened.
 *
 * **The children and pets are observed, with one guard.** Every emission of
 * [ChildInfoRepository.getAllChildInfo] is offered to the drafts, and the drafts take it **only
 * while every one of them is blank**. That guard is what separates this from the
 * `ChildInfoViewModel` defect CLAUDE.md records under "Known issues", where a background sync tick
 * re-emitted the list and overwrote a form mid-edit: here a list with anything typed into it is
 * never touched. Observing rather than reading once is what pairing-first needs — the records a
 * second parent came for arrive *after* the wizard is constructed.
 *
 * **Children and pets are lists, and how many there are is never asked.** The steps collect
 * names and the count falls out of them — see [ChildDraft] for why a stored "one or several"
 * would be the wrong shape of answer.
 *
 * @param userRepository Reads and writes the signed-in parent's own record
 * @param childInfoRepository Reads and writes the child records the wizard fills in
 * @param petRepository Reads and writes the pet records
 * @param familySettingsRepository The expense split, agreed or cached
 * @param pairingRepository Whether there is a co-parent, and who
 * @param custodyModelRepository The active custody pattern; collecting it mirrors the pair's
 * @param syncRequester Asks for the sync that brings a new co-parent's records across
 * @param preferencesRepository Takes the confirmed country's currency as the default for new
 *   expenses, unless the parent already chose one
 */
@HiltViewModel
// Eight collaborators, all injected: a Hilt graph edge list, not a call signature anybody writes
// by hand, and a wrapper type would only hide which dependencies this screen actually has.
@Suppress("LongParameterList", "TooManyFunctions")
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

    private val _uiState = MutableStateFlow(
        // One blank draft of each kind, so both steps open with a form rather than an empty
        // list and an Add button. Neither is written unless it is named.
        OnboardingUiState(
            children = listOf(ChildDraft(id = newDraftId())),
            pets = listOf(PetDraft(id = newDraftId()))
        )
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Serializes every write this wizard makes, so a read-modify-write cannot interleave with
     * another.
     *
     * All of them follow the same shape — read the current row, copy this form's fields onto it,
     * write it back — and two of them touch the same row: the profile step's save and [finish]'s
     * marker. Without this, a parent moving quickly from the profile step to the end could have
     * both read the pre-marker row, and whichever wrote second would silently drop the other's
     * field. The marker is the one that matters: losing it means the wizard reappears on the
     * next launch over data the parent has already entered.
     */
    private val writeLock = Mutex()

    init {
        prefill()
        observeRecords()
        observeCustody()
        observeCoParent()
    }

    /**
     * Loads whatever this account already holds into the form.
     *
     * A Google sign-in arrives with a name; an interrupted first run arrives with everything it
     * got as far as saving. Asking either to retype it would be the wizard's worst moment.
     *
     * Every field is filled **only while it is still untouched**, and a blank stored value is
     * never applied. Both halves of that matter: this runs asynchronously against a Room read,
     * so a parent who starts typing before it lands must not have their answer replaced by the
     * row it finds, and an account whose stored name is the empty string — every email/password
     * sign-up before the profile screen is opened — must not have that emptiness written over
     * what they just typed.
     */
    private fun prefill() {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                // The cached ratio is slot 1's share; the slider shows this parent's.
                val cachedMyPercent = familySettingsRepository.agreedRatioOrDefault()
                    .myPercent(slotOne = user?.role != SLOT_TWO)
                _uiState.update { state ->
                    // The stored value unless the parent has already touched the chips on this
                    // run, the same rule `caresFor` follows below.
                    val country = state.country.takeIf { it != HolidayCountry.Default }
                        ?: HolidayCountry.fromCode(user?.countryCode)
                    state.copy(
                        name = state.name.orStored(user?.name),
                        dateOfBirth = state.dateOfBirth ?: user?.dateOfBirth,
                        country = country,
                        // Same rule, read against whichever country won: a stored region for a
                        // country the parent has just moved away from on this run is dropped.
                        region = state.region ?: country.regionOrNull(user?.regionCode),
                        phone = state.phone.orStored(user?.phone),
                        storedCaresFor = user?.caresFor.orEmpty(),
                        splitMyPercent = if (state.splitTouched) state.splitMyPercent else cachedMyPercent
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
     * Keeps the child and pet drafts abreast of Room, for as long as every draft is blank.
     *
     * See the class doc for why this observes rather than reads once, and for the guard that
     * makes observing safe. The signed-in uid is read once, up front, to tell this parent's
     * records from the co-parent's — a record with no creator recorded counts as this parent's.
     */
    private fun observeRecords() {
        viewModelScope.launch {
            try {
                val uid = userRepository.getCurrentUserId()
                childInfoRepository.getAllChildInfo().collect { stored ->
                    _uiState.update { state ->
                        state.copy(children = state.children.orStoredChildren(stored, uid))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.e(TAG, "The wizard stopped following the child records", e)
            }
        }
        viewModelScope.launch {
            try {
                val uid = userRepository.getCurrentUserId()
                petRepository.getAllPets().collect { stored ->
                    _uiState.update { state ->
                        state.copy(pets = state.pets.orStoredPets(stored, uid))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.e(TAG, "The wizard stopped following the pet records", e)
            }
        }
    }

    /**
     * Follows the active custody pattern, so the custody step can say whether one exists.
     *
     * Collecting [CustodyModelRepository.getActiveModel] is also what folds the pair's shared
     * document into Room — no other screen is open during the wizard, so without this collector
     * a second parent would reach the custody step with the co-parent's schedule sitting
     * unread in Firestore.
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

    /**
     * Follows the pairing state and starts the fetch the moment a co-parent appears.
     *
     * The co-parent's family answer is seeded from here as well — see
     * [OnboardingUiState.caresFor] for the one place in the app where that is the right seed.
     */
    private fun observeCoParent() {
        viewModelScope.launch {
            try {
                pairingRepository.observePairingState().collect { pairing ->
                    val link = when (pairing) {
                        PairingState.Loading -> CoParentLink.Unknown
                        is PairingState.NotPaired -> CoParentLink.None
                        is PairingState.Paired -> CoParentLink.Linked(pairing.partner.name.trim())
                    }
                    _uiState.update { state ->
                        state.copy(
                            coParent = link,
                            coParentCaresFor = (pairing as? PairingState.Paired)
                                ?.partner?.caresFor.orEmpty()
                        )
                    }
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
     * that restarted on each of them would never report. The wait is a poll over Room rather than
     * a subscription because three of the four answers are already kept current by the
     * collectors above; the fourth — the pair's split — is a Firestore document read through
     * [refreshSplitFromPair], which the poll repeats until it appears or the wait ends.
     *
     * Ending with nothing found is a real answer, not a failure: the co-parent's phone widens the
     * audience of its records on *its* next sync, and until then there is nothing this phone may
     * read. The step says so, and the collectors above keep listening after the fetch has
     * reported, so a record that lands later still fills an untouched step.
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
                Log.e(TAG, "The fetch of the co-parent's records failed; reporting what is local", e)
                coParentData()
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
        refreshSplitFromPair()
        val state = _uiState.value
        return CoParentData(
            children = state.children.count { it.byCoParent },
            pets = state.pets.count { it.byCoParent },
            hasCustodySchedule = state.custodyType != null,
            hasSplitAgreement = state.splitAgreed
        )
    }

    /**
     * Reads the pair's agreed split, if there is one, and opens the slider on it.
     *
     * Only an untouched slider is moved: a parent who has already chosen must not have their
     * choice replaced by a document that happened to land. The agreement is remembered either
     * way, so the step can say it exists and Next can tell "moved back to the agreed value" from
     * "moved".
     */
    private suspend fun refreshSplitFromPair() {
        if (_uiState.value.coParent !is CoParentLink.Linked) return
        val settings = try {
            familySettingsRepository.observeSettings().first()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "Could not read the pair's expense split", e)
            null
        } ?: return
        val agreed = settings.ratio.myPercent(slotOne = mySlotIsOne())
        _uiState.update { state ->
            state.copy(
                agreedSplitMyPercent = agreed,
                splitMyPercent = if (state.splitTouched) state.splitMyPercent else agreed
            )
        }
    }

    /** Updates the parent's name. */
    fun updateName(name: String) = _uiState.update { it.copy(name = name) }

    /** Updates the parent's date of birth. */
    fun updateDateOfBirth(date: LocalDate?) = _uiState.update { it.copy(dateOfBirth = date) }

    /** Updates the parent's phone number, free text as typed. */
    fun updatePhone(phone: String) = _uiState.update { it.copy(phone = phone) }

    /** Records the colour picked on the profile step. */
    fun updateParentColor(choice: ParentColorChoice) =
        _uiState.update { it.copy(parentColor = choice) }

    /** Records the country picked on the profile step. */
    fun updateCountry(country: HolidayCountry) =
        _uiState.update { it.copy(country = country, region = country.regionOrNull(it.region)) }

    /** Records the region picked on the profile step, or null for "nationwide only". */
    fun updateRegion(region: String?) =
        _uiState.update { it.copy(region = it.country.regionOrNull(region)) }

    /** Updates one child's name. */
    fun updateChildName(id: String, name: String) = updateChild(id) { it.copy(name = name) }

    /** Updates one child's date of birth. */
    fun updateChildDateOfBirth(id: String, date: LocalDate?) =
        updateChild(id) { it.copy(dateOfBirth = date) }

    /** Updates one child's allergies. */
    fun updateChildAllergies(id: String, allergies: List<String>) =
        updateChild(id) { it.copy(allergies = allergies) }

    /** Updates one child's medical profile. */
    fun updateChildMedicalProfile(id: String, profile: MedicalProfile) =
        updateChild(id) { it.copy(medicalProfile = profile) }

    /** Replaces one child's emergency contacts. */
    fun updateRelatives(id: String, relatives: List<EmergencyContact>) =
        updateChild(id) { it.copy(relatives = relatives) }

    /** Appends a blank child draft. Nothing is written for it until it is named. */
    fun addChild() = _uiState.update { it.copy(children = it.children + ChildDraft(id = newDraftId())) }

    /**
     * Takes a child out of the wizard, and deletes its record if one was already written.
     *
     * Dropping the draft alone would leave a child the parent has explicitly removed sitting in
     * the child list — the step saves on Next, so a draft removed after one is a record. The
     * delete is a no-op for a draft that never reached Room.
     *
     * It goes through [ChildInfoRepository.deleteChildInfo], which writes a tombstone the
     * co-parent's phone collects (CQ-19) — the same call the child editor's own Delete makes.
     */
    fun removeChild(id: String) {
        val removed = _uiState.value.children.firstOrNull { it.id == id } ?: return
        _uiState.update { state ->
            val remaining = state.children.filterNot { it.id == id }
            // Never leave the step with nothing to render.
            state.copy(children = remaining.ifEmpty { listOf(ChildDraft(id = newDraftId())) })
        }
        if (!removed.isBlank) {
            persist { childInfoRepository.getChildInfoById(id)?.let { childInfoRepository.deleteChildInfo(it) } }
        }
    }

    /** Which child the relatives step is collecting contacts for. */
    fun selectRelativesChild(id: String) = _uiState.update { it.copy(relativesForId = id) }

    /**
     * Saves this step's answers and moves to the next one.
     *
     * The step advances immediately and the write runs behind it: the write targets a fresh row
     * and a snapshot taken here, so nothing the parent types on the next step can reach it, and
     * making them watch a spinner between two questions would be the wrong trade. A failed write
     * is logged, not surfaced — the parent has no action to take about it, and the next step's
     * save (or the profile screen later) writes the same fields again.
     */
    fun next() {
        val state = _uiState.value
        if (!state.canAdvance) return

        when (state.step) {
            OnboardingStep.Family -> persist { saveCaresFor(state) }
            OnboardingStep.Profile -> persist { saveProfile(state) }
            OnboardingStep.Child -> persist { saveChildren(state) }
            OnboardingStep.Relatives -> persist { saveChildren(state) }
            OnboardingStep.Pet -> persist { savePets(state) }
            OnboardingStep.Split -> persist { saveSplit(state) }
            else -> Unit
        }
        leaveStep(state.step)
    }

    /**
     * Moves on without saving.
     *
     * Skipping means "I am not answering this", so the step's fields are deliberately not
     * written: a blank answer written over a value the account already had would turn a skip
     * into a deletion.
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
     * Records whether this family is co-parenting children, pets or both.
     *
     * Written on Next like every other step rather than on tap, so backing out of the wizard
     * leaves nothing behind — and so the answer reaches the co-parent's device through the same
     * profile write as the rest.
     */
    fun setCaresFor(kinds: Set<FamilyKind>) {
        _uiState.update { it.copy(chosenCaresFor = kinds) }
    }

    /** One pet's name. */
    fun setPetName(id: String, value: String) = updatePet(id) { it.copy(name = value) }

    /** One pet's species. */
    fun setPetSpecies(id: String, value: PetSpecies) = updatePet(id) { it.copy(species = value) }

    /** Appends a blank pet draft. Nothing is written for it until it is named. */
    fun addPet() = _uiState.update { it.copy(pets = it.pets + PetDraft(id = newDraftId())) }

    /** Takes a pet out of the wizard, deleting its record if one was written. See [removeChild]. */
    fun removePet(id: String) {
        val removed = _uiState.value.pets.firstOrNull { it.id == id } ?: return
        _uiState.update { state ->
            val remaining = state.pets.filterNot { it.id == id }
            state.copy(pets = remaining.ifEmpty { listOf(PetDraft(id = newDraftId())) })
        }
        if (!removed.isBlank) {
            persist { petRepository.getPetById(id)?.let { petRepository.deletePet(it) } }
        }
    }

    /** Applies [transform] to the one child with [id], leaving the rest of the list alone. */
    private fun updateChild(id: String, transform: (ChildDraft) -> ChildDraft) =
        _uiState.update { state ->
            state.copy(
                children = state.children.map { if (it.id == id) transform(it) else it },
                editedStep = state.step
            )
        }

    /** Applies [transform] to the one pet with [id]. See [updateChild]. */
    private fun updatePet(id: String, transform: (PetDraft) -> PetDraft) =
        _uiState.update { state ->
            state.copy(pets = state.pets.map { if (it.id == id) transform(it) else it }, editedStep = state.step)
        }

    /** The split step's slider: this parent's share, as a whole percent. */
    fun setSplitMyPercent(value: Int) {
        _uiState.update {
            it.copy(
                splitMyPercent = value.coerceIn(0, WHOLE_PERCENT),
                splitTouched = true,
                editedStep = it.step
            )
        }
    }

    /**
     * Records that onboarding is done and signals the host to leave.
     *
     * Unlike [next] this one waits: [OnboardingUiState.isFinished] is what navigates away, and
     * navigating before the marker reaches Room would let the launch check re-read a null marker
     * and hand the parent the same questionnaire again. The marker is stamped even when the
     * write to Firestore fails — [UserRepository.updateUser] already swallows that half — and
     * even when the local write throws, because trapping someone in a wizard they have finished
     * is worse than asking again on the next launch.
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
     *
     * Both Next and Skip come through here, because on the last step they mean the same thing.
     * Skipping the co-parent link is a supported outcome, not a dead end: it leaves the parent
     * unpaired on Home, where the app's own "connect your co-parent" prompt lives.
     *
     * Arriving on the split step re-reads the pair's agreement, so a second parent who got there
     * after the fetch gave up still opens on the ratio the first one set.
     */
    private fun leaveStep(step: OnboardingStep) {
        if (_uiState.value.isLastStep) {
            finish()
            return
        }
        _uiState.update { state ->
            val steps = state.steps
            val following = steps.getOrNull(steps.indexOf(step) + 1)
            following?.let { state.copy(step = it, editedStep = null) } ?: state
        }
        if (_uiState.value.step == OnboardingStep.Split) {
            viewModelScope.launch { refreshSplitFromPair() }
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
     * Writes what the family co-parents onto the parent's own record.
     *
     * Its own write rather than folding it into [saveProfile], because the family step comes
     * *before* the profile step: the answer decides which steps follow, and waiting for the
     * profile save would leave it unstored for anyone who backs out in between.
     */
    private suspend fun saveCaresFor(state: OnboardingUiState) {
        val fresh = userRepository.getCurrentUser() ?: return
        userRepository.updateUser(fresh.copy(caresFor = state.caresFor))
    }

    /**
     * Submits the split, if the slider was moved to a value the pair has not already agreed.
     *
     * An untouched slider writes nothing. Before the link came first that was harmless either
     * way; now a second parent reaches this step paired, and an unconditional write of the
     * default would create the pair's agreement out of "half each" — ahead of, and instead of,
     * the ratio the first parent chose in their own wizard, which their phone publishes on its
     * next sync only if no agreement exists yet.
     *
     * The slider holds this parent's share and the repository speaks slot 1's, so the number is
     * converted through a fresh read of the slot — pairing may have moved this device to slot 2
     * since the step opened (CLAUDE.md item 17: a save path reads, never a stream's `.value`).
     */
    private suspend fun saveSplit(state: OnboardingUiState) {
        if (!state.splitTouched || state.splitMyPercent == state.agreedSplitMyPercent) return
        val slotOne = mySlotIsOne()
        val momPercent = if (slotOne) state.splitMyPercent else WHOLE_PERCENT - state.splitMyPercent
        familySettingsRepository.submitRatio(SplitRatio.ofMomPercent(momPercent))
    }

    /** Whether this device holds slot 1, from a fresh read. An unknown slot reads as slot 1. */
    private suspend fun mySlotIsOne(): Boolean = userRepository.getCurrentUser()?.role != SLOT_TWO

    /** Writes every named pet. See [saveChildren]. */
    private suspend fun savePets(state: OnboardingUiState) {
        state.pets.forEach { savePetDraft(it) }
    }

    /**
     * Writes one pet's record.
     *
     * Same shape as [saveChildDraft]: the draft's id **is** the record's id, so a second Next
     * updates rather than duplicating. Nothing is written for a blank name — a nameless pet is
     * not creatable anywhere else in the app, and would show as an unidentifiable row — and
     * nothing is written for a record the step left as it found it.
     */
    private suspend fun savePetDraft(draft: PetDraft) {
        val name = draft.name.trim()
        if (name.isBlank()) return

        val uid = userRepository.getCurrentUserId()
        val now = LocalDateTime.now()
        val existing = petRepository.getPetById(draft.id)
        if (existing != null && existing.name == name && existing.species == draft.species) return

        // `copy()` onto whatever is stored, never a fresh object: the same field-preserving rule
        // the event and child editors follow, so ownership and sync stamps survive.
        val pet = (existing ?: Pet(id = draft.id, name = name, createdAt = now, updatedAt = now)).copy(
            name = name,
            species = draft.species,
            createdByFirebaseUid = existing?.createdByFirebaseUid ?: uid,
            lastModifiedBy = uid,
            syncedToFirestore = false,
            updatedAt = now
        )
        petRepository.upsertPet(pet)
    }

    /**
     * Copies the fields this wizard owns onto the parent's current row.
     *
     * `partnerId`, `fcmToken`, `role` and `colorCode` come from the fresh read, never from
     * [state] — see this class's doc, and `ProfileViewModel.save`, which learned it the hard way.
     */
    private suspend fun saveProfile(state: OnboardingUiState) {
        val fresh = userRepository.getCurrentUser() ?: return
        userRepository.updateUser(
            fresh.copy(
                name = state.name.trim(),
                dateOfBirth = state.dateOfBirth,
                phone = state.phone.ifBlank { null },
                // Only when they actually chose. An untouched swatch strip must not overwrite a
                // colour the parent set in Settings on a previous run through this wizard.
                colorCode = state.parentColor?.storedCode ?: fresh.colorCode,
                countryCode = state.country.code,
                regionCode = state.country.regionOrNull(state.region)
            )
        )
        // The first default currency came from the device region, which is how a Czech parent
        // with a phone in English (United States) ended up in dollars. The country confirmed
        // here is the better guess; a currency picked in Settings is kept.
        currencyOfRegion(state.country.code)?.let { preferencesRepository.suggestDefaultCurrency(it) }
    }

    /**
     * Writes every named child, each onto its own record.
     *
     * Both the child step and the relatives step call this, so the two never create separate
     * rows: the contacts a parent enters on the relatives step belong to a [ChildDraft] and are
     * written by the same pass that writes that child's name.
     */
    private suspend fun saveChildren(state: OnboardingUiState) {
        state.children.forEach { saveChildDraft(it) }
    }

    /**
     * Writes one child's record.
     *
     * Nothing is written for a blank name: a nameless child is not creatable anywhere else in
     * the app — `AddEditChildInfoScreen` refuses to save one — and it would appear in the child
     * list as an empty row nobody could identify. Nothing is written for a record the step left
     * exactly as it found it either: re-uploading the co-parent's child under this parent's name
     * would announce an edit that never happened.
     */
    private suspend fun saveChildDraft(draft: ChildDraft) {
        val name = draft.name.trim()
        if (name.isBlank()) return

        val uid = userRepository.getCurrentUserId()
        val now = LocalDateTime.now()
        val existing = childInfoRepository.getChildInfoById(draft.id)
        val dateOfBirth = draft.dateOfBirth?.atStartOfDay()
        if (existing != null && existing.isUnchangedBy(draft, name, dateOfBirth)) return

        val base = existing ?: ChildInfo(
            id = draft.id,
            childName = name,
            dateOfBirth = dateOfBirth,
            createdAt = now,
            updatedAt = now
        )
        childInfoRepository.upsertChildInfo(
            base.copy(
                childName = name,
                dateOfBirth = dateOfBirth,
                allergies = draft.allergies,
                medicalProfile = draft.medicalProfile,
                emergencyContacts = draft.relatives,
                updatedAt = now,
                createdByFirebaseUid = base.createdByFirebaseUid ?: uid,
                lastModifiedBy = uid,
                syncedToFirestore = false
            )
        )
    }

    private companion object {
        const val TAG = "OnboardingViewModel"

        /** How long the first step watches for the co-parent's records before reporting. */
        const val FETCH_TIMEOUT_MS = 30_000L

        /** How often the fetch re-reads what has landed. */
        const val FETCH_POLL_MS = 2_500L

        /** When the fetch asks for its one further sync. */
        const val FETCH_NUDGE_AFTER_MS = 10_000L

        /** A fresh record id for a draft the parent has just opened. */
        fun newDraftId(): String = UUID.randomUUID().toString()

        /** This ratio as the share of whichever slot this device holds. */
        fun SplitRatio.myPercent(slotOne: Boolean): Int = if (slotOne) momPercent else dadPercent

        /** True when the four fields the wizard edits read exactly as [draft] has them. */
        fun ChildInfo.isUnchangedBy(draft: ChildDraft, name: String, dateOfBirth: LocalDateTime?): Boolean =
            childName == name &&
                this.dateOfBirth == dateOfBirth &&
                allergies == draft.allergies &&
                medicalProfile == draft.medicalProfile &&
                emergencyContacts == draft.relatives

        /**
         * The drafts already on screen, unless none has been touched — then the stored records.
         *
         * Whole-list rather than field-by-field: with several drafts there is no honest way to
         * merge a stored record into a form the parent may have started typing into, so a list
         * with anything in it is left entirely alone.
         *
         * @param myUid The signed-in account, to tell its own records from the co-parent's
         */
        fun List<ChildDraft>.orStoredChildren(stored: List<ChildInfo>, myUid: String?): List<ChildDraft> =
            if (stored.isEmpty() || any { !it.isBlank }) {
                this
            } else {
                stored.map { child ->
                    ChildDraft(
                        id = child.id,
                        name = child.childName,
                        dateOfBirth = child.dateOfBirth?.toLocalDate(),
                        allergies = child.allergies,
                        medicalProfile = child.medicalProfile,
                        relatives = child.emergencyContacts,
                        byCoParent = isByCoParent(child.createdByFirebaseUid, myUid)
                    )
                }
            }

        /** The pet drafts on screen, unless none has been touched. See [orStoredChildren]. */
        fun List<PetDraft>.orStoredPets(stored: List<Pet>, myUid: String?): List<PetDraft> =
            if (stored.isEmpty() || any { !it.isBlank }) {
                this
            } else {
                stored.map {
                    PetDraft(
                        id = it.id,
                        name = it.name,
                        species = it.species,
                        byCoParent = isByCoParent(it.createdByFirebaseUid, myUid)
                    )
                }
            }

        /** A record stamped with somebody else's uid. An unstamped record is this parent's. */
        fun isByCoParent(createdBy: String?, myUid: String?): Boolean =
            !createdBy.isNullOrBlank() && createdBy != myUid

        /** This text unless it is blank, in which case [stored] — but never a blank [stored]. */
        fun String.orStored(stored: String?): String =
            takeIf { it.isNotBlank() } ?: stored?.takeIf { it.isNotBlank() } ?: this
    }
}
