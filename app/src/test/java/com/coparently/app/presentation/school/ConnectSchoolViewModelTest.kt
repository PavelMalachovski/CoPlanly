package com.coparently.app.presentation.school

import androidx.lifecycle.SavedStateHandle
import com.coparently.app.R
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.school.SchoolAccounts
import com.coparently.app.data.school.SchoolImporter
import com.coparently.app.data.school.SchoolSignIn
import com.coparently.app.data.school.bakalari.BakalariAccount
import com.coparently.app.data.school.bakalari.BakalariException
import com.coparently.app.data.school.bakalari.BakalariSchoolDirectory
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.data.school.bakalari.DirectorySchool
import com.coparently.app.data.school.bakalari.SchoolTown
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolStudent
import com.coparently.app.domain.school.SchoolSyncResult
import com.coparently.app.presentation.common.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Connecting a school (MON-8): the directory, the sign-in with a password used once, and the
 * choices nobody makes for the parent — which child, and which family at two or more.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectSchoolViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = mockk<SchoolAccounts>()
    private val directory = mockk<BakalariSchoolDirectory>()
    private val importer = mockk<SchoolImporter>()
    private val families = mockk<SelectedFamilySource>()
    private val children = mockk<ChildInfoRepository>()

    private val school = DirectorySchool("ZŠ Beroun - Závodí", "https://zavodi.bakalari.cz")
    private val bobFamily = FamilyOption("alice__bob", "bob", "Bob")
    private val carolFamily = FamilyOption("alice__carol", "carol", "Carol")
    private val signIn = SchoolSignIn(
        baseUrl = school.baseUrl,
        username = "novak",
        tokens = BakalariTokens("a", "r", 1L),
        account = BakalariAccount(
            student = SchoolStudent("1234/x", "Nováková Anna, 5.A", "XL"),
            className = "5.A",
            schoolName = "ZŠ Beroun",
            canReadTimetable = true,
            canReadEvents = true
        )
    )
    private val saved = SchoolConnection(
        id = "conn1",
        baseUrl = school.baseUrl,
        username = "novak",
        schoolName = school.name,
        studentName = "Nováková Anna, 5.A",
        childId = "anna",
        familyId = "alice__bob",
        status = SchoolConnectionStatus.OK,
        lastSuccessAtMillis = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { directory.towns() } returns listOf(SchoolTown("Beroun", 3), SchoolTown("Brno", 90))
        coEvery { directory.schools("Beroun") } returns listOf(school)
        coEvery { families.namedFamilies() } returns listOf(bobFamily)
        every { children.getAllChildInfo() } returns flowOf(listOf(child("anna", "Anna", "alice__bob")))
        coEvery { accounts.signIn(school.baseUrl, "novak", "heslo") } returns signIn
        coEvery { accounts.connect(any(), any(), any(), any()) } returns saved
        coEvery { importer.sync(any()) } returns SchoolSyncResult.Success(1, 0, 0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `towns load and filter as the parent types`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTownQuery("ber")

        assertEquals(listOf("Beroun"), vm.state.value.visibleTowns.map { it.name })
    }

    @Test
    fun `a town's schools lead to the sign-in for the school chosen`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.chooseTown("Beroun")
        advanceUntilIdle()
        assertEquals(listOf(school), vm.state.value.schools)

        vm.chooseSchool(school)
        assertEquals(ConnectStep.CREDENTIALS, vm.state.value.step)
        assertEquals(school.baseUrl, vm.state.value.baseUrl)
    }

    @Test
    fun `with one family and one child both are chosen, and the account is shown`() = runTest(dispatcher) {
        val vm = signedIn()

        val state = vm.state.value
        assertEquals(ConnectStep.CHOOSE, state.step)
        assertEquals("Nováková Anna, 5.A", state.account?.displayName)
        assertFalse(state.choosesFamily)
        assertEquals("alice__bob", state.familyId)
        assertEquals("anna", state.childId)
        assertTrue(state.canContinue)
    }

    @Test
    fun `with several children none is chosen for the parent`() = runTest(dispatcher) {
        every { children.getAllChildInfo() } returns flowOf(
            listOf(child("anna", "Anna", "alice__bob"), child("leo", "Leo", null))
        )
        val vm = signedIn()

        assertEquals(listOf("Anna", "Leo"), vm.state.value.children.map { it.name })
        assertNull(vm.state.value.childId)
        assertFalse(vm.state.value.canContinue)

        vm.chooseChild("leo")
        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun `with two families the parent chooses one, and sees only its children`() = runTest(dispatcher) {
        coEvery { families.namedFamilies() } returns listOf(bobFamily, carolFamily)
        every { children.getAllChildInfo() } returns flowOf(
            listOf(child("anna", "Anna", "alice__bob"), child("max", "Max", "alice__carol"))
        )
        val vm = signedIn()

        assertTrue(vm.state.value.choosesFamily)
        assertNull(vm.state.value.familyId)
        assertTrue(vm.state.value.children.isEmpty())

        vm.chooseFamily("alice__carol")
        advanceUntilIdle()

        assertEquals(listOf("Max"), vm.state.value.children.map { it.name })
        assertEquals("max", vm.state.value.childId)
        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun `a family with no child records offers nothing`() = runTest(dispatcher) {
        every { children.getAllChildInfo() } returns flowOf(emptyList())
        val vm = signedIn()

        assertTrue(vm.state.value.children.isEmpty())
        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun `an account with neither right cannot continue`() = runTest(dispatcher) {
        coEvery { accounts.signIn(any(), any(), any()) } returns signIn.copy(
            account = signIn.account.copy(canReadTimetable = false, canReadEvents = false)
        )
        val vm = signedIn()

        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun `a wrong password is worded and keeps the parent on the sign-in`() = runTest(dispatcher) {
        coEvery { accounts.signIn(any(), any(), any()) } throws BakalariException.InvalidGrant()
        val vm = signedIn()

        assertEquals(ConnectStep.CREDENTIALS, vm.state.value.step)
        assertEquals(UiText.Res(R.string.school_connect_wrong_password), vm.state.value.error)
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun `connecting saves the choices and runs the first import`() = runTest(dispatcher) {
        val vm = signedIn()

        vm.toConfirm()
        assertEquals(ConnectStep.CONFIRM, vm.state.value.step)
        vm.connect()
        advanceUntilIdle()

        coVerify { accounts.connect(signIn, school.name, "anna", "alice__bob") }
        coVerify { importer.sync("conn1") }
        assertTrue(vm.state.value.done)
    }

    @Test
    fun `a typed address must be https and answer as Bakalari`() = runTest(dispatcher) {
        coEvery { accounts.checkServer("https://skola.example.cz") } returns Unit
        coEvery { accounts.checkServer("https://nothing.example.cz") } throws BakalariException.NotBakalari()
        val vm = viewModel()
        advanceUntilIdle()
        vm.enterManually()

        vm.onManualUrl("http://skola.example.cz")
        vm.checkManualUrl()
        assertEquals(UiText.Res(R.string.school_connect_url_invalid), vm.state.value.error)

        vm.onManualUrl("nothing.example.cz")
        vm.checkManualUrl()
        advanceUntilIdle()
        assertEquals(UiText.Res(R.string.school_connect_url_not_bakalari), vm.state.value.error)

        vm.onManualUrl("skola.example.cz/login")
        vm.checkManualUrl()
        advanceUntilIdle()
        assertEquals(ConnectStep.CREDENTIALS, vm.state.value.step)
        assertEquals("https://skola.example.cz", vm.state.value.baseUrl)
    }

    @Test
    fun `signing in again asks for the password only, then updates`() = runTest(dispatcher) {
        coEvery { accounts.connection("conn1") } returns saved.copy(status = SchoolConnectionStatus.NEEDS_PASSWORD)
        coEvery { accounts.reconnect("conn1", "nove-heslo") } returns saved
        val vm = viewModel(connectionId = "conn1")
        advanceUntilIdle()

        assertTrue(vm.state.value.reconnecting)
        assertEquals(ConnectStep.CREDENTIALS, vm.state.value.step)
        assertEquals("novak", vm.state.value.username)
        coVerify(exactly = 0) { directory.towns() }

        vm.signIn("nove-heslo")
        advanceUntilIdle()

        coVerify { accounts.reconnect("conn1", "nove-heslo") }
        coVerify { importer.sync("conn1") }
        assertTrue(vm.state.value.done)
    }

    @Test
    fun `back walks the steps and drops a sign-in that was not saved`() = runTest(dispatcher) {
        val vm = signedIn()

        assertTrue(vm.back())
        assertEquals(ConnectStep.CREDENTIALS, vm.state.value.step)
        assertTrue(vm.back())
        assertEquals(ConnectStep.SCHOOL, vm.state.value.step)
        assertTrue(vm.back())
        assertEquals(ConnectStep.TOWN, vm.state.value.step)
        assertFalse(vm.back())

        vm.connect()
        advanceUntilIdle()
        coVerify(exactly = 0) { accounts.connect(any(), any(), any(), any()) }
    }

    private fun viewModel(connectionId: String? = null) = ConnectSchoolViewModel(
        savedStateHandle = SavedStateHandle(
            connectionId?.let { mapOf(ConnectSchoolViewModel.ARG_CONNECTION_ID to it) }.orEmpty()
        ),
        accounts = accounts,
        directory = directory,
        importer = importer,
        selectedFamilySource = families,
        childInfoRepository = children
    )

    private fun TestScope.signedIn(): ConnectSchoolViewModel {
        val vm = viewModel()
        advanceUntilIdle()
        vm.chooseTown("Beroun")
        advanceUntilIdle()
        vm.chooseSchool(school)
        vm.onUsername("novak")
        vm.signIn("heslo")
        advanceUntilIdle()
        return vm
    }

    private fun child(id: String, name: String, familyId: String?) = ChildInfo(
        id = id,
        childName = name,
        dateOfBirth = null,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        familyId = familyId
    )
}
