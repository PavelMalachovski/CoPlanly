package com.coparently.app.presentation.school

import com.coparently.app.R
import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.FamilyViewSource
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
    private val view = MutableStateFlow(FamilyView(me = "alice", onScreen = "alice__bob", others = emptySet()))
    private val familyViewSource = mockk<FamilyViewSource> { every { observe() } returns view }

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
        every { children.getAllChildInfoInEveryFamily() } returns flowOf(listOf(child("anna", "Anička")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a connection is named after the family's child`() = runTest(dispatcher) {
        val vm = SchoolImportViewModel(accounts, importer, children, familyViewSource)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals("Anička", vm.state.value.rows.single().childName)
    }

    @Test
    fun `a child record that is gone falls back to the school's name for the child`() = runTest(dispatcher) {
        every { children.getAllChildInfoInEveryFamily() } returns flowOf(emptyList())
        val vm = SchoolImportViewModel(accounts, importer, children, familyViewSource)
        advanceUntilIdle()

        assertEquals("Nováková Anna, 5.A", vm.state.value.rows.single().childName)
    }

    @Test
    fun `update now runs once, says it is running, and words the outcome`() = runTest(dispatcher) {
        val gate = CompletableDeferred<SchoolSyncResult>()
        coEvery { importer.sync("conn1") } coAnswers { gate.await() }
        val vm = SchoolImportViewModel(accounts, importer, children, familyViewSource)
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
        val vm = SchoolImportViewModel(accounts, importer, children, familyViewSource)
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
        val vm = SchoolImportViewModel(accounts, importer, children, familyViewSource)
        advanceUntilIdle()

        vm.disconnect("conn1")
        advanceUntilIdle()

        coVerify { accounts.disconnect("conn1") }
        assertEquals(UiText.Res(R.string.school_import_disconnected), vm.state.value.message)
    }

    // ---- two families (isolation audit, October 2026) --------------------------------------

    @Test
    fun `only the family on screen's connections and those of no family are listed, following a switch`() =
        runTest(dispatcher) {
            val bobs = connection.copy(id = "bob", familyId = "alice__bob")
            val carols = connection.copy(id = "carol", familyId = "alice__carol")
            val nobodys = connection.copy(id = "none", familyId = null)
            every { accounts.observe() } returns flowOf(listOf(bobs, carols, nobodys))
            view.value = FamilyView(me = "alice", onScreen = "alice__bob", others = setOf("alice__carol"))
            val vm = SchoolImportViewModel(accounts, importer, children, familyViewSource)
            advanceUntilIdle()
            assertEquals(listOf("bob", "none"), vm.state.value.rows.map { it.connection.id })

            view.value = FamilyView(me = "alice", onScreen = "alice__carol", others = setOf("alice__bob"))
            advanceUntilIdle()

            assertEquals(listOf("carol", "none"), vm.state.value.rows.map { it.connection.id })
        }

    @Test
    fun `a connection of no family says it needs one only when nothing names one`() = runTest(dispatcher) {
        val unstamped = connection.copy(id = "none", familyId = null, childId = "anna")
        val stampedChild = connection.copy(id = "leo", familyId = null, childId = "leo")
        every { accounts.observe() } returns flowOf(listOf(unstamped, stampedChild))
        every { children.getAllChildInfoInEveryFamily() } returns
            flowOf(listOf(child("anna", "Anička"), child("leo", "Leo", familyId = "alice__bob")))
        view.value = FamilyView(me = "alice", onScreen = "alice__bob", others = setOf("alice__carol"))
        val vm = SchoolImportViewModel(accounts, importer, children, familyViewSource)
        advanceUntilIdle()
        assertEquals(listOf(true, false), vm.state.value.rows.map { it.needsFamily })

        // With one family the importer takes it: nothing to choose.
        view.value = FamilyView(me = "alice", onScreen = "alice__bob", others = emptySet())
        advanceUntilIdle()
        assertEquals(listOf(false, false), vm.state.value.rows.map { it.needsFamily })
    }

    @Test
    fun `an update that found no family says how to choose one`() = runTest(dispatcher) {
        coEvery { importer.sync("conn1") } returns SchoolSyncResult.NeedsFamily
        val vm = SchoolImportViewModel(accounts, importer, children, familyViewSource)
        advanceUntilIdle()

        vm.updateNow("conn1")
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.school_import_sync_needs_family), vm.state.value.message)
    }

    private fun child(id: String, name: String, familyId: String? = null) = ChildInfo(
        id = id,
        childName = name,
        dateOfBirth = null,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        familyId = familyId
    )
}
