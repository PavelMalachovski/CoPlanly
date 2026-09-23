package com.coparently.app.presentation.contacts

import com.coparently.app.domain.contacts.ContactGroup
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.EmergencyContact
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.presentation.common.Loadable
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import kotlin.test.assertIs

/**
 * The emergency-contacts screen: children's contacts and pets' vets in one list, and a loading
 * state that is never mistaken for "there are no contacts".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = LocalDateTime.parse("2026-09-01T09:00:00")

    private val children = listOf(
        ChildInfo(
            id = "c1",
            childName = "Anna",
            dateOfBirth = null,
            emergencyContacts = listOf(EmergencyContact(name = "Grandma", relationship = "Grandmother", phone = "123")),
            createdAt = now,
            updatedAt = now
        ),
        // No contacts: omitted rather than rendered as an empty header.
        ChildInfo(id = "c2", childName = "Ben", dateOfBirth = null, createdAt = now, updatedAt = now)
    )
    private val pets = listOf(
        Pet(id = "p1", name = "Rex", vetName = "Dr Vet", vetPhone = "456", createdAt = now, updatedAt = now)
    )

    private fun viewModel() = ContactsViewModel(
        childInfoRepository = mockk<ChildInfoRepository> { every { getAllChildInfo() } returns flowOf(children) },
        petRepository = mockk<PetRepository> { every { getAllPets() } returns flowOf(pets) }
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading, not empty`() = runTest(dispatcher) {
        val vm = viewModel()

        assertEquals(Loadable.Loading, vm.groups.value)
    }

    @Test
    fun `lists each child with contacts and each pet with a vet`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.groups.collect {} }
        advanceUntilIdle()

        val loaded = assertIs<Loadable.Loaded<List<ContactGroup>>>(vm.groups.value)
        assertEquals(listOf("c1", "pet:p1"), loaded.value.map { it.childId })
        assertEquals(listOf("123"), loaded.value.first().contacts.single().numbers)
    }
}
