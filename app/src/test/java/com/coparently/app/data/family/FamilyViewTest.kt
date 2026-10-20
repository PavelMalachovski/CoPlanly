package com.coparently.app.data.family

import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.family.FamilyKey
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The family on screen hides the records of the parent's other live families, and nothing else
 * (tester report, October 2026: the family with Carol showed the family with Igor's expenses).
 */
class FamilyViewTest {

    private val zuzana = "zuzana"
    private val igorFamily = FamilyKey.of(zuzana, "igor")
    private val carolFamily = FamilyKey.of(zuzana, "carol")
    private val endedFamily = FamilyKey.of(zuzana, "ex")

    private fun row(onScreen: String?, partners: List<String>) = UserEntity(
        id = zuzana,
        email = "z@example.test",
        name = "Zuzana",
        role = "mom",
        colorCode = "#FF4081",
        partnerId = onScreen,
        partnerIdsJson = partners.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    )

    @Test
    fun `with two families the other family's records are hidden`() {
        val view = FamilyView.of(zuzana, row(onScreen = "carol", partners = listOf("igor", "carol")))
        assertTrue(view.shows(carolFamily))
        assertFalse(view.shows(igorFamily))
    }

    @Test
    fun `a record naming no family and one of an ended family stay visible`() {
        val view = FamilyView.of(zuzana, row(onScreen = "carol", partners = listOf("igor", "carol")))
        assertTrue(view.shows(null))
        assertTrue(view.shows(""))
        assertTrue(view.shows(endedFamily))
    }

    @Test
    fun `with one family or none nothing is hidden`() {
        val one = FamilyView.of(zuzana, row(onScreen = "igor", partners = listOf("igor")))
        assertTrue(one.shows(igorFamily))
        assertTrue(one.shows(endedFamily))
        val none = FamilyView.of(zuzana, row(onScreen = null, partners = emptyList()))
        assertTrue(none.shows(igorFamily))
        assertEquals(FamilyView.EVERYTHING, FamilyView.of(zuzana, null))
    }

    @Test
    fun `a malformed partner list hides nothing rather than failing`() {
        val malformed = row(onScreen = "igor", partners = emptyList()).copy(partnerIdsJson = "not json")
        val view = FamilyView.of(zuzana, malformed)
        assertTrue(view.shows(carolFamily))
    }

    @Test
    fun `a switch re-filters the same records`() = runTest(UnconfinedTestDispatcher()) {
        val user = mockk<FirebaseUser> { every { uid } returns zuzana }
        val auth = mockk<FirebaseAuthService> { every { getCurrentUser() } returns user }
        val rowFlow = MutableStateFlow<UserEntity?>(row(onScreen = "igor", partners = listOf("igor", "carol")))
        val userDao = mockk<UserDao> {
            every { observeUserById(zuzana) } returns rowFlow
            coEvery { getUserById(zuzana) } answers { rowFlow.value }
        }
        val records = flowOf(listOf("shoes" to igorFamily, "swimming" to carolFamily, "own" to null))
        val seen = mutableListOf<List<String>>()
        val job = launch {
            records.inFamilyOnScreen(auth, userDao) { it.second }
                .take(2)
                .toList(mutableListOf())
                .forEach { list -> seen += list.map { it.first } }
        }
        rowFlow.value = row(onScreen = "carol", partners = listOf("igor", "carol"))
        job.join()
        assertEquals(listOf(listOf("shoes", "own"), listOf("swimming", "own")), seen)
    }

    @Test
    fun `signed out nothing is filtered`() = runTest {
        val auth = mockk<FirebaseAuthService> { every { getCurrentUser() } returns null }
        val records = flowOf(listOf("shoes" to igorFamily, "swimming" to carolFamily))
        val out = records.inFamilyOnScreen(auth, mockk()) { it.second }.first()
        assertEquals(2, out.size)
    }
}
