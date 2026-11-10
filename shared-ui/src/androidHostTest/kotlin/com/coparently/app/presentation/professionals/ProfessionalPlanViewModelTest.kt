package com.coparently.app.presentation.professionals

import androidx.lifecycle.SavedStateHandle
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.repository.ProfessionalRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The parenting plan as a professional reads it (MON-18). */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfessionalPlanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val far = System.currentTimeMillis() + 10L * 24 * 60 * 60 * 1000

    private fun grant(consents: Map<String, Long>, expiresAtMillis: Long = far) = ProfessionalGrant(
        id = "a__b__pro", familyId = "a__b", familyParents = listOf("a", "b"), proUid = "pro",
        role = ProfessionalRole.THERAPIST, name = "T", invitedBy = "a", grantedAtMillis = 1L,
        expiresAtMillis = expiresAtMillis, consents = consents
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(repository: ProfessionalRepository) =
        ProfessionalPlanViewModel(SavedStateHandle(mapOf("grantId" to "a__b__pro")), repository)

    @Test
    fun `an expired grant reads nothing`() = runTest(dispatcher) {
        val repository = mockk<ProfessionalRepository> {
            every { observeMyGrants() } returns flowOf(listOf(grant(mapOf("a" to 1L, "b" to 1L), 1L)))
        }
        val model = vm(repository)
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(ProfessionalPlanUiState.Unavailable, model.uiState.value)
        verify(exactly = 0) { repository.observePlan(any()) }
    }

    @Test
    fun `both halves are shown in the family's own order`() = runTest(dispatcher) {
        val active = grant(mapOf("a" to 1L, "b" to 1L))
        val alice = ParentingPlanEntry(answers = mapOf("residence_home" to "Alternate weeks"))
        val repository = mockk<ProfessionalRepository> {
            every { observeMyGrants() } returns flowOf(listOf(active))
            every { observePlan(active) } returns flowOf(mapOf("a" to alice))
        }
        val model = vm(repository)
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        val ready = assertIs<ProfessionalPlanUiState.Ready>(model.uiState.value)
        assertEquals("Alternate weeks", ready.first.answerTo("residence_home"))
        assertNull(ready.second)
    }
}
