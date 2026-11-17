package com.coparently.app.data.repository

import com.coparently.app.data.remote.firebase.FirestoreUserDataSource
import com.coparently.app.data.remote.firebase.SignedInUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * [UserRepositoryImpl.recordTimeZone] (iPhone phase 4 part C, design §7.3): the phone's zone goes
 * into `users/{uid}.timeZone` as a merge of that key alone, only when it differs from the stored
 * one, never into a profile that is missing or could not be read, and never past the 64 characters
 * `firestore.rules` accepts.
 */
class UserRepositoryTimeZoneTest {

    private val authService: SignedInUser = mockk(relaxed = true)
    private val users: FirestoreUserDataSource = mockk(relaxed = true)
    private lateinit var repository: UserRepositoryImpl

    @Before
    fun setUp() {
        coEvery { authService.getCurrentUid() } returns UID
        coEvery { users.updateUser(any(), any()) } returns Result.success(Unit)
        repository = UserRepositoryImpl(mockk(relaxed = true), authService, users, mockk(relaxed = true), mockk())
    }

    @Test
    fun `writes the zone alone into a profile that holds another`() = runTest {
        coEvery { users.readUserOrThrow(UID) } returns mapOf("name" to "Alice", "timeZone" to "Europe/London")

        repository.recordTimeZone(PRAGUE)

        coVerify(exactly = 1) { users.updateUser(UID, mapOf("timeZone" to PRAGUE)) }
    }

    @Test
    fun `writes the zone into a profile that has none`() = runTest {
        coEvery { users.readUserOrThrow(UID) } returns mapOf("name" to "Alice")

        repository.recordTimeZone(PRAGUE)

        coVerify(exactly = 1) { users.updateUser(UID, mapOf("timeZone" to PRAGUE)) }
    }

    @Test
    fun `writes nothing when the stored zone is the same`() = runTest {
        coEvery { users.readUserOrThrow(UID) } returns mapOf("name" to "Alice", "timeZone" to PRAGUE)

        repository.recordTimeZone(PRAGUE)

        coVerify(exactly = 0) { users.updateUser(any(), any()) }
    }

    @Test
    fun `writes nothing into a missing or unreadable profile`() = runTest {
        coEvery { users.readUserOrThrow(UID) } returns null
        repository.recordTimeZone(PRAGUE)
        coEvery { users.readUserOrThrow(UID) } throws IllegalStateException("offline")
        repository.recordTimeZone(PRAGUE)

        coVerify(exactly = 0) { users.updateUser(any(), any()) }
    }

    @Test
    fun `writes nothing while signed out, or for a zone the rules would refuse`() = runTest {
        coEvery { users.readUserOrThrow(UID) } returns mapOf("name" to "Alice")
        repository.recordTimeZone("")
        repository.recordTimeZone("x".repeat(65))
        coEvery { authService.getCurrentUid() } returns null
        repository.recordTimeZone(PRAGUE)

        coVerify(exactly = 0) { users.updateUser(any(), any()) }
    }

    private companion object {
        const val UID = "alice-uid"
        const val PRAGUE = "Europe/Prague"
    }
}
