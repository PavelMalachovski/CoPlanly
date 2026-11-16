package com.coparently.app.ios.shell

import com.coparently.app.presentation.navigation.AppDestinationCatalog
import com.coparently.app.presentation.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every screen's bar is the iPhone's navigation bar (MON-24 phase 3, part B, Task 9): the shell
 * hides it only over the screens that draw no bar at all, and shows it over every other route the
 * app declares. Two of those, the Calendar's and Home's tab roots, hand it a hidden bar (part C):
 * they lead with the redesign's wordmark header in content, so their bar starts hidden.
 */
class NativeBarRoutesTest {

    private val routes = AppDestinationCatalog.all.map { it.first }

    @Test
    fun `only the loading screen - the telemetry question and sign-in draw no bar`() {
        assertEquals(
            setOf(Screen.Loading.route, Screen.PrivacyConsent.route, Screen.Auth.route),
            StackHost.NO_BAR_ROUTES
        )
    }

    @Test
    fun `every other route of the catalogue shows the native bar`() {
        val native = routes.filter { StackHost.showsNativeBar(it) }

        assertEquals(routes - StackHost.NO_BAR_ROUTES, native)
        assertTrue(StackHost.showsNativeBar(Screen.Onboarding.route), "Onboarding draws its bar natively")
    }

    @Test
    fun `every route without a bar is a route the app declares`() {
        StackHost.NO_BAR_ROUTES.forEach { route ->
            assertTrue(route in routes, "$route is not a destination")
            assertFalse(StackHost.showsNativeBar(route))
        }
    }

    @Test
    fun `the Calendar and Home are the two routes whose root bar is hidden`() {
        assertEquals(setOf(Screen.Calendar.route, Screen.Home.route), StackHost.HIDDEN_BAR_ROUTES)
        assertTrue(StackHost.hidesNativeBar(Screen.Calendar.route))
        assertTrue(StackHost.hidesNativeBar(Screen.Home.route))
        StackHost.HIDDEN_BAR_ROUTES.forEach { route ->
            assertTrue(route in routes, "$route is not a destination")
            // A hidden bar is still described to the host: its title names the screen to the back
            // button of a screen pushed over it.
            assertTrue(StackHost.showsNativeBar(route), "$route describes its bar")
        }
    }

    @Test
    fun `every other route keeps its native bar`() {
        val hidden = routes.filter { StackHost.hidesNativeBar(it) }

        assertEquals(StackHost.HIDDEN_BAR_ROUTES, hidden.toSet())
        (routes - StackHost.HIDDEN_BAR_ROUTES).forEach { route ->
            assertFalse(StackHost.hidesNativeBar(route), "$route hides its bar")
        }
        assertFalse(StackHost.hidesNativeBar(Screen.Settings.route), "Settings, pushed from either, keeps its bar")
    }
}
