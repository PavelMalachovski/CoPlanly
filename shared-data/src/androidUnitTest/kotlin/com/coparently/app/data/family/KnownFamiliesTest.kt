package com.coparently.app.data.family

import com.coparently.app.data.local.dao.KnownFamilyDao
import com.coparently.app.data.remote.firebase.FirestoreFamilyDataSource
import com.coparently.app.data.remote.firebase.FirestoreUserDataSource
import com.coparently.app.domain.family.FamilyKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class KnownFamiliesTest {

    private val dao = mockk<KnownFamilyDao>(relaxed = true)
    private val users = mockk<FirestoreUserDataSource>()
    private val families = mockk<FirestoreFamilyDataSource>()
    private val known = KnownFamilies(dao, users, families)
    private val lana = FamilyKey.of(ME, LANA)

    @Test
    fun `a live family's co-parent name and both slots are remembered`() = runTest {
        coEvery { users.getUserById(LANA) } returns mapOf("name" to " Lana ")
        coEvery { families.getSlots(lana) } returns mapOf(ME to "dad", LANA to "mom")

        known.refresh(ME, listOf(LANA), nowMillis = 7L)

        coVerify { dao.rememberName(lana, LANA, "Lana", 7L) }
        coVerify { dao.rememberSlots(lana, LANA, "dad", "mom", 7L) }
    }

    @Test
    fun `a failed or empty read keeps what was known`() = runTest {
        coEvery { users.getUserById(LANA) } throws IllegalStateException("offline")
        coEvery { families.getSlots(lana) } returns mapOf(ME to "dad")

        known.refresh(ME, listOf(LANA), nowMillis = 7L)

        coVerify(exactly = 0) { dao.rememberName(any(), any(), any(), any()) }
        coVerify(exactly = 0) { dao.rememberSlots(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a blank name, an unknown slot and the parent themselves write nothing`() = runTest {
        coEvery { users.getUserById(LANA) } returns mapOf("name" to "  ")
        coEvery { families.getSlots(lana) } returns mapOf(ME to "dad", LANA to "parent")

        known.refresh(ME, listOf(LANA, ME, ""), nowMillis = 7L)

        coVerify(exactly = 0) { dao.rememberName(any(), any(), any(), any()) }
        coVerify(exactly = 0) { dao.rememberSlots(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { users.getUserById(ME) }
    }

    private companion object {
        const val ME = "uid-me"
        const val LANA = "uid-lana"
    }
}
