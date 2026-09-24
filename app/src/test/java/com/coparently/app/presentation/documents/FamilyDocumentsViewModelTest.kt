package com.coparently.app.presentation.documents

import com.coparently.app.R
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.files.SharedFileIntegrityException
import com.coparently.app.data.files.SharedFileRejectedException
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.documents.VaultListing
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.domain.repository.FamilyDocumentRepository
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

/**
 * The vault screen (MON-23): which family it files into, what it says when it cannot show a list,
 * and which sentence a failure gets.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FamilyDocumentsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<FamilyDocumentRepository>()
    private val documents = MutableStateFlow<VaultListing?>(VaultListing(emptyList()))
    private val selectedFamilySource = mockk<SelectedFamilySource> {
        coEvery { selected() } returns FamilyOption(FAMILY, "bob")
    }
    private val authService = mockk<FirebaseAuthService> {
        every { getCurrentUser() } returns mockk<FirebaseUser> { every { uid } returns "alice" }
    }
    private val parentsSource = mockk<ParentsSource> { every { observe() } returns flowOf(Parents()) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.observe(FAMILY) } returns documents
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = FamilyDocumentsViewModel(repository, selectedFamilySource, authService, parentsSource)

    @Test
    fun `lists the documents of the family on screen`() = runTest(dispatcher) {
        documents.value = VaultListing(listOf(DOCUMENT))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DocumentsList.Loaded(listOf(DOCUMENT)), vm.state.value.list)
        assertEquals("alice", vm.state.value.myUid)
    }

    @Test
    fun `a cached list reaches the screen marked as possibly out of date`() = runTest(dispatcher) {
        documents.value = VaultListing(listOf(DOCUMENT), possiblyOutdated = true)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DocumentsList.Loaded(listOf(DOCUMENT), possiblyOutdated = true), vm.state.value.list)

        documents.value = VaultListing(listOf(DOCUMENT))
        advanceUntilIdle()
        assertEquals(DocumentsList.Loaded(listOf(DOCUMENT)), vm.state.value.list)
    }

    @Test
    fun `an unreadable vault says so rather than looking empty`() = runTest(dispatcher) {
        documents.value = null
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DocumentsList.Unavailable, vm.state.value.list)
    }

    @Test
    fun `without a co-parent there is no vault to file into`() = runTest(dispatcher) {
        coEvery { selectedFamilySource.selected() } returns null
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DocumentsList.NoFamily, vm.state.value.list)
        vm.add("Order", DocumentCategory.COURT_ORDER, "content://x")
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.add(any(), any(), any(), any()) }
    }

    @Test
    fun `an upload goes to the family on screen and a refused type is worded by the policy`() = runTest(dispatcher) {
        coEvery { repository.add(FAMILY, "Order", DocumentCategory.COURT_ORDER, "content://x") } returns
            Result.failure(SharedFileRejectedException(SharedFilePolicy.Rejection.TYPE))
        val vm = viewModel()
        advanceUntilIdle()

        vm.add("Order", DocumentCategory.COURT_ORDER, "content://x")
        advanceUntilIdle()

        assertFalse(vm.state.value.uploading)
        assertEquals(UiText.Res(R.string.shared_file_error_type), vm.state.value.error)
    }

    @Test
    fun `a download that does not match its digest is never opened`() = runTest(dispatcher) {
        coEvery { repository.localCopy(DOCUMENT) } returns Result.failure(SharedFileIntegrityException())
        val vm = viewModel()
        advanceUntilIdle()

        vm.open(DOCUMENT)
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.shared_file_error_integrity), vm.state.value.error)
    }

    private companion object {
        const val FAMILY = "alice__bob"
        val DOCUMENT = FamilyDocument(
            id = "doc-1",
            familyId = FAMILY,
            createdByFirebaseUid = "alice",
            title = "Custody order",
            category = DocumentCategory.COURT_ORDER,
            fileName = "order.pdf",
            storagePath = "family_documents/$FAMILY/doc-1/order.pdf",
            contentType = "application/pdf",
            sizeBytes = 10,
            sha256 = "a".repeat(64),
            createdAtMillis = 1
        )
    }
}
