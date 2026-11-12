package com.coparently.app.presentation.school

import androidx.lifecycle.SavedStateHandle
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.school.EdupageSignIn
import com.coparently.app.data.school.SchoolAccounts
import com.coparently.app.data.school.SchoolImporter
import com.coparently.app.data.school.bakalari.BakalariSchoolDirectory
import com.coparently.app.data.school.edupage.EdupageAccount
import com.coparently.app.data.school.edupage.EdupageChild
import com.coparently.app.data.school.edupage.EdupageException
import com.coparently.app.data.school.edupage.EdupageSession
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolSystem
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.platform.AppBuildValues
import com.coparently.app.resources.Res
import com.coparently.app.resources.school_connect_wrong_password
import com.coparently.app.resources.school_edupage_second_factor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Connecting an EduPage account: where the flow starts, and the choices after the sign-in. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectSchoolEdupageTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = mockk<SchoolAccounts>()
    private val directory = mockk<BakalariSchoolDirectory>(relaxed = true)
    private val importer = mockk<SchoolImporter>(relaxed = true)
    private val families = mockk<SelectedFamilySource>()
    private val children = mockk<ChildInfoRepository>()

    private val anna = EdupageChild("-1", "Anna Nováková", "5.A")
    private val petr = EdupageChild("-2", "Petr Novák", "3.B")
    private val session = EdupageSession("zsx.edupage.org", emptyMap(), "", JsonObject(emptyMap()))

    private fun signIn(vararg pupils: EdupageChild) = EdupageSignIn(
        session,
        "novak",
        "pw",
        EdupageAccount("ZŠ Beroun", "Novák Petr", true, pupils.toList(), 2026)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { families.namedFamilies() } returns listOf(FamilyOption("alice__bob", "bob", "Bob"))
        every { children.getAllChildInfoInEveryFamily() } returns flowOf(
            listOf(
                ChildInfo(
                    id = "c1",
                    childName = "Anna",
                    dateOfBirth = null,
                    createdAt = LocalDateTime(2026, 1, 1, 0, 0),
                    updatedAt = LocalDateTime(2026, 1, 1, 0, 0),
                    familyId = "alice__bob"
                )
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(edupage: Boolean) = ConnectSchoolViewModel(
        savedStateHandle = SavedStateHandle(),
        accounts = accounts,
        directory = directory,
        importer = importer,
        selectedFamilySource = families,
        childInfoRepository = children,
        buildValues = AppBuildValues("1", null, null, null, pairingWebLink = false, edupageImport = edupage)
    )

    private fun signInToEdupage(vm: ConnectSchoolViewModel) {
        vm.chooseSystem(SchoolSystem.EDUPAGE)
        vm.onUsername("novak")
        vm.signIn("pw")
    }

    @Test
    fun `signing an edupage connection in again asks for the password only and finishes through reconnectEdupage`() =
        runTest(dispatcher) {
            val saved = SchoolConnection(
                id = "ep1",
                baseUrl = "https://zsx.edupage.org",
                username = "novak",
                schoolName = "ZŠ Beroun",
                studentName = "Anna",
                childId = "c1",
                familyId = "alice__bob",
                status = SchoolConnectionStatus.NEEDS_PASSWORD,
                lastSuccessAtMillis = null,
                system = SchoolSystem.EDUPAGE
            )
            coEvery { accounts.connection("ep1") } returns saved
            val reconnected = saved.copy(status = SchoolConnectionStatus.OK)
            coEvery { accounts.reconnectEdupage("ep1", "new-pw") } returns reconnected
            val vm = ConnectSchoolViewModel(
                savedStateHandle = SavedStateHandle(mapOf("connectionId" to "ep1")),
                accounts = accounts,
                directory = directory,
                importer = importer,
                selectedFamilySource = families,
                childInfoRepository = children,
                buildValues = AppBuildValues("1", null, null, null, pairingWebLink = false, edupageImport = true)
            )
            advanceUntilIdle()

            assertTrue(vm.state.value.reconnecting)
            assertEquals(ConnectStep.EDUPAGE_CREDENTIALS, vm.state.value.step)
            assertEquals("novak", vm.state.value.username)

            vm.signIn("new-pw")
            advanceUntilIdle()

            coVerify { accounts.reconnectEdupage("ep1", "new-pw") }
            coVerify(exactly = 0) { accounts.reconnect(any(), any()) }
            coVerify { importer.sync("ep1") }
            assertTrue(vm.state.value.done)
        }

    @Test
    fun `with the flag off the flow starts on the town as today`() = runTest(dispatcher) {
        val vm = viewModel(edupage = false)
        advanceUntilIdle()

        assertEquals(ConnectStep.TOWN, vm.state.value.step)
    }

    @Test
    fun `with the flag on the flow starts on the system choice`() = runTest(dispatcher) {
        val vm = viewModel(edupage = true)
        advanceUntilIdle()

        assertEquals(ConnectStep.SYSTEM, vm.state.value.step)
        vm.chooseSystem(SchoolSystem.EDUPAGE)
        assertEquals(ConnectStep.EDUPAGE_CREDENTIALS, vm.state.value.step)
    }

    @Test
    fun `an edupage sign-in with two children asks which child`() = runTest(dispatcher) {
        coEvery { accounts.signInEdupage("", "novak", "pw") } returns signIn(anna, petr)
        val vm = viewModel(edupage = true)

        signInToEdupage(vm)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ConnectStep.EDUPAGE_CHILD, state.step)
        assertEquals(listOf(anna, petr), state.edupageChildren)
        assertNull(state.edupageChild)
        assertEquals(false, state.canContinue)
    }

    @Test
    fun `an edupage sign-in with one child preselects it`() = runTest(dispatcher) {
        coEvery { accounts.signInEdupage("", "novak", "pw") } returns signIn(anna)
        val vm = viewModel(edupage = true)

        signInToEdupage(vm)
        advanceUntilIdle()

        assertEquals("-1", vm.state.value.edupageChild)
        assertEquals("c1", vm.state.value.childId)
        assertEquals(true, vm.state.value.canContinue)
    }

    @Test
    fun `a wrong edupage password says so`() = runTest(dispatcher) {
        coEvery { accounts.signInEdupage(any(), any(), any()) } throws EdupageException.BadCredentials()
        val vm = viewModel(edupage = true)

        signInToEdupage(vm)
        advanceUntilIdle()

        assertEquals(UiText.Res(Res.string.school_connect_wrong_password), vm.state.value.error)
        assertEquals(ConnectStep.EDUPAGE_CREDENTIALS, vm.state.value.step)
    }

    @Test
    fun `a second factor says to approve it in edupage`() = runTest(dispatcher) {
        coEvery { accounts.signInEdupage(any(), any(), any()) } throws
            EdupageException.SecondFactor(emptyMap(), "zsx.edupage.org")
        val vm = viewModel(edupage = true)

        signInToEdupage(vm)
        advanceUntilIdle()

        assertEquals(UiText.Res(Res.string.school_edupage_second_factor), vm.state.value.error)
    }
}
