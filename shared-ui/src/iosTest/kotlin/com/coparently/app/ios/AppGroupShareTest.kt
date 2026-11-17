package com.coparently.app.ios

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the app shares with its notification extension (phase 4 part C): the uid and the language
 * and nothing else, both gone at sign-out. On a suite of the test's own, so the app's App Group is
 * never touched; the keys and the suite are the ones Task 6's Swift extension reads.
 */
class AppGroupShareTest {

    @BeforeTest
    fun useATestSuite() {
        AppGroupShare.suiteName = TEST_SUITE
        NSUserDefaults(suiteName = TEST_SUITE).removePersistentDomainForName(TEST_SUITE)
    }

    @AfterTest
    fun restoreTheSuite() {
        NSUserDefaults(suiteName = TEST_SUITE).removePersistentDomainForName(TEST_SUITE)
        AppGroupShare.suiteName = AppGroupShare.SUITE
    }

    @Test
    fun `the uid and the language are shared under the keys the extension reads`() {
        AppGroupShare.write("alice", "cs")

        val shared = NSUserDefaults(suiteName = TEST_SUITE)
        assertEquals("alice", shared.stringForKey("coplanly.push.uid"))
        assertEquals("cs", shared.stringForKey("coplanly.push.language"))
        assertEquals("alice", AppGroupShare.uid)
        assertEquals("cs", AppGroupShare.language)
    }

    @Test
    fun `the App Group is the one the entitlements name`() {
        assertEquals("group.app.coplanly", AppGroupShare.SUITE)
    }

    @Test
    fun `both are cleared at sign-out`() = runTest {
        shareSignedInAccount(flowOf("alice", null)) { "en-150" }

        assertNull(AppGroupShare.uid)
        assertNull(AppGroupShare.language)
    }

    @Test
    fun `a sign-in shares the account with the language the app speaks`() = runTest {
        shareSignedInAccount(flowOf(null, "bob")) { "de" }

        assertEquals("bob", AppGroupShare.uid)
        assertEquals("de", AppGroupShare.language)
    }

    @Test
    fun `another account signing in replaces the previous one`() {
        AppGroupShare.write("alice", "cs")
        AppGroupShare.write("bob", "uk")

        assertEquals("bob", AppGroupShare.uid)
        assertEquals("uk", AppGroupShare.language)
    }

    @Test
    fun `a blank uid is nobody`() {
        AppGroupShare.write("alice", "cs")
        AppGroupShare.write(" ", "cs")

        assertNull(AppGroupShare.uid)
        assertNull(AppGroupShare.language)
    }

    private companion object {
        const val TEST_SUITE = "app.coplanly.tests.appgroupshare"
    }
}
