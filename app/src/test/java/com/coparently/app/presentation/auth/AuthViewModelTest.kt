package com.coparently.app.presentation.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for AuthViewModel.
 * Tests authentication flows including sign-in and sign-up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var firebaseAuthService: FirebaseAuthService
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        firebaseAuthService = mockk()
        viewModel = AuthViewModel(firebaseAuthService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state is correct`() = runTest {
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("", state.email)
            assertEquals("", state.password)
            assertTrue(state.isSignInMode)
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
        }
    }

    @Test
    fun `updateEmail updates email and clears error`() = runTest {
        // Given
        val testEmail = "test@example.com"

        // When
        viewModel.updateEmail(testEmail)

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testEmail, state.email)
            assertNull(state.errorMessage)
        }
    }

    @Test
    fun `updatePassword updates password and clears error`() = runTest {
        // Given
        val testPassword = "password123"

        // When
        viewModel.updatePassword(testPassword)

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testPassword, state.password)
            assertNull(state.errorMessage)
        }
    }

    @Test
    fun `toggleSignInMode toggles mode and clears error`() = runTest {
        // When
        viewModel.toggleSignInMode()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isSignInMode)
            assertNull(state.errorMessage)
        }

        // When - toggle again
        viewModel.toggleSignInMode()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isSignInMode)
        }
    }

    @Test
    fun `signIn with empty email shows error`() = runTest {
        // Given
        viewModel.updatePassword("password123")

        // When
        var callbackInvoked = false
        viewModel.signIn { callbackInvoked = true }

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Please fill in all fields", state.errorMessage)
            assertFalse(state.isLoading)
        }
        assertFalse(callbackInvoked)
    }

    @Test
    fun `signIn with empty password shows error`() = runTest {
        // Given
        viewModel.updateEmail("test@example.com")

        // When
        var callbackInvoked = false
        viewModel.signIn { callbackInvoked = true }

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Please fill in all fields", state.errorMessage)
            assertFalse(state.isLoading)
        }
        assertFalse(callbackInvoked)
    }

    @Test
    fun `signIn with valid credentials succeeds`() = runTest {
        // Given
        val testEmail = "test@example.com"
        val testPassword = "password123"
        val mockUser = mockk<FirebaseUser>()

        viewModel.updateEmail(testEmail)
        viewModel.updatePassword(testPassword)

        coEvery {
            firebaseAuthService.signInWithEmail(testEmail, testPassword)
        } returns Result.success(mockUser)

        // When
        var callbackInvoked = false
        viewModel.signIn { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        // Then
        assertTrue(callbackInvoked)
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
        }

        coVerify { firebaseAuthService.signInWithEmail(testEmail, testPassword) }
    }

    @Test
    fun `signIn with invalid credentials shows error`() = runTest {
        // Given
        val testEmail = "test@example.com"
        val testPassword = "wrongpassword"
        val errorMessage = "Invalid credentials"

        viewModel.updateEmail(testEmail)
        viewModel.updatePassword(testPassword)

        coEvery {
            firebaseAuthService.signInWithEmail(testEmail, testPassword)
        } returns Result.failure(Exception(errorMessage))

        // When
        var callbackInvoked = false
        viewModel.signIn { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        // Then
        assertFalse(callbackInvoked)
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(errorMessage, state.errorMessage)
        }

        coVerify { firebaseAuthService.signInWithEmail(testEmail, testPassword) }
    }

    @Test
    fun `signIn shows loading state during authentication`() = runTest {
        // Given
        val testEmail = "test@example.com"
        val testPassword = "password123"
        val mockUser = mockk<FirebaseUser>()

        viewModel.updateEmail(testEmail)
        viewModel.updatePassword(testPassword)

        coEvery {
            firebaseAuthService.signInWithEmail(testEmail, testPassword)
        } coAnswers {
            kotlinx.coroutines.delay(100)
            Result.success(mockUser)
        }

        // When
        viewModel.signIn { }

        // Then - should be loading
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertNull(state.errorMessage)
        }

        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `signUp with empty email shows error`() = runTest {
        // Given
        viewModel.updatePassword("password123")

        // When
        var callbackInvoked = false
        viewModel.signUp { callbackInvoked = true }

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Please fill in all fields", state.errorMessage)
            assertFalse(state.isLoading)
        }
        assertFalse(callbackInvoked)
    }

    @Test
    fun `signUp with empty password shows error`() = runTest {
        // Given
        viewModel.updateEmail("test@example.com")

        // When
        var callbackInvoked = false
        viewModel.signUp { callbackInvoked = true }

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Please fill in all fields", state.errorMessage)
            assertFalse(state.isLoading)
        }
        assertFalse(callbackInvoked)
    }

    @Test
    fun `signUp with valid credentials succeeds`() = runTest {
        // Given
        val testEmail = "newuser@example.com"
        val testPassword = "password123"
        val mockUser = mockk<FirebaseUser>()

        viewModel.updateEmail(testEmail)
        viewModel.updatePassword(testPassword)

        coEvery {
            firebaseAuthService.createAccountWithEmail(testEmail, testPassword)
        } returns Result.success(mockUser)

        // When
        var callbackInvoked = false
        viewModel.signUp { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        // Then
        assertTrue(callbackInvoked)
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
        }

        coVerify { firebaseAuthService.createAccountWithEmail(testEmail, testPassword) }
    }

    @Test
    fun `signUp with existing email shows error`() = runTest {
        // Given
        val testEmail = "existing@example.com"
        val testPassword = "password123"
        val errorMessage = "Email already in use"

        viewModel.updateEmail(testEmail)
        viewModel.updatePassword(testPassword)

        coEvery {
            firebaseAuthService.createAccountWithEmail(testEmail, testPassword)
        } returns Result.failure(Exception(errorMessage))

        // When
        var callbackInvoked = false
        viewModel.signUp { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        // Then
        assertFalse(callbackInvoked)
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(errorMessage, state.errorMessage)
        }

        coVerify { firebaseAuthService.createAccountWithEmail(testEmail, testPassword) }
    }

    @Test
    fun `signUp shows loading state during registration`() = runTest {
        // Given
        val testEmail = "newuser@example.com"
        val testPassword = "password123"
        val mockUser = mockk<FirebaseUser>()

        viewModel.updateEmail(testEmail)
        viewModel.updatePassword(testPassword)

        coEvery {
            firebaseAuthService.createAccountWithEmail(testEmail, testPassword)
        } coAnswers {
            kotlinx.coroutines.delay(100)
            Result.success(mockUser)
        }

        // When
        viewModel.signUp { }

        // Then - should be loading
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertNull(state.errorMessage)
        }

        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `signIn handles exception without message`() = runTest {
        // Given
        val testEmail = "test@example.com"
        val testPassword = "password123"

        viewModel.updateEmail(testEmail)
        viewModel.updatePassword(testPassword)

        coEvery {
            firebaseAuthService.signInWithEmail(testEmail, testPassword)
        } returns Result.failure(Exception())

        // When
        viewModel.signIn { }
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Sign in failed", state.errorMessage)
        }
    }

    @Test
    fun `signUp handles exception without message`() = runTest {
        // Given
        val testEmail = "test@example.com"
        val testPassword = "password123"

        viewModel.updateEmail(testEmail)
        viewModel.updatePassword(testPassword)

        coEvery {
            firebaseAuthService.createAccountWithEmail(testEmail, testPassword)
        } returns Result.failure(Exception())

        // When
        viewModel.signUp { }
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Sign up failed", state.errorMessage)
        }
    }
}

