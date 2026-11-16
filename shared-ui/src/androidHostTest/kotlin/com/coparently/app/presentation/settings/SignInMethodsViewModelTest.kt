package com.coparently.app.presentation.settings

import com.coparently.app.data.remote.firebase.AccountLinking
import com.coparently.app.data.remote.firebase.LinkException
import com.coparently.app.data.remote.firebase.LinkFailure
import com.coparently.app.data.remote.firebase.SignInMethod
import com.coparently.app.presentation.auth.AuthErrors
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.platform.AppleSignInCancelledException
import com.coparently.app.presentation.platform.CredentialSignIn
import com.coparently.app.resources.Res
import com.coparently.app.resources.sign_in_methods_in_use
import com.coparently.app.resources.sign_in_methods_sign_in_again
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Settings' "Sign-in methods" (MON-24 phase 4, part A): the last method is never removable and its
 * removal makes no call, a method another account holds leaves the methods as they were behind a
 * banner that names nothing of that account, a change Firebase wants a recent sign-in for asks the
 * parent to sign in again, Apple's row waits for the build's flag, and an Apple ID linked by the
 * sheet (around the seam) is read again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInMethodsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val linking = FakeAccountLinking()
    private val authErrors = mockk<AuthErrors>()
    private val credentials = mockk<CredentialSignIn>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SignInMethodsViewModel(linking, authErrors)

    @Test
    fun lastMethodCannotBeRemoved() = runTest(dispatcher) {
        linking.methods.value = setOf(SignInMethod.EMAIL)
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.canRemove(SignInMethod.EMAIL))
        val row = vm.state.value.rows(setOf(SignInMethod.EMAIL)).single()
        assertTrue(row.linked)
        assertFalse(row.canRemove, "the last method has no Remove")

        vm.remove(SignInMethod.EMAIL)
        advanceUntilIdle()

        assertEquals(emptyList(), linking.unlinked, "removing the last method makes no call")
        assertEquals(setOf(SignInMethod.EMAIL), vm.state.value.linked)
    }

    @Test
    fun aSecondMethodCanBeRemoved() = runTest(dispatcher) {
        linking.methods.value = setOf(SignInMethod.EMAIL, SignInMethod.GOOGLE)
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.canRemove(SignInMethod.GOOGLE))
        vm.remove(SignInMethod.GOOGLE)
        advanceUntilIdle()

        assertEquals(listOf(SignInMethod.GOOGLE), linking.unlinked)
        assertEquals(setOf(SignInMethod.EMAIL), vm.state.value.linked)
        assertNull(vm.state.value.banner)
    }

    @Test
    fun inUseShowsBannerAndKeepsMethods() = runTest(dispatcher) {
        linking.methods.value = setOf(SignInMethod.EMAIL)
        linking.linkFailure = LinkFailure.InUseByAnotherAccount
        coEvery { credentials.googleIdToken() } returns Result.success("google-id-token")
        val vm = viewModel()
        advanceUntilIdle()

        vm.addGoogle(credentials)
        advanceUntilIdle()

        assertEquals(listOf("google-id-token"), linking.googleTokens)
        assertEquals(UiText.Res(Res.string.sign_in_methods_in_use), vm.state.value.banner)
        assertEquals(setOf(SignInMethod.EMAIL), vm.state.value.linked, "nothing was linked")
        assertNull(vm.state.value.working)
    }

    @Test
    fun needsRecentSignInAsksToSignInAgain() = runTest(dispatcher) {
        linking.methods.value = setOf(SignInMethod.EMAIL, SignInMethod.GOOGLE)
        linking.unlinkFailure = LinkFailure.NeedsRecentSignIn
        val vm = viewModel()
        advanceUntilIdle()

        vm.remove(SignInMethod.GOOGLE)
        advanceUntilIdle()

        assertEquals(UiText.Res(Res.string.sign_in_methods_sign_in_again), vm.state.value.banner)
        assertEquals(setOf(SignInMethod.EMAIL, SignInMethod.GOOGLE), vm.state.value.linked)
    }

    @Test
    fun appleRowHiddenWithoutFlag() = runTest(dispatcher) {
        linking.methods.value = setOf(SignInMethod.EMAIL)
        val vm = viewModel()
        advanceUntilIdle()

        val withoutFlag = SignInMethodsUiState.offeredMethods(googleAvailable = true, appleAvailable = false)
        assertEquals(
            listOf(SignInMethod.EMAIL, SignInMethod.GOOGLE),
            vm.state.value.rows(withoutFlag).map { it.method }
        )

        val withFlag = SignInMethodsUiState.offeredMethods(googleAvailable = true, appleAvailable = true)
        assertEquals(
            listOf(SignInMethod.EMAIL, SignInMethod.GOOGLE, SignInMethod.APPLE),
            vm.state.value.rows(withFlag).map { it.method }
        )
    }

    @Test
    fun appleLinkRefreshesMethods() = runTest(dispatcher) {
        linking.methods.value = setOf(SignInMethod.EMAIL)
        // The sheet links on the platform's own user, which the seam reads only when asked again.
        linking.onRefresh = { setOf(SignInMethod.EMAIL, SignInMethod.APPLE) }
        coEvery { credentials.linkApple() } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.addApple(credentials)
        advanceUntilIdle()

        assertEquals(1, linking.refreshes)
        assertEquals(setOf(SignInMethod.EMAIL, SignInMethod.APPLE), vm.state.value.linked)
        assertEquals(listOf(SignInMethod.EMAIL, SignInMethod.APPLE), vm.state.value.linkedInOrder)
        assertNull(vm.state.value.banner)
    }

    @Test
    fun closingAppleSheetSaysNothing() = runTest(dispatcher) {
        linking.methods.value = setOf(SignInMethod.EMAIL)
        coEvery { credentials.linkApple() } returns Result.failure(AppleSignInCancelledException())
        val vm = viewModel()
        advanceUntilIdle()

        vm.addApple(credentials)
        advanceUntilIdle()

        assertNull(vm.state.value.banner)
        assertEquals(0, linking.refreshes)
    }

    @Test
    fun addEmailLinksTheTrimmedAddress() = runTest(dispatcher) {
        linking.methods.value = setOf(SignInMethod.GOOGLE)
        val vm = viewModel()
        advanceUntilIdle()

        vm.addEmail("  alice@example.com ", "secret-password")
        advanceUntilIdle()

        assertEquals(listOf("alice@example.com"), linking.emails)
        assertEquals(setOf(SignInMethod.EMAIL, SignInMethod.GOOGLE), vm.state.value.linked)
    }

    /** An [AccountLinking] over a set of methods, recording each call. */
    private class FakeAccountLinking : AccountLinking {
        val methods = MutableStateFlow<Set<SignInMethod>>(emptySet())
        var linkFailure: LinkFailure? = null
        var unlinkFailure: LinkFailure? = null
        var onRefresh: (() -> Set<SignInMethod>)? = null
        val googleTokens = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val unlinked = mutableListOf<SignInMethod>()
        var refreshes = 0

        override suspend fun linkGoogle(idToken: String): Result<Unit> {
            googleTokens += idToken
            return link(SignInMethod.GOOGLE)
        }

        override suspend fun linkEmail(email: String, password: String): Result<Unit> {
            emails += email
            return link(SignInMethod.EMAIL)
        }

        private fun link(method: SignInMethod): Result<Unit> = linkFailure
            ?.let { Result.failure<Unit>(LinkException(it)) }
            ?: Result.success(Unit).also { methods.value = methods.value + method }

        override suspend fun unlink(method: SignInMethod): Result<Unit> {
            unlinked += method
            return unlinkFailure
                ?.let { Result.failure<Unit>(LinkException(it)) }
                ?: Result.success(Unit).also { methods.value = methods.value - method }
        }

        override fun linkedMethods(): Flow<Set<SignInMethod>> = methods

        override suspend fun refresh() {
            refreshes++
            onRefresh?.let { methods.value = it() }
        }
    }
}
