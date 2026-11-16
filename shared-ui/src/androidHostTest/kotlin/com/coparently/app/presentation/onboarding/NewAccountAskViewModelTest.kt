package com.coparently.app.presentation.onboarding

import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.platform.SealedTextStore
import com.coparently.app.data.platform.StoredText
import com.coparently.app.data.remote.firebase.AuthSession
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.session.AccountConsistency
import com.coparently.app.data.session.AccountDeletion
import com.coparently.app.presentation.common.UiText
import com.coparently.app.resources.Res
import com.coparently.app.resources.new_account_ask_retry_failed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Already use CoPlanly?" (MON-24 phase 4, part A): asked only of the account the sign-in marked,
 * answered once, and "Sign out and try again" deleting the new account before it signs out — and
 * signing nothing out when the deletion fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewAccountAskViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val preferences = SealedSecurePreferences(MemoryText())
    private val consistency = mockk<AccountConsistency>(relaxed = true)
    private val store = NewAccountAsk(preferences, consistency)
    private val signedInUser = mockk<SignedInUser>()
    private val deletion = mockk<AccountDeletion>()
    private val session = mockk<AuthSession>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { signedInUser.getCurrentUid() } returns "new-uid"
        coEvery { session.signOutCompletely() } returns Result.success(Unit)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = NewAccountAskViewModel(store, signedInUser, deletion, session)

    @Test
    fun asksOnlyWhenPendingForThisUid() = runTest(dispatcher) {
        assertFalse(viewModel().ask.value, "nothing marked: no question")

        store.pending("someone-else")
        assertFalse(viewModel().ask.value, "another account's mark is not this one's")

        store.pending("new-uid")
        assertTrue(viewModel().ask.value)

        every { signedInUser.getCurrentUid() } returns null
        assertFalse(viewModel().ask.value, "nobody signed in: no question")
    }

    @Test
    fun keepNewAccountClearsTheQuestion() = runTest(dispatcher) {
        store.pending("new-uid")
        val vm = viewModel()

        vm.keepNewAccount()

        assertFalse(vm.ask.value)
        assertFalse(store.isPending("new-uid"))
        assertFalse(viewModel().ask.value, "answered once, never asked again")
    }

    @Test
    fun signOutAndRetryDeletesThenSignsOut() = runTest(dispatcher) {
        store.pending("new-uid")
        coEvery { deletion.deleteAccount() } returns Result.success(Unit)
        val vm = viewModel()

        vm.signOutAndRetry()
        advanceUntilIdle()

        coVerifyOrder {
            deletion.deleteAccount()
            session.signOutCompletely()
        }
        assertTrue(vm.signedOut.value)
        assertFalse(vm.working.value)
        assertFalse(store.isPending("new-uid"))
    }

    @Test
    fun signOutAndRetryKeepsQuestionWhenDeletionFails() = runTest(dispatcher) {
        store.pending("new-uid")
        coEvery { deletion.deleteAccount() } returns Result.failure(IOException("offline"))
        val vm = viewModel()
        val errors = mutableListOf<UiText>()
        val collecting = launch { vm.errors.collect { errors += it } }
        advanceUntilIdle()

        vm.signOutAndRetry()
        advanceUntilIdle()

        coVerify(exactly = 0) { session.signOutCompletely() }
        assertTrue(vm.ask.value, "the question stays, to be answered again")
        assertFalse(vm.signedOut.value)
        assertFalse(vm.working.value)
        assertTrue(store.isPending("new-uid"))
        assertEquals(listOf<UiText>(UiText.Res(Res.string.new_account_ask_retry_failed)), errors)
        collecting.cancel()
    }

    @Test
    fun markingRunsTheAccountSwitchWipeFirst() = runTest(dispatcher) {
        // The wipe clears the preferences when a different account signs in; run after the mark,
        // it would erase it.
        var markedBeforeWipe: Boolean? = null
        coEvery { consistency.ensureAccountConsistency() } answers {
            markedBeforeWipe = store.isPending("new-uid")
        }

        store.pending("new-uid")

        assertEquals(false, markedBeforeWipe, "the wipe must run before the mark is written")
        assertTrue(store.isPending("new-uid"))
    }

    /** The sealed store's text, held in memory, as a phone's file or Keychain item holds it. */
    private class MemoryText : SealedTextStore {
        private var text: String? = null

        override fun read(): StoredText = text?.let(StoredText::Present) ?: StoredText.Absent

        override fun write(text: String) {
            this.text = text
        }

        override fun delete() {
            text = null
        }
    }
}
