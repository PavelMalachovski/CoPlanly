package com.coparently.app.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [AppDestinationCatalog], the data half of the destinations both shells read (MON-24 phase
 * 3): every route of [Screen] the graph declares is in it exactly once, and each has the kind a
 * shell builds it as — the four tabs as tab roots, Expenses full width (design item 19's
 * exception), the forms as forms and the screens before the app proper as the root flow.
 */
class AppDestinationsTest {

    private val kinds: Map<String, DestinationKind> = AppDestinationCatalog.all.toMap()

    @Test
    fun `the catalogue holds every route of Screen once`() {
        val routes = AppDestinationCatalog.all.map { it.first }
        assertEquals(routes.size, routes.toSet().size, "a route appears twice")
        assertEquals(SCREEN_ROUTES.toSet(), routes.toSet())
        assertEquals(51, routes.size)
    }

    @Test
    fun `the four tabs are tab roots`() {
        BottomNavDestination.entries.forEach { tab ->
            assertEquals(DestinationKind.TAB_ROOT, kinds[tab.route], tab.name)
        }
        assertEquals(
            BottomNavDestination.topLevelRoutes,
            kinds.filterValues { it == DestinationKind.TAB_ROOT }.keys
        )
    }

    @Test
    fun `expenses is the one full width push`() {
        assertEquals(
            setOf(Screen.Expenses.route),
            kinds.filterValues { it == DestinationKind.FULL_WIDTH_PUSH }.keys
        )
    }

    @Test
    fun `the forms are forms`() {
        assertEquals(
            setOf(
                Screen.AddExpense.route,
                Screen.EditExpense.route,
                Screen.AddEvent.route,
                Screen.EditEvent.route,
                Screen.EditChildInfo.route,
                Screen.EditPet.route,
                Screen.JournalEditor.route,
                Screen.RequestChange.route
            ),
            kinds.filterValues { it == DestinationKind.FORM }.keys
        )
    }

    @Test
    fun `loading - consent - sign-in and onboarding are the root flow`() {
        assertEquals(
            setOf(
                Screen.Loading.route,
                Screen.PrivacyConsent.route,
                Screen.Auth.route,
                Screen.Onboarding.route
            ),
            kinds.filterValues { it == DestinationKind.ROOT_FLOW }.keys
        )
    }

    @Test
    fun `every other route is a push`() {
        val others = SCREEN_ROUTES.toSet() -
            BottomNavDestination.topLevelRoutes -
            setOf(
                Screen.Expenses.route,
                Screen.AddExpense.route,
                Screen.EditExpense.route,
                Screen.AddEvent.route,
                Screen.EditEvent.route,
                Screen.EditChildInfo.route,
                Screen.EditPet.route,
                Screen.JournalEditor.route,
                Screen.RequestChange.route,
                Screen.Loading.route,
                Screen.PrivacyConsent.route,
                Screen.Auth.route,
                Screen.Onboarding.route
            )
        assertEquals(others, kinds.filterValues { it == DestinationKind.PUSH }.keys)
    }

    private companion object {
        /** Every route of [Screen] the graph declares today, spelled out: common code has no reflection. */
        val SCREEN_ROUTES = listOf(
            Screen.Loading.route,
            Screen.PrivacyConsent.route,
            Screen.Auth.route,
            Screen.Onboarding.route,
            Screen.Home.route,
            Screen.Calendar.route,
            Screen.AddEvent.route,
            Screen.Settings.route,
            Screen.ChildInfo.route,
            Screen.ParentingPlan.route,
            Screen.Export.route,
            Screen.Documents.route,
            Screen.Support.route,
            Screen.EarlierFamilies.route,
            Screen.EarlierFamily.route,
            Screen.MonthReview.route,
            Screen.UnassignedRecords.route,
            Screen.Journal.route,
            Screen.Stickers.route,
            Screen.JournalEditor.route,
            Screen.Pets.route,
            Screen.Pairing.route,
            Screen.GuestAccept.route,
            Screen.Friends.route,
            Screen.CalendarFeed.route,
            Screen.SchoolImport.route,
            Screen.SchoolConnect.route,
            Screen.DataSources.route,
            Screen.FriendDetail.route,
            Screen.FriendProfile.route,
            Screen.FriendCalendar.route,
            Screen.Professionals.route,
            Screen.ProfessionalCalendar.route,
            Screen.ProfessionalPlan.route,
            Screen.CustodySetup.route,
            Screen.MyProfile.route,
            Screen.CoParentProfile.route,
            Screen.CustodyConflict.route,
            Screen.EditEvent.route,
            Screen.EditChildInfo.route,
            Screen.ChildDetail.route,
            Screen.EditPet.route,
            Screen.Conversations.route,
            Screen.Chat.route,
            Screen.Expenses.route,
            Screen.Family.route,
            Screen.AddExpense.route,
            Screen.EditExpense.route,
            Screen.Contacts.route,
            Screen.ChangeRequests.route,
            Screen.RequestChange.route
        )
    }
}
