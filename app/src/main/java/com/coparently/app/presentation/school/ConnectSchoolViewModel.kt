package com.coparently.app.presentation.school

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.school.SchoolAccounts
import com.coparently.app.data.school.SchoolImporter
import com.coparently.app.data.school.SchoolSignIn
import com.coparently.app.data.school.bakalari.BakalariException
import com.coparently.app.data.school.bakalari.BakalariSchoolDirectory
import com.coparently.app.data.school.bakalari.BakalariUrls
import com.coparently.app.data.school.bakalari.DirectorySchool
import com.coparently.app.data.school.bakalari.SchoolTown
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** The steps of connecting a school, in order (MON-8). */
enum class ConnectStep {
    /** Find the school's town in Bakaláři's directory. */
    TOWN,

    /** Pick the school in that town. */
    SCHOOL,

    /** Type the school's address instead. */
    MANUAL,

    /** Username and password. */
    CREDENTIALS,

    /** Whose account it is, and which CoPlanly child (and family) it is for. */
    CHOOSE,

    /** What will be imported, then connect. */
    CONFIRM
}

/**
 * A CoPlanly child the parent can link the account to.
 *
 * @property id The child record's id.
 * @property name The child's name.
 */
data class ChildChoice(val id: String, val name: String)

/**
 * The account the school confirmed.
 *
 * @property displayName "Surname Name, class", as the school writes it.
 * @property canReadTimetable Whether school hours and days off can be imported.
 * @property canReadEvents Whether school events can be imported.
 */
data class SignedInAccount(
    val displayName: String,
    val canReadTimetable: Boolean,
    val canReadEvents: Boolean
) {
    /** Whether there is anything to import at all. */
    val canImport: Boolean get() = canReadTimetable || canReadEvents
}

/**
 * The connect screen's state (MON-8).
 *
 * @property step The step on screen.
 * @property reconnecting Whether this is "Sign in again" for a stored connection, which asks
 *   for the password only.
 * @property townQuery What the parent typed to find the town.
 * @property towns Bakaláři's towns, as loaded.
 * @property town The chosen town, or null.
 * @property schools The schools in [town].
 * @property manualUrl The address typed by hand.
 * @property schoolName The chosen school's name, blank for an address typed by hand.
 * @property baseUrl The chosen school's server.
 * @property username The username typed.
 * @property account Who the login belongs to, once signed in.
 * @property families The parent's families; the choice is offered only at two or more.
 * @property familyId The family chosen, or the only one.
 * @property children The children of that family.
 * @property childId The child chosen.
 * @property isLoading A list is being fetched.
 * @property isBusy A sign-in, a check or the save is running.
 * @property error What went wrong, worded, or null.
 * @property done The flow is finished and the screen should close.
 */
data class ConnectSchoolUiState(
    val step: ConnectStep = ConnectStep.TOWN,
    val reconnecting: Boolean = false,
    val townQuery: String = "",
    val towns: List<SchoolTown> = emptyList(),
    val town: String? = null,
    val schools: List<DirectorySchool> = emptyList(),
    val manualUrl: String = "",
    val schoolName: String = "",
    val baseUrl: String? = null,
    val username: String = "",
    val account: SignedInAccount? = null,
    val families: List<FamilyOption> = emptyList(),
    val familyId: String? = null,
    val children: List<ChildChoice> = emptyList(),
    val childId: String? = null,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val error: UiText? = null,
    val done: Boolean = false
) {
    /** The towns whose name contains [townQuery], ignoring case. */
    val visibleTowns: List<SchoolTown>
        get() {
            val query = townQuery.trim().lowercase(Locale.ROOT)
            return if (query.isEmpty()) towns else towns.filter { query in it.name.lowercase(Locale.ROOT) }
        }

    /** Whether the parent chooses the family: only when there are two or more. */
    val choosesFamily: Boolean get() = families.size > 1

    /** Whether the choices on [ConnectStep.CHOOSE] are complete. */
    val canContinue: Boolean
        get() = account?.canImport == true && childId != null && (!choosesFamily || familyId != null)
}

/**
 * Connecting a school account, one child per account (MON-8).
 *
 * **The password passes through [signIn] and nowhere else.** It is not a field of the state, is
 * not put in the [SavedStateHandle], and is handed straight to the sign-in call; the screen keeps
 * the text field in plain `remember`, not `rememberSaveable`, and clears it after the call.
 *
 * **Nothing is chosen for the parent.** Several children: none is ticked. Several families: none
 * is ticked. A family with no child records says so and offers nothing.
 */
@HiltViewModel
class ConnectSchoolViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accounts: SchoolAccounts,
    private val directory: BakalariSchoolDirectory,
    private val importer: SchoolImporter,
    private val selectedFamilySource: SelectedFamilySource,
    private val childInfoRepository: ChildInfoRepository
) : ViewModel() {

    private val reconnectId: String? = savedStateHandle.get<String>(ARG_CONNECTION_ID)?.takeIf { it.isNotBlank() }

    /** The sign-in awaiting the parent's choices. Tokens only; the password is already gone. */
    private var pending: SchoolSignIn? = null

    private val _state = MutableStateFlow(ConnectSchoolUiState())

    /** What the screen shows. */
    val state: StateFlow<ConnectSchoolUiState> = _state.asStateFlow()

    init {
        if (reconnectId != null) startReconnect(reconnectId) else loadTowns()
    }

    /** Loads the directory's towns again, after a failure. */
    fun loadTowns() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val towns = attempt { directory.towns() }
            _state.update {
                if (towns == null) {
                    it.copy(isLoading = false, error = UiText.Res(R.string.school_connect_towns_failed))
                } else {
                    it.copy(isLoading = false, towns = towns)
                }
            }
        }
    }

    /** The town search text changed. */
    fun onTownQuery(query: String) {
        _state.update { it.copy(townQuery = query) }
    }

    /** The parent picked [town]; its schools are loaded. */
    fun chooseTown(town: String) {
        _state.update {
            it.copy(step = ConnectStep.SCHOOL, town = town, schools = emptyList(), isLoading = true, error = null)
        }
        viewModelScope.launch {
            val schools = attempt { directory.schools(town) }
            _state.update {
                if (schools == null) {
                    it.copy(isLoading = false, error = UiText.Res(R.string.school_connect_schools_failed))
                } else {
                    it.copy(isLoading = false, schools = schools)
                }
            }
        }
    }

    /** The parent picked [school]. */
    fun chooseSchool(school: DirectorySchool) {
        _state.update {
            it.copy(step = ConnectStep.CREDENTIALS, schoolName = school.name, baseUrl = school.baseUrl, error = null)
        }
    }

    /** The parent will type the school's address instead. */
    fun enterManually() {
        _state.update { it.copy(step = ConnectStep.MANUAL, error = null) }
    }

    /** The typed address changed. */
    fun onManualUrl(url: String) {
        _state.update { it.copy(manualUrl = url, error = null) }
    }

    /** Checks the typed address with `GET {base}/api` and, when a Bakaláři server answers, moves on. */
    fun checkManualUrl() {
        val baseUrl = BakalariUrls.normalize(_state.value.manualUrl)
        if (baseUrl == null) {
            _state.update { it.copy(error = UiText.Res(R.string.school_connect_url_invalid)) }
            return
        }
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val failure = failureOf { accounts.checkServer(baseUrl) }
            _state.update {
                if (failure == null) {
                    it.copy(isBusy = false, step = ConnectStep.CREDENTIALS, schoolName = "", baseUrl = baseUrl)
                } else {
                    it.copy(isBusy = false, error = failure)
                }
            }
        }
    }

    /** The username changed. */
    fun onUsername(username: String) {
        _state.update { it.copy(username = username, error = null) }
    }

    /**
     * Signs in with [password], which is used for this one call and kept nowhere.
     *
     * For a new connection the account is then shown and the parent chooses the child; when
     * signing a stored connection in again, it is saved and updated at once.
     */
    fun signIn(password: String) {
        val current = _state.value
        val baseUrl = current.baseUrl ?: return
        if (current.isBusy || current.username.isBlank() || password.isEmpty()) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            if (reconnectId != null) {
                finishReconnect(reconnectId, password)
            } else {
                signInNew(baseUrl, current.username, password)
            }
        }
    }

    /** The parent picked the family [familyId]; its children are offered. */
    fun chooseFamily(familyId: String) {
        _state.update { it.copy(familyId = familyId, childId = null) }
        viewModelScope.launch { loadChildren(familyId) }
    }

    /** The parent picked the child [childId]. */
    fun chooseChild(childId: String) {
        _state.update { it.copy(childId = childId) }
    }

    /** From the choices to the summary of what will be imported. */
    fun toConfirm() {
        if (_state.value.canContinue) _state.update { it.copy(step = ConnectStep.CONFIRM) }
    }

    /** Saves the connection and runs its first import. */
    fun connect() {
        val current = _state.value
        val signIn = pending ?: return
        val childId = current.childId ?: return
        if (current.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val saved = accounts.connect(signIn, current.schoolName, childId, current.familyId)
            pending = null
            saved?.let { importer.sync(it.id) }
            _state.update { it.copy(isBusy = false, done = true) }
        }
    }

    /**
     * One step back, or false when there is none and the screen should close. A signed-in step
     * goes back to the credentials, and the sign-in is dropped with it.
     */
    fun back(): Boolean {
        val current = _state.value
        if (current.isBusy) return true
        val previous = when (current.step) {
            ConnectStep.TOWN -> null
            ConnectStep.SCHOOL, ConnectStep.MANUAL -> ConnectStep.TOWN
            ConnectStep.CREDENTIALS -> when {
                current.reconnecting -> null
                current.schoolName.isEmpty() -> ConnectStep.MANUAL
                else -> ConnectStep.SCHOOL
            }
            ConnectStep.CHOOSE -> ConnectStep.CREDENTIALS
            ConnectStep.CONFIRM -> ConnectStep.CHOOSE
        } ?: return false
        if (current.step == ConnectStep.CHOOSE) pending = null
        _state.update { it.copy(step = previous, error = null) }
        return true
    }

    private fun startReconnect(connectionId: String) {
        _state.update { it.copy(reconnecting = true, step = ConnectStep.CREDENTIALS, isLoading = true) }
        viewModelScope.launch {
            val connection = accounts.connection(connectionId)
            _state.update {
                if (connection == null) {
                    it.copy(isLoading = false, done = true)
                } else {
                    it.copy(
                        isLoading = false,
                        schoolName = connection.schoolName,
                        baseUrl = connection.baseUrl,
                        username = connection.username
                    )
                }
            }
        }
    }

    private suspend fun finishReconnect(connectionId: String, password: String) {
        var reconnected = false
        val failure = failureOf { reconnected = accounts.reconnect(connectionId, password) != null }
        if (failure != null) {
            _state.update { it.copy(isBusy = false, error = failure) }
            return
        }
        if (reconnected) importer.sync(connectionId)
        _state.update { it.copy(isBusy = false, done = true) }
    }

    private suspend fun signInNew(baseUrl: String, username: String, password: String) {
        var signIn: SchoolSignIn? = null
        val failure = failureOf { signIn = accounts.signIn(baseUrl, username.trim(), password) }
        val account = signIn
        if (failure != null || account == null) {
            _state.update { it.copy(isBusy = false, error = failure) }
            return
        }
        pending = account
        val families = selectedFamilySource.namedFamilies()
        val familyId = families.singleOrNull()?.familyId
        _state.update {
            it.copy(
                isBusy = false,
                step = ConnectStep.CHOOSE,
                schoolName = it.schoolName.ifBlank { account.account.schoolName },
                account = SignedInAccount(
                    displayName = account.account.displayName,
                    canReadTimetable = account.account.canReadTimetable,
                    canReadEvents = account.account.canReadEvents
                ),
                families = families,
                familyId = familyId,
                children = emptyList(),
                childId = null
            )
        }
        if (families.size <= 1) loadChildren(familyId)
    }

    /**
     * The children of [familyId] — records stamped with it, and records that belong to no family
     * yet (made before pairing). With one child, that child is chosen; with several, none is.
     */
    private suspend fun loadChildren(familyId: String?) {
        val children = childInfoRepository.getAllChildInfo().first()
            .filter { it.belongsTo(familyId) }
            .map { ChildChoice(it.id, it.childName) }
        _state.update { current ->
            if (current.familyId != familyId) {
                current
            } else {
                current.copy(children = children, childId = children.singleOrNull()?.id)
            }
        }
    }

    companion object {
        /** The navigation argument naming a stored connection to sign in again. */
        const val ARG_CONNECTION_ID = "connectionId"
    }
}

private fun ChildInfo.belongsTo(familyId: String?): Boolean =
    this.familyId.isNullOrBlank() || (familyId != null && this.familyId == familyId)

/** Runs [block]; null when the school or the directory could not answer. */
private suspend fun <T> attempt(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: BakalariException) {
    null
}

/** Runs [block] and words what went wrong, or null when nothing did. */
private suspend fun failureOf(block: suspend () -> Unit): UiText? = try {
    block()
    null
} catch (e: CancellationException) {
    throw e
} catch (e: BakalariException) {
    UiText.Res(messageFor(e))
}

private fun messageFor(error: BakalariException): Int = when (error) {
    is BakalariException.InvalidGrant -> R.string.school_connect_wrong_password
    is BakalariException.Network -> R.string.school_connect_network_error
    is BakalariException.NotBakalari, is BakalariException.InsecureUrl -> R.string.school_connect_url_not_bakalari
    else -> R.string.school_connect_server_error
}
