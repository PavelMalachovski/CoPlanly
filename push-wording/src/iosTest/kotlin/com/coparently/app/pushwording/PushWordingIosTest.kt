package com.coparently.app.pushwording

import platform.Foundation.NSBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The iPhone's half of the golden corpus (iPhone phase 4 part C, Task 2): the wording the
 * notification extension will run — `:push-wording` over `BundleWordingStrings` (the `.strings` and
 * `.stringsdict` files `tools/ios/generate-push-strings.js` writes, which Gradle writes for this run,
 * `PushStringsLocation`) and [FoundationReaderClock] — on the simulator.
 *
 * Every type whose text carries no date and no amount reads, in English, exactly what Android's
 * code wrote before the move (`LegacyPushText` in the app's tests is that code; the texts here were
 * written from the same XML by its rules: Compose Multiplatform's unescaping, `%<n>$s`). The day
 * count's plural reads in Czech, Russian and Ukrainian exactly what Android's `getQuantityString`
 * picks (CLDR's integer rules). Dates and money are Foundation's, which may differ from `java.time`
 * where their locale data differ (design §11 call 2), so they are not compared here.
 */
class PushWordingIosTest {

    private val bundle: NSBundle =
        requireNotNull(NSBundle.bundleWithPath(PushStringsLocation.DIR)) {
            "no strings at ${PushStringsLocation.DIR}"
        }

    private fun wording(language: String) =
        PushWording(BundleWordingStrings(language, bundle), FoundationReaderClock(language, is24Hour = true))

    private val payload = mapOf("actorName" to "Lana", "subject" to "Dentist")

    @Test
    fun everyTypeWithNoDateOrAmountReadsAsAndroidsEnglish() {
        val english = wording("en")
        for ((type, title, body) in ENGLISH) {
            assertEquals(PushText(title, body), english.compose(type, payload), type)
        }
    }

    @Test
    fun aMissingActorIsTheCoParentAndAnUnknownTypeIsDropped() {
        val english = wording("en")
        assertEquals(
            "Your co-parent added “Dentist”",
            english.compose("event_created", payload - "actorName")?.body
        )
        assertNull(english.compose("a_type_from_a_newer_build", payload))
        assertNull(english.compose(null, payload))
    }

    @Test
    fun aChatMessageIsRelayedAndItsCardWordedInEnglish() {
        val english = wording("en")
        val message = mapOf("actorName" to "Lana", "preview" to "Hi")
        assertEquals(PushText("Lana", "Hi"), english.compose("chat_message", message))
        val card = message + mapOf(
            "activityKind" to "EVENT_CREATED",
            "activityEntityType" to "EVENT",
            "activityEntityId" to "e1",
            "activityTitle" to "Dentist"
        )
        assertEquals(PushText("Lana", "New event: Dentist"), english.compose("chat_message", card))
    }

    @Test
    fun theDayCountPluralReadsAsAndroidsInCzechRussianAndUkrainian() {
        for (plural in PLURALS) {
            val body = wording(plural.language).compose(plural.type, mapOf("dayCount" to "${plural.count}"))?.body
            assertEquals(plural.text, body, "${plural.language} ${plural.type} ${plural.count}")
        }
    }

    @Test
    fun aLanguageWithNoFolderReadsEnglish() {
        val (type, title, body) = ENGLISH.first()
        assertEquals(PushText(title, body), wording("fr").compose(type, payload))
    }

    private data class Plural(val language: String, val type: String, val count: Int, val text: String)

    private companion object {
        /** Each type with no date or amount in its text: its English title and body for [payload]. */
        val ENGLISH = listOf(
            Triple("event_created", "New event", "Lana added “Dentist”"),
            Triple(
                "event_important",
                "Your presence is required",
                "Lana added “Dentist” and marked it important: you are expected there."
            ),
            Triple("event_updated", "Event changed", "Lana changed “Dentist”"),
            Triple("event_deleted", "Event cancelled", "Lana cancelled “Dentist”"),
            Triple("child_info_updated", "Child information updated", "Lana updated information for Dentist"),
            Triple("change_request_created", "Change requested", "Lana asked to change “Dentist”"),
            Triple("change_request_accepted", "Change accepted", "Lana accepted the change to “Dentist”"),
            Triple("change_request_declined", "Change declined", "Lana declined the change to “Dentist”"),
            Triple("change_request_cancelled", "Change request withdrawn", "Lana withdrew the request about “Dentist”"),
            Triple(
                "custody_proposal_proposed",
                "New custody schedule proposed",
                "Your co-parent proposes a new schedule"
            ),
            Triple(
                "custody_proposal_accepted",
                "New custody schedule agreed",
                "Your co-parent agreed to the new schedule"
            ),
            Triple(
                "custody_proposal_declined",
                "Custody proposal turned down",
                "Your co-parent turned down the proposed schedule"
            ),
            Triple(
                "split_ratio_proposed",
                "New expense split proposed",
                "Your co-parent proposes a different split. Open the app to confirm or decline."
            ),
            Triple(
                "split_ratio_accepted",
                "Expense split agreed",
                "Your co-parent agreed to the new split. It applies to expenses from now on."
            ),
            Triple(
                "split_ratio_declined",
                "Expense split turned down",
                "Your co-parent turned down the new split. Nothing has changed."
            ),
            Triple(
                "split_ratio_agreed",
                "Expense split agreed",
                "The split you and your co-parent will share by was set before you paired. Open Settings to see it."
            ),
            Triple(
                "records_shared",
                "Records shared with you",
                "Lana shared their calendar and family records with you"
            ),
            Triple("sticker_received", "A sticker for you", "Lana sent you a sticker"),
            Triple(
                "request_deadline_passed",
                "Lana didn't answer by the deadline",
                "Nothing has changed in the calendar. You can give more time."
            ),
            Triple(
                "request_no_answer",
                "No answer from Lana",
                "The request is closed as “No answer”. Nothing has changed in the calendar."
            ),
            Triple("pairing_accepted", "Invitation accepted", "Lana is now your co-parent in CoPlanly"),
            Triple("pairing_removed", "Co-parent unlinked", "Lana ended the co-parent link"),
            Triple(
                "professional_access_requested",
                "Professional access requested",
                "Lana redeemed an invitation to read the family calendar and parenting plan. It opens only when both" +
                    " parents consent."
            ),
            Triple(
                "calendar_friend_accepted",
                "A family friend joined",
                "Lana can now read the family calendar. Either of you can end it under Family friends."
            ),
        )

        /** The day count's body in the three languages with more than two plural forms. */
        val PLURALS = listOf(
            Plural("cs", "day_swap_group_offered", 1, "Druhý rodič navrhuje vyměnit 1 den"),
            Plural("cs", "day_swap_group_offered", 2, "Druhý rodič navrhuje vyměnit 2 dny"),
            Plural("cs", "day_swap_group_offered", 5, "Druhý rodič navrhuje vyměnit 5 dní"),
            Plural("cs", "day_swap_group_offered", 21, "Druhý rodič navrhuje vyměnit 21 dní"),
            Plural("cs", "day_swap_group_offered", 22, "Druhý rodič navrhuje vyměnit 22 dní"),
            Plural("cs", "day_swap_group_offered", 25, "Druhý rodič navrhuje vyměnit 25 dní"),
            Plural("cs", "day_swap_group_offered", 11, "Druhý rodič navrhuje vyměnit 11 dní"),
            Plural("cs", "day_swap_group_offered", 12, "Druhý rodič navrhuje vyměnit 12 dní"),
            Plural("cs", "day_swap_group_accepted", 1, "Druhý rodič souhlasil s výměnou 1 dne"),
            Plural("cs", "day_swap_group_accepted", 2, "Druhý rodič souhlasil s výměnou 2 dnů"),
            Plural("cs", "day_swap_group_accepted", 5, "Druhý rodič souhlasil s výměnou 5 dnů"),
            Plural("cs", "day_swap_group_accepted", 21, "Druhý rodič souhlasil s výměnou 21 dnů"),
            Plural("cs", "day_swap_group_accepted", 22, "Druhý rodič souhlasil s výměnou 22 dnů"),
            Plural("cs", "day_swap_group_accepted", 25, "Druhý rodič souhlasil s výměnou 25 dnů"),
            Plural("cs", "day_swap_group_accepted", 11, "Druhý rodič souhlasil s výměnou 11 dnů"),
            Plural("cs", "day_swap_group_accepted", 12, "Druhý rodič souhlasil s výměnou 12 dnů"),
            Plural("cs", "day_swap_group_declined", 1, "Druhý rodič odmítl výměnu 1 dne"),
            Plural("cs", "day_swap_group_declined", 2, "Druhý rodič odmítl výměnu 2 dnů"),
            Plural("cs", "day_swap_group_declined", 5, "Druhý rodič odmítl výměnu 5 dnů"),
            Plural("cs", "day_swap_group_declined", 21, "Druhý rodič odmítl výměnu 21 dnů"),
            Plural("cs", "day_swap_group_declined", 22, "Druhý rodič odmítl výměnu 22 dnů"),
            Plural("cs", "day_swap_group_declined", 25, "Druhý rodič odmítl výměnu 25 dnů"),
            Plural("cs", "day_swap_group_declined", 11, "Druhý rodič odmítl výměnu 11 dnů"),
            Plural("cs", "day_swap_group_declined", 12, "Druhý rodič odmítl výměnu 12 dnů"),
            Plural("ru", "day_swap_group_offered", 1, "Второй родитель предлагает обменять 1 день"),
            Plural("ru", "day_swap_group_offered", 2, "Второй родитель предлагает обменять 2 дня"),
            Plural("ru", "day_swap_group_offered", 5, "Второй родитель предлагает обменять 5 дней"),
            Plural("ru", "day_swap_group_offered", 21, "Второй родитель предлагает обменять 21 день"),
            Plural("ru", "day_swap_group_offered", 22, "Второй родитель предлагает обменять 22 дня"),
            Plural("ru", "day_swap_group_offered", 25, "Второй родитель предлагает обменять 25 дней"),
            Plural("ru", "day_swap_group_offered", 11, "Второй родитель предлагает обменять 11 дней"),
            Plural("ru", "day_swap_group_offered", 12, "Второй родитель предлагает обменять 12 дней"),
            Plural("ru", "day_swap_group_accepted", 1, "Второй родитель согласился обменять 1 день"),
            Plural("ru", "day_swap_group_accepted", 2, "Второй родитель согласился обменять 2 дня"),
            Plural("ru", "day_swap_group_accepted", 5, "Второй родитель согласился обменять 5 дней"),
            Plural("ru", "day_swap_group_accepted", 21, "Второй родитель согласился обменять 21 день"),
            Plural("ru", "day_swap_group_accepted", 22, "Второй родитель согласился обменять 22 дня"),
            Plural("ru", "day_swap_group_accepted", 25, "Второй родитель согласился обменять 25 дней"),
            Plural("ru", "day_swap_group_accepted", 11, "Второй родитель согласился обменять 11 дней"),
            Plural("ru", "day_swap_group_accepted", 12, "Второй родитель согласился обменять 12 дней"),
            Plural("ru", "day_swap_group_declined", 1, "Второй родитель отклонил обмен 1 дня"),
            Plural("ru", "day_swap_group_declined", 2, "Второй родитель отклонил обмен 2 дней"),
            Plural("ru", "day_swap_group_declined", 5, "Второй родитель отклонил обмен 5 дней"),
            Plural("ru", "day_swap_group_declined", 21, "Второй родитель отклонил обмен 21 дня"),
            Plural("ru", "day_swap_group_declined", 22, "Второй родитель отклонил обмен 22 дней"),
            Plural("ru", "day_swap_group_declined", 25, "Второй родитель отклонил обмен 25 дней"),
            Plural("ru", "day_swap_group_declined", 11, "Второй родитель отклонил обмен 11 дней"),
            Plural("ru", "day_swap_group_declined", 12, "Второй родитель отклонил обмен 12 дней"),
            Plural("uk", "day_swap_group_offered", 1, "Другий із батьків пропонує обміняти 1 день"),
            Plural("uk", "day_swap_group_offered", 2, "Другий із батьків пропонує обміняти 2 дні"),
            Plural("uk", "day_swap_group_offered", 5, "Другий із батьків пропонує обміняти 5 днів"),
            Plural("uk", "day_swap_group_offered", 21, "Другий із батьків пропонує обміняти 21 день"),
            Plural("uk", "day_swap_group_offered", 22, "Другий із батьків пропонує обміняти 22 дні"),
            Plural("uk", "day_swap_group_offered", 25, "Другий із батьків пропонує обміняти 25 днів"),
            Plural("uk", "day_swap_group_offered", 11, "Другий із батьків пропонує обміняти 11 днів"),
            Plural("uk", "day_swap_group_offered", 12, "Другий із батьків пропонує обміняти 12 днів"),
            Plural("uk", "day_swap_group_accepted", 1, "Другий із батьків погодився обміняти 1 день"),
            Plural("uk", "day_swap_group_accepted", 2, "Другий із батьків погодився обміняти 2 дні"),
            Plural("uk", "day_swap_group_accepted", 5, "Другий із батьків погодився обміняти 5 днів"),
            Plural("uk", "day_swap_group_accepted", 21, "Другий із батьків погодився обміняти 21 день"),
            Plural("uk", "day_swap_group_accepted", 22, "Другий із батьків погодився обміняти 22 дні"),
            Plural("uk", "day_swap_group_accepted", 25, "Другий із батьків погодився обміняти 25 днів"),
            Plural("uk", "day_swap_group_accepted", 11, "Другий із батьків погодився обміняти 11 днів"),
            Plural("uk", "day_swap_group_accepted", 12, "Другий із батьків погодився обміняти 12 днів"),
            Plural("uk", "day_swap_group_declined", 1, "Другий із батьків відхилив обмін 1 дня"),
            Plural("uk", "day_swap_group_declined", 2, "Другий із батьків відхилив обмін 2 днів"),
            Plural("uk", "day_swap_group_declined", 5, "Другий із батьків відхилив обмін 5 днів"),
            Plural("uk", "day_swap_group_declined", 21, "Другий із батьків відхилив обмін 21 дня"),
            Plural("uk", "day_swap_group_declined", 22, "Другий із батьків відхилив обмін 22 днів"),
            Plural("uk", "day_swap_group_declined", 25, "Другий із батьків відхилив обмін 25 днів"),
            Plural("uk", "day_swap_group_declined", 11, "Другий із батьків відхилив обмін 11 днів"),
            Plural("uk", "day_swap_group_declined", 12, "Другий із батьків відхилив обмін 12 днів"),
        )
    }
}
