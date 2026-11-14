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
 * app declares.
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
}
