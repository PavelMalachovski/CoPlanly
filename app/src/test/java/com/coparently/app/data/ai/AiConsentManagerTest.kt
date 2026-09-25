package com.coparently.app.data.ai

import app.cash.turbine.test
import com.coparently.app.domain.ai.AI_CONSENT_VERSION
import com.coparently.app.domain.ai.AiConsent
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The AI-assist consent: read once per account, written only through
 * [UserRepository.setAiConsent], remembered after a write that landed, and forgotten when the
 * server says it holds none.
 */
class AiConsentManagerTest {

    private val uid = MutableStateFlow<String?>(ALICE)
    private var stored: AiConsent? = null
    private var writeSucceeds = true

    private val userRepository = mockk<UserRepository> {
        every { observeCurrentUserId() } returns uid
        coEvery { getCurrentUserId() } answers { uid.value }
        coEvery { getAiConsent() } answers { stored }
        coEvery { setAiConsent(any()) } answers {
            if (writeSucceeds) stored = firstArg<Int?>()?.let { AiConsent(it, GRANTED_AT) }
            writeSucceeds
        }
    }

    private val manager = AiConsentManager(userRepository)

    @Test
    fun `the profile is read once and then remembered`() = runTest {
        stored = AiConsent(AI_CONSENT_VERSION, GRANTED_AT)

        assertEquals(stored, manager.current())
        assertEquals(stored, manager.current())

        coVerify(exactly = 1) { userRepository.getAiConsent() }
    }

    @Test
    fun `granting writes the current version and is known at once`() = runTest {
        assertTrue(manager.grant())

        coVerify { userRepository.setAiConsent(AI_CONSENT_VERSION) }
        assertEquals(AI_CONSENT_VERSION, manager.current()?.version)
        // Known from the write, not from a second read.
        coVerify(exactly = 0) { userRepository.getAiConsent() }
    }

    @Test
    fun `a failed write is reported and remembers nothing`() = runTest {
        writeSucceeds = false

        assertFalse(manager.grant())
        assertNull(manager.current())
    }

    @Test
    fun `withdrawing deletes the consent`() = runTest {
        stored = AiConsent(AI_CONSENT_VERSION, GRANTED_AT)
        manager.current()

        assertTrue(manager.withdraw())

        coVerify { userRepository.setAiConsent(null) }
        assertNull(manager.current())
    }

    @Test
    fun `forgetting makes the next request read the profile again`() = runTest {
        stored = AiConsent(AI_CONSENT_VERSION, GRANTED_AT)
        manager.current()
        stored = null

        manager.forget()

        assertNull(manager.current())
        coVerify(exactly = 2) { userRepository.getAiConsent() }
    }

    @Test
    fun `observe follows the account and this device's writes`() = runTest {
        manager.observe().test {
            assertNull(awaitItem())
            manager.grant()
            assertEquals(AI_CONSENT_VERSION, awaitItem()?.version)
            uid.value = null
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing is read or written while signed out`() = runTest {
        uid.value = null

        assertNull(manager.current())
        assertFalse(manager.grant())
        coVerify(exactly = 0) { userRepository.setAiConsent(any()) }
    }

    private companion object {
        const val ALICE = "uid-alice"
        const val GRANTED_AT = 1_790_000_000_000L
    }
}
