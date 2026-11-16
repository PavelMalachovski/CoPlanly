package com.coparently.app.presentation.auth

import android.app.Activity
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.SignInOutcome
import com.coparently.app.data.remote.google.CredentialAuthenticator
import com.coparently.app.data.remote.google.SavedPassword
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.onboarding.NewAccountAsk
import com.coparently.app.presentation.platform.AppleSignInCancelledException
import com.coparently.app.presentation.platform.AppleSignInResult
import com.coparently.app.presentation.platform.CredentialSignIn
import com.coparently.app.presentation.platform.SignsInAnotherWayException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the login screen must do around the system's password manager.
 *
 * The load-bearing case is `a declined password save still signs the parent in`: offering to
 * remember a password happens *after* Firebase has already accepted the credentials, so a refusal
 * — or a device with no password manager at all — must not turn a successful authentication into
 * a failed one. Getting that backwards locks out the user who taps "Never".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val activity = mockk<Activity>(relaxed = true)
    private lateinit var firebaseAuthService: FirebaseAuthService
    private lateinit var credentialAuthenticator: CredentialAuthenticator
    private lateinit var crashlyticsManager: CrashlyticsManager
    private lateinit var newAccountAsk: NewAccountAsk
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: AuthViewModel

    /**
     * The shell's credential sheets, as `MainActivity` provides them: [credentialAuthenticator] on
     * [activity], so each case still drives Credential Manager's calls with the Activity (MON-24
     * phase 2, slice 11f: the ViewModel takes this where it took the Activity).
     */
    private val credentials get() = AndroidCredentialSignIn(activity, credentialAuthenticator, mockk(relaxed = true))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        firebaseAuthService = mockk()
        credentialAuthenticator = mockk(relaxed = true)
        crashlyticsManager = mockk(relaxed = true)
        newAccountAsk = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        coEvery { userRepository.getCurrentUserId() } returns "uid-1"
        viewModel = AuthViewModel(
            firebaseAuthService = firebaseAuthService,
            authErrors = AndroidAuthErrors,
            analyticsManager = mockk(relaxed = true),
            crashlyticsManager = crashlyticsManager,
            newAccountAsk = newAccountAsk,
            userRepository = userRepository
        )
        viewModel.updateEmail("parent@example.com")
        viewModel.updatePassword("hunter2")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a declined password save still signs the parent in`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.signInWithEmail(any(), any()) } returns
            Result.success(mockk<FirebaseUser>())
        // savePassword swallows its own failures and returns Unit, so a decline is
        // indistinguishable here from an accept - which is exactly the contract under test.
        coEvery { credentialAuthenticator.savePassword(any(), any(), any()) } returns Unit

        var signedIn = false
        viewModel.signIn(credentials) { signedIn = true }
        advanceUntilIdle()

        assertTrue(signedIn, "sign-in must complete regardless of the save prompt")
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `the save is offered before navigating away, not after`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.signInWithEmail(any(), any()) } returns
            Result.success(mockk<FirebaseUser>())
        // Record the real order rather than asserting inside a callback: if navigation ran
        // first, the system's save sheet would land on top of the home screen.
        val order = mutableListOf<String>()
        coEvery { credentialAuthenticator.savePassword(activity, "parent@example.com", "hunter2") }
            .answers { order += "save" }

        viewModel.signIn(credentials) { order += "navigate" }
        advanceUntilIdle()

        assertEquals(listOf("save", "navigate"), order)
    }

    @Test
    fun `blank fields are rejected without troubling Firebase`() = runTest(dispatcher) {
        viewModel.updatePassword("")

        viewModel.signIn(credentials) { error("must not navigate") }
        advanceUntilIdle()

        assertEquals(AuthError.EmptyFields, viewModel.uiState.value.error)
        coVerify(exactly = 0) { firebaseAuthService.signInWithEmail(any(), any()) }
    }

    @Test
    fun `a Firebase rejection lands in the state as a type and clears loading`() =
        runTest(dispatcher) {
            coEvery { firebaseAuthService.signInWithEmail(any(), any()) } returns
                Result.failure(FirebaseAuthException("ERROR_WRONG_PASSWORD", "nope"))

            viewModel.signIn(credentials) { error("must not navigate") }
            advanceUntilIdle()

            assertEquals(AuthError.InvalidCredentials, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `dismissing Google's sheet leaves no error on screen`() = runTest(dispatcher) {
        coEvery { credentialAuthenticator.signInWithGoogle(any()) } returns
            Result.failure(GetCredentialCancellationException())

        viewModel.signInWithGoogle(credentials)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error, "a deliberate cancel is not an error")
        assertFalse(viewModel.uiState.value.isLoading)
        // Nor is it a fault worth reporting: a stream of cancellations would bury real ones.
        verify(exactly = 0) { crashlyticsManager.recordWithContext(any(), any()) }
    }

    @Test
    fun `a saved password is fed through the same email sign-in path`() = runTest(dispatcher) {
        coEvery { credentialAuthenticator.getSavedPassword(any()) } returns
            Result.success(SavedPassword("saved@example.com", "s3cret"))
        coEvery { firebaseAuthService.signInWithEmail(any(), any()) } returns
            Result.success(mockk<FirebaseUser>())

        viewModel.signInWithSavedPassword(credentials)
        advanceUntilIdle()

        coVerify { firebaseAuthService.signInWithEmail("saved@example.com", "s3cret") }
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `having nothing saved says so rather than failing silently`() = runTest(dispatcher) {
        coEvery { credentialAuthenticator.getSavedPassword(any()) } returns
            Result.failure(NoCredentialException())

        viewModel.signInWithSavedPassword(credentials)
        advanceUntilIdle()

        assertEquals(AuthError.NoSavedPassword, viewModel.uiState.value.error)
    }

    @Test
    fun `signing in with a saved password does not re-offer to save it`() = runTest(dispatcher) {
        coEvery { credentialAuthenticator.getSavedPassword(any()) } returns
            Result.success(SavedPassword("saved@example.com", "s3cret"))
        coEvery { firebaseAuthService.signInWithEmail(any(), any()) } returns
            Result.success(mockk<FirebaseUser>())

        viewModel.signInWithSavedPassword(credentials)
        advanceUntilIdle()

        coVerify(exactly = 0) { credentialAuthenticator.savePassword(any(), any(), any()) }
    }

    /**
     * Regression coverage for the isLoading-stuck-forever defect: both [AuthViewModel.signInWithGoogle]
     * and [AuthViewModel.signInWithSavedPassword] run from a `rememberCoroutineScope()` in
     * `AuthScreen` that a configuration change cancels mid-flight, and the narrowed exception
     * catch in `CredentialAuthenticator` (correct for detekt) no longer clears `isLoading` by
     * accident the way a blanket `catch (e: Exception)` used to.
     */
    @Test
    fun `cancelling signInWithGoogle mid-flight leaves isLoading false`() = runTest(dispatcher) {
        coEvery { credentialAuthenticator.signInWithGoogle(any()) } coAnswers { awaitCancellation() }

        val job = launch { viewModel.signInWithGoogle(credentials) }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading, "should be loading while getCredential is suspended")

        job.cancelAndJoin()

        assertFalse(
            viewModel.uiState.value.isLoading,
            "cancelling the hosting coroutine must not leave every control on screen disabled"
        )
    }

    @Test
    fun `cancelling signInWithSavedPassword mid-flight leaves isLoading false`() = runTest(dispatcher) {
        coEvery { credentialAuthenticator.getSavedPassword(any()) } coAnswers { awaitCancellation() }

        val job = launch { viewModel.signInWithSavedPassword(credentials) }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading, "should be loading while getCredential is suspended")

        job.cancelAndJoin()

        assertFalse(
            viewModel.uiState.value.isLoading,
            "cancelling the hosting coroutine must not leave every control on screen disabled"
        )
    }

    @Test
    fun `a cancellation surfacing from FirebaseAuthService is not treated as a sign-in failure`() =
        runTest(dispatcher) {
            // FirebaseAuthService catches Exception broadly, so a coroutine cancellation crosses
            // it disguised as an ordinary Result.failure - this is what that looks like arriving.
            coEvery { credentialAuthenticator.getSavedPassword(any()) } returns
                Result.success(SavedPassword("saved@example.com", "s3cret"))
            coEvery { firebaseAuthService.signInWithEmail(any(), any()) } returns
                Result.failure(CancellationException("scope cancelled"))

            val job = launch { viewModel.signInWithSavedPassword(credentials) }
            advanceUntilIdle()

            assertTrue(job.isCancelled, "rethrowing must cancel this coroutine rather than crash it")
            assertNull(viewModel.uiState.value.error, "a cancellation must never surface as an AuthError")
            assertFalse(viewModel.uiState.value.isLoading)
            verify(exactly = 0) { crashlyticsManager.recordWithContext(any(), any()) }
        }

    @Test
    fun `the same cancellation guard protects the typed sign-in path`() = runTest(dispatcher) {
        // signIn() is not itself suspend - it launches on viewModelScope internally - but that
        // scope is torn down along with the ViewModel if the backstack entry is popped mid-call,
        // which produces the same cancel-disguised-as-Result.failure shape from FirebaseAuthService.
        coEvery { firebaseAuthService.signInWithEmail(any(), any()) } returns
            Result.failure(CancellationException("view model cleared"))

        var navigated = false
        viewModel.signIn(credentials) { navigated = true }
        advanceUntilIdle()

        assertFalse(navigated, "must not navigate on a cancellation")
        assertNull(viewModel.uiState.value.error, "a cancellation must never surface as an AuthError")
        assertFalse(viewModel.uiState.value.isLoading)
        verify(exactly = 0) { crashlyticsManager.recordWithContext(any(), any()) }
    }

    @Test
    fun `a genuine saved-password credential failure reaches Crashlytics`() = runTest(dispatcher) {
        val failure = GetCredentialUnknownException()
        coEvery { credentialAuthenticator.getSavedPassword(any()) } returns Result.failure(failure)

        viewModel.signInWithSavedPassword(credentials)
        advanceUntilIdle()

        verify {
            crashlyticsManager.recordWithContext(
                failure,
                mapOf("action" to "saved_password_credential")
            )
        }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    /** The shell's sheets answering an Apple sign-in with [result], as the iPhone's and Android's do. */
    private fun appleAnswering(result: Result<AppleSignInResult>): CredentialSignIn = mockk {
        coEvery { signInWithApple() } returns result
    }

    private fun appleSignIn(isNewUser: Boolean, givenName: String? = null, familyName: String? = null) =
        Result.success(AppleSignInResult(SignInOutcome(isNewUser), givenName, familyName))

    private val profile = User(
        id = "uid-1",
        email = "x@privaterelay.appleid.com",
        name = "",
        role = "mom",
        colorCode = ""
    )

    @Test
    fun appleNewUserMarksQuestion() = runTest(dispatcher) {
        var signedIn = false
        viewModel.onAuthSuccess = { signedIn = true }

        viewModel.signInWithApple(appleAnswering(appleSignIn(isNewUser = true)))
        advanceUntilIdle()

        coVerify(exactly = 1) { newAccountAsk.pending("uid-1") }
        assertTrue(signedIn)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun appleExistingUserDoesNotMarkQuestion() = runTest(dispatcher) {
        viewModel.signInWithApple(appleAnswering(appleSignIn(isNewUser = false)))
        advanceUntilIdle()

        coVerify(exactly = 0) { newAccountAsk.pending(any()) }
    }

    @Test
    fun googleNewUserMarksQuestion() = runTest(dispatcher) {
        coEvery { credentialAuthenticator.signInWithGoogle(any()) } returns Result.success("id-token")
        coEvery { firebaseAuthService.signInWithGoogleIdToken("id-token") } returns
            Result.success(SignInOutcome(isNewUser = true))

        viewModel.signInWithGoogle(credentials)
        advanceUntilIdle()

        coVerify(exactly = 1) { newAccountAsk.pending("uid-1") }
    }

    @Test
    fun googleExistingUserDoesNotMarkQuestion() = runTest(dispatcher) {
        coEvery { credentialAuthenticator.signInWithGoogle(any()) } returns Result.success("id-token")
        coEvery { firebaseAuthService.signInWithGoogleIdToken("id-token") } returns
            Result.success(SignInOutcome(isNewUser = false))
        var signedIn = false
        viewModel.onAuthSuccess = { signedIn = true }

        viewModel.signInWithGoogle(credentials)
        advanceUntilIdle()

        assertTrue(signedIn)
        coVerify(exactly = 0) { newAccountAsk.pending(any()) }
    }

    @Test
    fun emailSignUpNeverMarksQuestion() = runTest(dispatcher) {
        // An account made through "Create account" was made on purpose (spec §3.8 call 4).
        coEvery { firebaseAuthService.createAccountWithEmail(any(), any()) } returns
            Result.success(mockk<FirebaseUser>())
        viewModel.toggleSignInMode()
        var signedIn = false

        viewModel.signUp(credentials) { signedIn = true }
        advanceUntilIdle()

        assertTrue(signedIn)
        coVerify(exactly = 0) { newAccountAsk.pending(any()) }
    }

    @Test
    fun existsWithDifferentMethodMapsToGenericError() = runTest(dispatcher) {
        viewModel.signInWithApple(appleAnswering(Result.failure(SignsInAnotherWayException())))
        advanceUntilIdle()

        assertEquals(AuthError.ExistsWithDifferentMethod, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { newAccountAsk.pending(any()) }

        // Google's sign-in answers the same Firebase refusal with its own code: the same sentence.
        coEvery { credentialAuthenticator.signInWithGoogle(any()) } returns Result.success("id-token")
        coEvery { firebaseAuthService.signInWithGoogleIdToken("id-token") } returns
            Result.failure(FirebaseAuthException("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "x"))

        viewModel.signInWithGoogle(credentials)
        advanceUntilIdle()

        assertEquals(AuthError.ExistsWithDifferentMethod, viewModel.uiState.value.error)
    }

    @Test
    fun closingAppleSheetLeavesNoErrorOnScreen() = runTest(dispatcher) {
        viewModel.signInWithApple(appleAnswering(Result.failure(AppleSignInCancelledException())))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error, "a deliberate cancel is not an error")
        assertFalse(viewModel.uiState.value.isLoading)
        verify(exactly = 0) { crashlyticsManager.recordWithContext(any(), any()) }
    }

    @Test
    fun appleFirstSignInSeedsName() = runTest(dispatcher) {
        // The session may have written the profile before the shell set Apple's name on the
        // account: the name Apple shared must still reach the profile.
        coEvery { userRepository.getCurrentUser() } returns profile

        viewModel.signInWithApple(
            appleAnswering(appleSignIn(isNewUser = true, givenName = "Jana", familyName = "Nováková"))
        )
        advanceUntilIdle()

        coVerifyOrder {
            userRepository.ensureProfile()
            userRepository.updateUser(profile.copy(name = "Jana Nováková"))
        }
    }

    @Test
    fun appleNameNeverReplacesAStoredName() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns profile.copy(name = "Jana")

        viewModel.signInWithApple(
            appleAnswering(appleSignIn(isNewUser = false, givenName = "Jana", familyName = "Nováková"))
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { userRepository.updateUser(any()) }
        coVerify(exactly = 0) { userRepository.ensureProfile() }
    }

    @Test
    fun appleWithoutANameWritesNoName() = runTest(dispatcher) {
        viewModel.signInWithApple(appleAnswering(appleSignIn(isNewUser = true)))
        advanceUntilIdle()

        coVerify(exactly = 0) { userRepository.updateUser(any()) }
    }
}
