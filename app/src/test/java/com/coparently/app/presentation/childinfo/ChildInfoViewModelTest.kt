package com.coparently.app.presentation.childinfo

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.repository.ChildInfoRepository
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for ChildInfoViewModel.
 * Tests child information management operations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChildInfoViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var childInfoRepository: ChildInfoRepository
    private lateinit var firebaseAuthService: FirebaseAuthService
    private lateinit var viewModel: ChildInfoViewModel

    private val mockUser = mockk<FirebaseUser> {
        every { uid } returns "test_uid"
        every { email } returns "test@example.com"
    }

    private val testChildInfo1 = ChildInfo(
        id = "1",
        childName = "Test Child 1",
        dateOfBirth = LocalDateTime.now().minusYears(5),
        medications = emptyList(),
        activities = emptyList(),
        allergies = emptyList(),
        medicalNotes = null,
        emergencyContacts = emptyList(),
        schoolInfo = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        createdByFirebaseUid = "test_uid",
        lastModifiedBy = "test_uid",
        syncedToFirestore = false
    )

    private val testChildInfo2 = ChildInfo(
        id = "2",
        childName = "Test Child 2",
        dateOfBirth = LocalDateTime.now().minusYears(3),
        medications = emptyList(),
        activities = emptyList(),
        allergies = emptyList(),
        medicalNotes = null,
        emergencyContacts = emptyList(),
        schoolInfo = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        createdByFirebaseUid = "test_uid",
        lastModifiedBy = "test_uid",
        syncedToFirestore = false
    )

    private val testChildInfoList = listOf(testChildInfo1, testChildInfo2)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        childInfoRepository = mockk()
        firebaseAuthService = mockk()

        every { firebaseAuthService.getCurrentUser() } returns mockUser
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `init loads child info successfully`() = runTest {
        // Given
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(testChildInfoList)

        // When
        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<ChildInfoUiState.Success>(state)
            assertEquals(testChildInfoList, state.childInfoList)
        }

        viewModel.currentChildInfo.test {
            val childInfo = awaitItem()
            assertEquals(testChildInfo1, childInfo)
        }

        coVerify { childInfoRepository.getAllChildInfo() }
    }

    @Test
    fun `init handles empty child info list`() = runTest {
        // Given
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(emptyList())

        // When
        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<ChildInfoUiState.Success>(state)
            assertEquals(emptyList(), state.childInfoList)
        }

        viewModel.currentChildInfo.test {
            val childInfo = awaitItem()
            assertNull(childInfo)
        }
    }

    @Test
    fun `init shows loading state initially`() = runTest {
        // Given
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(testChildInfoList)

        // When
        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)

        // Then - should be loading before advancing
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<ChildInfoUiState.Loading>(state)
        }
    }

    @Test
    fun `loadChildInfo handles error`() = runTest {
        // Given
        val errorMessage = "Database error"
        coEvery { childInfoRepository.getAllChildInfo() } throws Exception(errorMessage)

        // When
        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<ChildInfoUiState.Error>(state)
            assertEquals(errorMessage, state.message)
        }
    }

    @Test
    fun `loadChildInfoById observes specific child info`() = runTest {
        // Given
        val childId = "1"
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(testChildInfoList)
        coEvery { childInfoRepository.observeChildInfoById(childId) } returns flowOf(testChildInfo1)

        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.loadChildInfoById(childId)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.currentChildInfo.test {
            val childInfo = awaitItem()
            assertEquals(testChildInfo1, childInfo)
        }

        coVerify { childInfoRepository.observeChildInfoById(childId) }
    }

    @Test
    fun `upsertChildInfo creates new child info`() = runTest {
        // Given
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(emptyList())
        coEvery { childInfoRepository.upsertChildInfo(any()) } just Runs

        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.upsertChildInfo(
            id = null,
            childName = "New Child",
            dateOfBirth = LocalDateTime.now().minusYears(2),
            medications = emptyList(),
            activities = emptyList(),
            allergies = emptyList(),
            medicalNotes = null,
            emergencyContacts = emptyList(),
            schoolInfo = null
        )
        testScheduler.advanceUntilIdle()

        // Then
        coVerify {
            childInfoRepository.upsertChildInfo(match {
                it.childName == "New Child" &&
                it.id.isNotEmpty() &&
                it.createdByFirebaseUid == "test_uid" &&
                it.lastModifiedBy == "test_uid" &&
                it.syncedToFirestore == false
            })
        }
    }

    @Test
    fun `upsertChildInfo updates existing child info`() = runTest {
        // Given
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(listOf(testChildInfo1))
        coEvery { childInfoRepository.upsertChildInfo(any()) } just Runs

        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.upsertChildInfo(
            id = testChildInfo1.id,
            childName = "Updated Child Name",
            dateOfBirth = testChildInfo1.dateOfBirth,
            medications = emptyList(),
            activities = emptyList(),
            allergies = emptyList(),
            medicalNotes = null,
            emergencyContacts = emptyList(),
            schoolInfo = null
        )
        testScheduler.advanceUntilIdle()

        // Then
        coVerify {
            childInfoRepository.upsertChildInfo(match {
                it.id == testChildInfo1.id &&
                it.childName == "Updated Child Name" &&
                it.lastModifiedBy == "test_uid"
            })
        }
    }

    @Test
    fun `upsertChildInfo handles unauthenticated user`() = runTest {
        // Given
        every { firebaseAuthService.getCurrentUser() } returns null
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(emptyList())

        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.upsertChildInfo(
            id = null,
            childName = "New Child",
            dateOfBirth = LocalDateTime.now(),
            medications = emptyList(),
            activities = emptyList(),
            allergies = emptyList(),
            medicalNotes = null,
            emergencyContacts = emptyList(),
            schoolInfo = null
        )
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<ChildInfoUiState.Error>(state)
            assertEquals("User not authenticated", state.message)
        }

        coVerify(exactly = 0) { childInfoRepository.upsertChildInfo(any()) }
    }

    @Test
    fun `upsertChildInfo handles repository error`() = runTest {
        // Given
        val errorMessage = "Save failed"
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(emptyList())
        coEvery { childInfoRepository.upsertChildInfo(any()) } throws Exception(errorMessage)

        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.upsertChildInfo(
            id = null,
            childName = "New Child",
            dateOfBirth = LocalDateTime.now(),
            medications = emptyList(),
            activities = emptyList(),
            allergies = emptyList(),
            medicalNotes = null,
            emergencyContacts = emptyList(),
            schoolInfo = null
        )
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<ChildInfoUiState.Error>(state)
            assertEquals(errorMessage, state.message)
        }
    }

    @Test
    fun `deleteChildInfo deletes child info successfully`() = runTest {
        // Given
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(testChildInfoList)
        coEvery { childInfoRepository.deleteChildInfo(any()) } just Runs

        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.deleteChildInfo(testChildInfo1)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { childInfoRepository.deleteChildInfo(testChildInfo1) }
        // loadChildInfo is called again after delete
        coVerify(atLeast = 2) { childInfoRepository.getAllChildInfo() }
    }

    @Test
    fun `deleteChildInfo handles error`() = runTest {
        // Given
        val errorMessage = "Delete failed"
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(testChildInfoList)
        coEvery { childInfoRepository.deleteChildInfo(any()) } throws Exception(errorMessage)

        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.deleteChildInfo(testChildInfo1)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<ChildInfoUiState.Error>(state)
            assertEquals(errorMessage, state.message)
        }
    }

    @Test
    fun `syncChildInfo syncs with Firestore successfully`() = runTest {
        // Given
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(testChildInfoList)
        coEvery { childInfoRepository.syncWithFirestore() } just Runs

        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.syncChildInfo()
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { childInfoRepository.syncWithFirestore() }
    }

    @Test
    fun `syncChildInfo handles sync error`() = runTest {
        // Given
        val errorMessage = "Sync failed"
        coEvery { childInfoRepository.getAllChildInfo() } returns flowOf(testChildInfoList)
        coEvery { childInfoRepository.syncWithFirestore() } throws Exception(errorMessage)

        viewModel = ChildInfoViewModel(childInfoRepository, firebaseAuthService)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.syncChildInfo()
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<ChildInfoUiState.Error>(state)
            assertEquals(errorMessage, state.message)
        }
    }
}

