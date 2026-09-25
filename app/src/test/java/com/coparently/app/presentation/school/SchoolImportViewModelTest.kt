package com.coparently.app.presentation.school

import com.coparently.app.R
import com.coparently.app.data.school.SchoolAccounts
import com.coparently.app.data.school.SchoolImporter
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolSyncFailure
import com.coparently.app.domain.school.SchoolSyncResult
import com.coparently.app.presentation.common.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The school-import list (MON-8): who each connection is for, "Update now" and "Disconnect". */
@OptIn(ExperimentalCoroutinesApi::class)
class SchoolImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = mockk<SchoolAccounts>(relaxed = true)
    private val importer = mockk<SchoolImporter>()
    private val children = mockk<ChildInfoRepository>()

    private val connection = SchoolConnection(
        id = "conn1",
        baseUrl = "https://skola.bakalari.cz",
        username = "novak",
        schoolName = "ZŠ Beroun",
        studentName = "Nováková Anna, 5.A",
        childId = "anna",
        familyId = "alice__bob",
        status = SchoolConnectionStatus.OK,
        lastSuccessAtMillis = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { accounts.observe() } returns flowOf(listOf(connection))
        every { children.getAllChildInfo() } returns flowOf(listOf(child("anna", "Anička")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a connection is named after the family's child`() = runTest(dispatcher) {
        val vm = SchoolImportViewModel(accounts, importer, children)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals("Anička", vm.state.value.rows.single().childName)
    }

    @Test
    fun `a child record that is gone falls back to the school's name for the child`() = runTest(dispatcher) {
        every { children.getAllChildInfo() } returns flowOf(emptyList())
        val vm = SchoolImportViewModel(accounts, importer, children)
        advanceUntilIdle()

        assertEquals("Nováková Anna, 5.A", vm.state.value.rows.single().childName)
    }

    @Test
    fun `update now runs once, says it is running, and words the outcome`() = runTest(dispatcher) {
        val gate = CompletableDeferred<SchoolSyncResult>()
        coEvery { importer.sync("conn1") } coAnswers { gate.await() }
        val vm = SchoolImportViewModel(accounts, importer, children)
        advanceUntilIdle()

        vm.updateNow("conn1")
        vm.updateNow("conn1")
        advanceUntilIdle()
        assertTrue("conn1" in vm.state.value.updating)

        gate.complete(SchoolSyncResult.Success(created = 3, updated = 0, deleted = 0))
        advanceUntilIdle()

        coVerify(exactly = 1) { importer.sync("conn1") }
        assertTrue(vm.state.value.updating.isEmpty())
        assertEquals(UiText.Res(R.string.school_import_sync_done), vm.state.value.message)
    }

    @Test
    fun `a refused sign-in and a network failure get their own sentences`() = runTest(dispatcher) {
        coEvery { importer.sync("conn1") } returnsMany listOf(
            SchoolSyncResult.NeedsPassword,
            SchoolSyncResult.Failed(SchoolSyncFailure.NETWORK)
        )
        val vm = SchoolImportViewModel(accounts, importer, children)
        advanceUntilIdle()

        vm.updateNow("conn1")
        advanceUntilIdle()
        assertEquals(UiText.Res(R.string.school_import_sync_needs_password), vm.state.value.message)
        vm.messageShown()

        vm.updateNow("conn1")
        advanceUntilIdle()
        assertEquals(UiText.Res(R.string.school_import_sync_network), vm.state.value.message)
    }

    @Test
    fun `disconnect forgets the connection and says so`() = runTest(dispatcher) {
        val vm = SchoolImportViewModel(accounts, importer, children)
        advanceUntilIdle()

        vm.disconnect("conn1")
        advanceUntilIdle()

        coVerify { accounts.disconnect("conn1") }
        assertEquals(UiText.Res(R.string.school_import_disconnected), vm.state.value.message)
    }

    private fun child(id: String, name: String) = ChildInfo(
        id = id,
        childName = name,
        dateOfBirth = null,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0)
    )
}
