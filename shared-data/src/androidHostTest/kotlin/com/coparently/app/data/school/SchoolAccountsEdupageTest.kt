package com.coparently.app.data.school

import com.coparently.app.data.platform.MemoryTextStore
import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.edupage.EdupageAccount
import com.coparently.app.data.school.edupage.EdupageChild
import com.coparently.app.data.school.edupage.EdupageClient
import com.coparently.app.data.school.edupage.EdupageException
import com.coparently.app.data.school.edupage.EdupageSession
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolSystem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Connecting an EduPage account: the address it signs in at, and what is stored once it is chosen. */
class SchoolAccountsEdupageTest {

    private val store = SchoolConnectionStore(SealedSecurePreferences(MemoryTextStore()))
    private val edupage = mockk<EdupageClient>()
    private val users = mockk<UserRepository>()
    private val scheduler = mockk<SchoolImportScheduler>(relaxed = true)
    private val accounts = SchoolAccounts(mockk<BakalariClient>(), edupage, store, users, scheduler)

    private val child = EdupageChild("-1234", "Anna Nováková", "5.A")
    private val account = EdupageAccount("ZŠ Beroun", "Novák Petr", true, listOf(child), 2026)
    private val session = EdupageSession("zsx.edupage.org", mapOf("PHPSESSID" to "s1"), "hash", JsonObject(emptyMap()))

    @Test
    fun `an address is normalised to the school host`() {
        assertEquals("zsx.edupage.org", normaliseEdupageAddress("zsx"))
        assertEquals("zsx.edupage.org", normaliseEdupageAddress("zsx.edupage.org"))
        assertEquals("zsx.edupage.org", normaliseEdupageAddress("https://zsx.edupage.org/user"))
        assertEquals("zsx.edupage.org", normaliseEdupageAddress("  ZSX.edupage.org "))
        assertEquals("login1.edupage.org", normaliseEdupageAddress(""))
        assertEquals("login1.edupage.org", normaliseEdupageAddress(null))
    }

    @Test
    fun `an address outside edupage is refused`() {
        listOf("evil.com", "https://zsx.edupage.org.evil.com/", "edupage.org.evil.com", "zsx@evil.com", "a b")
            .forEach { typed ->
                assertFailsWith<EdupageException.NotEdupage>(typed) { normaliseEdupageAddress(typed) }
            }
    }

    @Test
    fun `a sign-in at a foreign address never reaches the client`() = runTest {
        val refused = runCatching { accounts.signInEdupage("evil.com", "novak", "pw") }

        assertTrue(refused.exceptionOrNull() is EdupageException.NotEdupage)
        coVerify(exactly = 0) { edupage.signIn(any(), any(), any()) }
    }

    @Test
    fun `connect stores the password and the child and schedules the job`() = runTest {
        coEvery { users.getCurrentUserId() } returns "alice"
        val signIn = EdupageSignIn(session, "novak", "pw", account)

        val saved = accounts.connectEdupage(signIn, child, "child-1", "alice__bob")

        assertNotNull(saved)
        assertEquals(SchoolSystem.EDUPAGE, saved.system)
        assertEquals("https://zsx.edupage.org", saved.baseUrl)
        assertEquals("child-1", saved.childId)
        assertEquals(SchoolConnectionStatus.OK, saved.status)
        val stored = store.get("alice", saved.id)
        assertEquals("pw", stored?.edupage?.password)
        assertEquals("-1234", stored?.edupage?.studentId)
        assertEquals(mapOf("PHPSESSID" to "s1"), stored?.edupage?.cookies)
        verify(exactly = 1) { scheduler.schedule() }
    }
}
