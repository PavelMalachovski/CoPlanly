package com.coparently.app.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.AuthSession
import com.coparently.app.data.remote.firebase.PushRegistration
import com.coparently.app.data.school.SchoolConnectionStore
import com.coparently.app.data.session.AccountTraces
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.onboarding.OnboardingState
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for managing authentication state across the app.
 * Provides authentication state to determine navigation routing.
 *
 * It also answers the second half of that routing question — whether this account still has to
 * be walked through the first-run questionnaire. Both answers live here rather than in a second
 * view model so the start destination has one source that cannot disagree with itself.
 *
 * In `:shared-ui` since MON-24 phase 2 (Task 10): the session, the push token and the files left on
 * the phone go through [AuthSession], [PushRegistration] and [AccountTraces], which the app's
 * `FirebaseAuthService`, `FcmService` and `DeviceTraces` answer on Android; each seam is one call of
 * the ones [signOut] makes, so their order is this function's, as before. The parameter names are
 * the ones the app's classes gave them.
 */
// Eight collaborators, all injected: a Koin graph edge list, and the last four are what sign-out
// has to undo on this device (push token, school tokens, pending reminders, pending request-clock
// steps) — the same reasoning `PairingViewModel` uses.
@Suppress("LongParameterList")
class AuthStateViewModel(
    private val firebaseAuthService: AuthSession,
    private val userRepository: UserRepository,
    private val childInfoRepository: ChildInfoRepository,
    private val petRepository: PetRepository,
    private val fcmService: PushRegistration,
    private val schoolConnections: SchoolConnectionStore,
    private val reminderScheduler: ReminderScheduler,
    private val requestClockScheduler: RequestClockScheduler,
    private val deviceTraces: AccountTraces
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow<Boolean?>(null)
    val isAuthenticated: StateFlow<Boolean?> = _isAuthenticated.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Whether the first-run questionnaire still has to run, or null while the answer is unknown.
     *
     * Null must route to the loading screen, never to Home: flashing the dashboard and then
     * replacing it with a questionnaire is worse than a moment's spinner.
     */
    private val _needsOnboarding = MutableStateFlow<Boolean?>(null)
    val needsOnboarding: StateFlow<Boolean?> = _needsOnboarding.asStateFlow()

    init {
        checkAuthState()
    }

    /**
     * Checks the current authentication state, and — when there is an account — whether it still
     * needs onboarding. Both are published before [isLoading] clears, so no caller ever sees a
     * resolved authentication with an unresolved questionnaire.
     */
    private fun checkAuthState() {
        viewModelScope.launch {
            _isLoading.value = true
            _needsOnboarding.value = null

            // Wait for authentication to be ready, then check state
            val uid = firebaseAuthService.awaitSignedInUid()
            _isAuthenticated.value = uid != null
            _needsOnboarding.value = uid?.let { resolveOnboarding(it) } ?: false
            _isLoading.value = false
        }
    }

    /**
     * Whether [uid] still has to be walked through the questionnaire.
     *
     * Waits for the account's Room row rather than reading once, because on the launch right
     * after a sign-up the row is still being written: `ensureProfile` runs asynchronously off
     * the auth-state boundary, and a single read that happened to land first would report "no
     * user", which [OnboardingState] reads as "nobody to ask" — sending a brand-new parent
     * straight past the wizard they exist to see. The wait is bounded by the same
     * [ONBOARDING_LOAD_TIMEOUT_MILLIS] `ProfileViewModel` uses; timing out falls back to not
     * asking, because the one thing worse than missing the wizard is trapping a signed-in user
     * on it with no account behind it.
     *
     * A failure is treated the same way, and deliberately not surfaced: this decides a
     * navigation route, and there is no screen yet on which to show an error about it.
     *
     * **A "yes" from the row is confirmed with the server before the wizard runs.** The row's
     * onboarding marker is a copy of `users/{uid}.onboardingCompletedAt`, and a row seeded while
     * that document could not be read carries none: the iPhone's shell walk signed its seeded
     * parent, who had finished onboarding, back in to the wizard that way. So when the row says
     * the wizard is owed, [UserRepository.ensureProfile] reads the document once more (it fills a
     * blank marker from it) and the row is asked again; when that cannot finish in
     * [ONBOARDING_CONFIRM_TIMEOUT_MILLIS], the row's answer stands. A brand-new account still
     * gets the wizard: its document holds no marker either.
     */
    private suspend fun resolveOnboarding(uid: String): Boolean = try {
        val fromRow = onboardingFromRow(uid) ?: false
        if (!fromRow) {
            false
        } else {
            withTimeoutOrNull(ONBOARDING_CONFIRM_TIMEOUT_MILLIS) { userRepository.ensureProfile() }
            onboardingFromRow(uid) ?: true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        logger.e(TAG, "Failed to decide whether onboarding is needed", e)
        false
    }

    /**
     * What the account's Room row and its own records say: whether [uid] still owes the wizard,
     * or null when the row did not arrive within [ONBOARDING_LOAD_TIMEOUT_MILLIS].
     */
    private suspend fun onboardingFromRow(uid: String): Boolean? =
        withTimeoutOrNull(ONBOARDING_LOAD_TIMEOUT_MILLIS) {
            val account = userRepository.observeUserById(uid).first { it != null }
            // Only records this account created are evidence that it has been through the
            // wizard. The wizard links the co-parent first, so a second parent's phone holds the
            // first one's children within seconds of pairing — see `OnboardingState.isOwnRecord`.
            // Read in every family: a parent whose own children are in the family not on screen
            // has been through the wizard all the same (isolation audit, October 2026).
            val hasChildInfo = childInfoRepository.getAllChildInfoInEveryFamily().first()
                .any { OnboardingState.isOwnRecord(uid, it.createdByFirebaseUid) }
            // A pet counts as evidence too. Counting children alone handed the questionnaire to
            // a pets-only family on every launch.
            val hasPets = petRepository.getAllPetsInEveryFamily().first()
                .any { OnboardingState.isOwnRecord(uid, it.createdByFirebaseUid) }
            OnboardingState.isNeeded(account, hasChildInfo, hasPets)
        }

    /**
     * Refreshes the authentication state.
     * Call this after login/logout operations.
     */
    fun refreshAuthState() {
        checkAuthState()
    }

    /**
     * Signs out the current user from Firebase authentication.
     * This should be called when the user wants to sign out of the app completely.
     */
    fun signOut() {
        viewModelScope.launch {
            // Before the session ends, while the account can still write its own document: a
            // token that stays on the profile keeps this phone receiving the account's pushes —
            // the co-parent's chat included — for whoever signs in next.
            fcmService.unregisterToken()
            // The school connections' tokens (MON-8) are the account's, and `EncryptedPreferences`'
            // `clear()` deliberately keeps them, so they go here, explicitly.
            schoolConnections.clearAll()
            // Room survives sign-out, and a reminder reads its title from Room when it fires: left
            // armed, the account's events would go on announcing themselves on a signed-out phone.
            // Signing back in re-arms them as the sync delivers each event again.
            reminderScheduler.cancelAll()
            // The request clocks' steps (phase 3a) too: a reminder or an end left armed would act
            // for this account after it signed out. The next sync re-arms them when it signs in.
            requestClockScheduler.cancelAll()
            // The plaintext copies of the account's files (a child's medical photos, vault
            // documents, the last export) and its notifications: Room is kept, these are not.
            deviceTraces.clearAfterSignOut()
            firebaseAuthService.signOutCompletely()
            refreshAuthState()
        }
    }

    private companion object {
        const val TAG = "AuthStateViewModel"

        /** Same value `ProfileViewModel`'s own wait for the signed-in user's row uses. */
        const val ONBOARDING_LOAD_TIMEOUT_MILLIS = 5000L

        /** How long [resolveOnboarding] lets the server confirm a row that says the wizard is owed. */
        const val ONBOARDING_CONFIRM_TIMEOUT_MILLIS = 5000L
    }
}
