package com.coparently.app.presentation.navigation

import com.coparently.app.domain.text.UriComponent
import com.coparently.app.presentation.export.ExportViewModel
import com.coparently.app.presentation.journal.JournalEditorViewModel

/**
 * Sealed class representing all navigation screens.
 *
 * An id or a text a route carries is percent-encoded by [UriComponent], which writes what
 * `android.net.Uri.encode` wrote before the routes moved to common code (MON-24 phase 2, Task 12):
 * a route a pending deep link carries across a process death is written by one build and parsed by
 * the next.
 */
sealed class Screen(val route: String) {
    data object Loading : Screen("loading")

    /**
     * The analytics and crash-reporting question, asked once before sign-in (REL-5).
     *
     * Named for the route rather than for [com.coparently.app.domain.telemetry.TelemetryConsent],
     * which the navigation graph also imports: two things called `TelemetryConsent` in one scope
     * resolve correctly and read as if they might not.
     */
    data object PrivacyConsent : Screen("privacy_consent")
    data object Auth : Screen("auth")

    /**
     * The first-run questionnaire. Deliberately absent from
     * [BottomNavDestination.topLevelRoutes]: the bottom bar hides itself for any route not
     * listed there, which is exactly what a wizard wants.
     */
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Calendar : Screen("calendar")
    data object AddEvent : Screen("add_event?date={date}&hour={hour}") {
        const val ARG_DATE = "date"
        const val ARG_HOUR = "hour"

        fun createRoute(date: kotlinx.datetime.LocalDate? = null, hour: Int? = null): String {
            val dateParam = date?.toString() ?: "null"
            val hourParam = hour?.toString() ?: "-1"
            return "add_event?date=$dateParam&hour=$hourParam"
        }
    }
    data object Settings : Screen("settings")
    data object ChildInfo : Screen("child_info")
    data object ParentingPlan : Screen("parenting_plan")

    /**
     * The communication record (MON-3). [ARG_THREAD] names a thread kept after the co-parent
     * deleted their account, when the export is opened from that thread's banner; Settings opens
     * it with none.
     */
    data object Export : Screen("export?thread={thread}&family={family}") {
        const val ARG_THREAD = ExportViewModel.ARG_THREAD
        const val ARG_FAMILY = ExportViewModel.ARG_FAMILY

        /** The route, for the kept thread [conversationId] or, when null, the family on screen. */
        fun createRoute(conversationId: String? = null): String =
            if (conversationId.isNullOrBlank()) "export" else "export?thread=${UriComponent.encode(conversationId)}"

        /** The route for an ended family's record (Family → Earlier families). */
        fun forEarlierFamily(familyId: String): String = "export?family=${UriComponent.encode(familyId)}"
    }
    data object Documents : Screen("family_documents?family={family}") {
        const val ARG_FAMILY = com.coparently.app.presentation.documents.FamilyDocumentsViewModel.ARG_FAMILY

        /** The vault of the family on screen. */
        fun createRoute(): String = "family_documents"

        /** An ended family's vault, read-only (Family → Earlier families). */
        fun forEarlierFamily(familyId: String): String = "family_documents?family=${UriComponent.encode(familyId)}"
    }

    /** Family → Money → Child support (25 October 2026). */
    data object Support : Screen("support")

    /** Family → Earlier families: the families this parent was in, each read-only. */
    data object EarlierFamilies : Screen("earlier_families")

    /** One ended family's records, read-only. */
    data object EarlierFamily : Screen("earlier_family/{familyId}") {
        const val ARG_FAMILY_ID = "familyId"

        /** The route for [familyId]. */
        fun createRoute(familyId: String): String = "earlier_family/${UriComponent.encode(familyId)}"
    }

    /** A month in figures, with an optional AI-written summary of them. */
    data object MonthReview : Screen("month_review")

    /** This parent's own records that name no family while they co-parent, and the family each takes. */
    data object UnassignedRecords : Screen("unassigned_records")

    /** The private journal's list (MON-22). */
    data object Journal : Screen("journal")

    /** "My stickers": how stickers address this parent, and those received and sent. */
    data object Stickers : Screen("stickers")

    /** One journal entry in the editor; `new` opens an empty one. */
    data object JournalEditor : Screen("journal_entry/{entryId}") {
        /** The route for [entryId], or for a new entry when it is [JournalEditorViewModel.NEW_ENTRY]. */
        fun createRoute(entryId: String): String = "journal_entry/$entryId"
    }

    data object Pets : Screen("pets")
    data object Pairing : Screen("pairing?code={code}&enter={enter}&unpair={unpair}") {
        /** Optional invite code carried by a `coplanly://pair` deep link. */
        const val ARG_CODE = "code"

        /**
         * Whether the screen opens on "enter a code" rather than "share my code". The onboarding
         * wizard's first step sets it for a parent who is holding the other one's code.
         */
        const val ARG_ENTER = "enter"

        /** Builds the route, with [code] pre-filled when a deep link supplied one. */
        fun routeWithCode(code: String?): String =
            if (code.isNullOrEmpty()) "pairing" else "pairing?code=$code"

        /** Builds the route that opens on code entry, with nothing pre-filled. */
        fun routeForCodeEntry(): String = "pairing?enter=true"

        /**
         * Whether the screen opens its unpair confirmation as soon as it knows the account is
         * paired: the chat's "Block and stop sharing" (play-final audit F-10) lands here.
         */
        const val ARG_UNPAIR = "unpair"

        /** Builds the route that opens straight on the unpair confirmation. */
        fun routeForUnpair(): String = "pairing?unpair=true"
    }

    /**
     * Redeeming a guest invitation — a separate route from [Pairing], mirroring the two
     * separate callables behind them. Nothing about a guest belongs on a screen whose other
     * outcome is a co-parent link.
     */
    data object GuestAccept : Screen("guest_accept?code={code}") {
        /** Optional invite code carried by a `coplanly://guest` deep link. */
        const val ARG_CODE = "code"

        /** Builds the route, with [code] pre-filled when a deep link supplied one. */
        fun routeWithCode(code: String?): String =
            if (code.isNullOrEmpty()) "guest_accept" else "guest_accept?code=$code"
    }

    /** The parents' list of who outside the family can see the calendar (item 16). */
    data object Friends : Screen("friends")

    /** Read-only calendar links for an iPhone or any other calendar app (MON-17). */
    data object CalendarFeed : Screen("calendar_feed")

    /** The school import's connections (MON-8). */
    data object SchoolImport : Screen("school_import")

    /** Connecting a school, or signing a stored connection in again (MON-8). */
    data object SchoolConnect : Screen("school_connect?connectionId={connectionId}") {
        /** The connection to sign in again; absent for a new connection. */
        const val ARG_CONNECTION_ID =
            com.coparently.app.presentation.school.ConnectSchoolViewModel.ARG_CONNECTION_ID

        /** The route for a new connection, or for signing [connectionId] in again. */
        fun createRoute(connectionId: String? = null): String = if (connectionId.isNullOrBlank()) {
            "school_connect"
        } else {
            "school_connect?connectionId=${UriComponent.encode(connectionId)}"
        }
    }

    /** Where the calendar's holiday data comes from, and its licences (MON-13). */
    data object DataSources : Screen("data_sources")

    /**
     * One friend as the parents read them — their face, phone number and blood group, and the
     * control that ends their access.
     *
     * Carries the uid only: the name comes from the grant the screen already observes, so it
     * cannot go stale against a friend who renamed themselves between the two screens.
     */
    data object FriendDetail : Screen("friend_detail/{friendUid}") {
        /** Whose card to open. */
        const val ARG_FRIEND_UID = "friendUid"

        /** Builds the route for [friendUid]. */
        fun createRoute(friendUid: String): String = "friend_detail/$friendUid"
    }

    /** The friend's own profile, authored by them and read by the two parents. */
    data object FriendProfile : Screen("friend_profile")

    /** A family friend's read-only calendar of one family, by family id (phase 2). */
    data object FriendCalendar : Screen("friend_calendar/{familyId}") {
        /** Which family; read by `FriendCalendarViewModel` from its `SavedStateHandle`. */
        const val ARG_FAMILY_ID = "familyId"

        /** Builds the route for [familyId]. */
        fun createRoute(familyId: String): String = "friend_calendar/$familyId"
    }

    /** Professional access (MON-18): the parents' grants and a professional's families. */
    data object Professionals : Screen("professionals")

    /** A professional's read-only calendar of one family, by grant id. */
    data object ProfessionalCalendar : Screen("professional_calendar/{grantId}") {
        /** Which grant; read by `ProfessionalCalendarViewModel` from its `SavedStateHandle`. */
        const val ARG_GRANT_ID = "grantId"

        /** Builds the route for [grantId]. */
        fun createRoute(grantId: String): String = "professional_calendar/$grantId"
    }

    /** A professional's read-only parenting plan of one family, by grant id. */
    data object ProfessionalPlan : Screen("professional_plan/{grantId}") {
        /** Which grant; read by `ProfessionalPlanViewModel` from its `SavedStateHandle`. */
        const val ARG_GRANT_ID = "grantId"

        /** Builds the route for [grantId]. */
        fun createRoute(grantId: String): String = "professional_plan/$grantId"
    }

    /**
     * The custody schedule editor. Opened plainly from Settings and onboarding, or — MON-21 —
     * from an agreed parenting-plan answer, whose question id it carries so the editor can quote
     * the answer and the proposal can cite it.
     */
    data object CustodySetup : Screen("custody_setup?planQuestion={planQuestion}") {
        /** The parenting-plan question the editor was opened from; blank when none. */
        const val ARG_PLAN_QUESTION = "planQuestion"

        /** Builds the route, carrying [planQuestion] when the editor is opened from the plan. */
        fun routeFor(planQuestion: String? = null): String =
            if (planQuestion.isNullOrBlank()) "custody_setup" else "custody_setup?planQuestion=$planQuestion"
    }

    /** The signed-in user's own profile — editable. */
    data object MyProfile : Screen("my_profile")

    /** The co-parent's profile — read-only, `firestore.rules` refuses the write anyway. */
    data object CoParentProfile : Screen("coparent_profile")

    /**
     * The pairing conflict screen. Reached only from an accepted pairing that found two
     * disagreeing custody patterns; the two patterns themselves travel in
     * `PendingCustodyConflict`, not in the route — no route argument could carry them, and
     * re-deriving them here would race the shared-custody mirror.
     */
    data object CustodyConflict : Screen("custody_conflict")

    data object EditEvent : Screen("edit_event/{eventId}") {
        const val ARG_EVENT_ID = "eventId"

        fun createRoute(eventId: String): String {
            return "edit_event/$eventId"
        }
    }

    data object EditChildInfo : Screen("edit_child_info/{childInfoId}") {
        const val ARG_CHILD_INFO_ID = "childInfoId"

        fun createRoute(childInfoId: String): String {
            return "edit_child_info/$childInfoId"
        }
    }

    /** One child's read-only record, between the children list and the editor. */
    data object ChildDetail : Screen("child_detail/{childInfoId}") {
        const val ARG_CHILD_INFO_ID = "childInfoId"

        fun createRoute(childInfoId: String): String {
            return "child_detail/$childInfoId"
        }
    }

    data object EditPet : Screen("edit_pet/{petId}") {
        const val ARG_PET_ID = "petId"

        fun createRoute(petId: String): String {
            return "edit_pet/$petId"
        }
    }

    /**
     * Conversation list. An optional [ARG_DRAFT] is carried through to the thread the user
     * opens, so "Settle up" on Expenses can pre-fill the composer without sending anything.
     */
    data object Conversations : Screen("conversations?draft={draft}") {
        const val ARG_DRAFT = "draft"

        fun createRoute(draft: String? = null): String {
            val encoded = draft?.let { UriComponent.encode(it) }.orEmpty()
            return "conversations?draft=$encoded"
        }
    }

    data object Chat : Screen("chat/{conversationId}?draft={draft}") {
        const val ARG_CONVERSATION_ID = "conversationId"
        const val ARG_DRAFT = "draft"

        fun createRoute(conversationId: String, draft: String? = null): String {
            val encoded = draft?.let { UriComponent.encode(it) }.orEmpty()
            // Encoded like the draft: an id with a `/` in it — which only a crafted deep link
            // supplies, but a crafted deep link is one `am start` away — threw inside
            // `navigate()` and took the activity down.
            return "chat/${UriComponent.encode(conversationId)}?draft=$encoded"
        }
    }
    data object Expenses : Screen("expenses")
    data object Family : Screen("family")
    data object AddExpense : Screen("add_expense")
    data object EditExpense : Screen("edit_expense/{expenseId}") {
        const val ARG_EXPENSE_ID = "expenseId"

        fun createRoute(expenseId: String): String = "edit_expense/$expenseId"
    }

    /**
     * Important phone numbers, one tap from the dialler.
     *
     * A detail screen, not in [BottomNavDestination.topLevelRoutes]: the bottom bar hides and
     * an up-arrow appears, the same as every other screen reached from a row rather than a tab.
     */
    data object Contacts : Screen("contacts")

    /**
     * The requests inbox. The route keeps its old name; the screen holds more than change
     * requests now (phase 2).
     */
    data object ChangeRequests : Screen("change_requests?eventId={eventId}&section={section}") {
        const val ARG_EVENT_ID = "eventId"
        const val ARG_SECTION = "section"

        /**
         * @param eventId Event whose request should be highlighted, or null for the plain inbox.
         * @param section A section to scroll to on arrival, or null.
         */
        fun createRoute(
            eventId: String? = null,
            section: com.coparently.app.presentation.changerequests.InboxSection? = null
        ): String = "change_requests?eventId=${eventId ?: "null"}&section=${section?.routeValue ?: "null"}"
    }

    /**
     * The change-request form.
     *
     * It used to carry the conversation it was opened from, because the chat card was posted only
     * when one was supplied — so a change proposed from the calendar reached the thread not at
     * all. `ActivityAnnouncer` resolves the pair's thread from the two uids itself, so the
     * argument had nothing left to do and is gone rather than left as dead weight.
     */
    data object RequestChange : Screen("request_change/{eventId}") {
        const val ARG_EVENT_ID = "eventId"

        fun createRoute(eventId: String): String = "request_change/$eventId"
    }
}
