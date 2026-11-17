package com.coparently.app.pushwording.golden

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import com.coparently.app.data.money.MinorUnits
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.stickers.StickerKind
import com.coparently.app.pushwording.PushWording
import com.coparently.app.testing.RobolectricTestApplication
import com.coparently.app.utils.pushWording
import com.coparently.app.utils.reminderWording
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toKotlinLocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every notification's text from `:push-wording` is, byte for byte, what the code it moved out of
 * wrote ([LegacyPushText], a copy of that code kept as the oracle): every push type, every swap
 * wording, a deadline, an expense with and without a share, every chat card kind, a blank actor, an
 * unknown type, and the three reminders — in the five languages, on a 24-hour and a 12-hour clock
 * (iPhone phase 4 part C, Task 2; design §11 call 2: Android's text does not change).
 *
 * Robolectric for the app's resources, through the same calls production makes
 * (`Context.pushWording()`, `Context.reminderWording()`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class PushWordingGoldenTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private var zoneBefore: java.util.TimeZone? = null

    @Before
    fun pinZone() {
        zoneBefore = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Prague"))
    }

    @After
    fun restoreZone() {
        zoneBefore?.let(java.util.TimeZone::setDefault)
    }

    @Test
    fun everyPushIsWordedAsBefore() {
        forEveryReader { context, label ->
            val now = context.pushWording()
            val before = LegacyPushText.PushNotifier(context)
            for ((name, data) in pushCorpus()) {
                val type = data[PushPayload.TYPE]
                assertEquals(before.compose(type, data), now.compose(type, data), "$label: $name")
            }
        }
    }

    @Test
    fun everyChatCardIsWordedAsBefore() {
        forEveryReader { context, label ->
            val now = context.pushWording()
            val before = LegacyPushText.PushNotifier(context)
            for ((name, data) in cardCorpus()) {
                assertEquals(
                    before.compose(PushPayload.CHAT_MESSAGE, data),
                    now.compose(PushPayload.CHAT_MESSAGE, data),
                    "$label: $name"
                )
            }
        }
    }

    @Test
    fun theThreeRemindersAreWordedAsBefore() {
        forEveryReader { context, label ->
            val now = context.reminderWording()
            for (title in listOf("Dentist", "Zubař „Emma“", "")) {
                for (startTime in listOf("09:30", "3:30 PM", "")) {
                    assertEquals(
                        LegacyPushText.eventReminder(context, title, startTime),
                        now.eventAt(title, startTime),
                        "$label: event $title at $startTime"
                    )
                }
            }
            for ((due, currency, minor) in SUPPORT_CASES) {
                for (payee in NAMES) {
                    val dueMillis = due.toKotlinLocalDate().atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                    assertEquals(
                        LegacyPushText.supportReminder(context, due, currency, minor, payee),
                        now.support(dueMillis, MinorUnits.toDecimal(minor).toPlainString(), currency, payee),
                        "$label: support $due $currency $minor to $payee"
                    )
                }
            }
            for (proposer in NAMES) {
                for (millis in DEADLINES) {
                    assertEquals(
                        LegacyPushText.requestReminder(context, proposer, millis),
                        now.request(proposer, millis),
                        "$label: request from $proposer until $millis"
                    )
                }
            }
        }
    }

    @Test
    fun theServersRequestReminderIsWordedAsThePhonesOwn() {
        // `request_reminder` came after the move (phase 4 part C, Task 3), so the legacy copy has
        // no wording for it: its oracle is the phone's own reminder, whose sentence it repeats.
        forEveryReader { context, label ->
            val push = context.pushWording()
            val local = context.reminderWording()
            for (proposer in NAMES) {
                for (millis in DEADLINES) {
                    val data = buildMap {
                        put(PushPayload.TYPE, PushPayload.REQUEST_REMINDER)
                        proposer?.let { put(PushPayload.ACTOR, it) }
                        put(PushPayload.REQUEST_KIND, "swap")
                        put(PushPayload.ANSWER_BY, "$millis")
                        put(PushPayload.FAMILY_ID, "a__b")
                    }
                    assertEquals(
                        local.request(proposer, millis),
                        push.compose(PushPayload.REQUEST_REMINDER, data),
                        "$label: request reminder from $proposer until $millis"
                    )
                }
            }
            val unreadable = mapOf(PushPayload.ACTOR to "Lana", PushPayload.ANSWER_BY to "soon")
            assertEquals(null, push.compose(PushPayload.REQUEST_REMINDER, unreadable), "$label: unreadable deadline")
        }
    }

    @Test
    fun theCorpusReachesEveryWordedTypeAndEveryCardKind() {
        val types = pushCorpus().mapNotNull { it.second[PushPayload.TYPE] }.toSet()
        val legacyTypes = PushWording.PUSH_TEXT.keys - NEWER_THAN_THE_MOVE
        assertTrue(types.containsAll(legacyTypes), "missing ${legacyTypes - types}")
        val kinds = cardCorpus().mapNotNull { it.second[PushPayload.ACTIVITY_KIND] }.toSet()
        assertTrue(kinds.containsAll(ActivityKind.entries.map { it.name }))
        assertEquals(LegacyPushText.PushNotifier.PUSH_TEXT.keys, legacyTypes)
        assertEquals(LegacyPushText.PushNotifier.ANSWER_BY_TYPES, PushWording.ANSWER_BY_TYPES)
    }

    @Test
    fun everyKeyTheWordingNamesIsOneOfTheAppsResources() {
        // The module reads the app's resources by name (`AndroidWordingStrings`), so a key the app
        // does not hold would fail only on a phone. `push-keys.txt` is the module's list
        // (`tools/test/push-keys.test.js` holds it to the code), and the release build keeps it
        // (`card_push_keep.xml`).
        val file = listOf(File("../push-wording/push-keys.txt"), File("push-wording/push-keys.txt"))
            .first { it.exists() }
        val keys = file.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
        assertTrue(keys.size > PushWording.PUSH_TEXT.size, "push-keys.txt lists ${keys.size} keys")
        for (key in keys) {
            assertTrue(idOf(key, "string") != 0 || idOf(key, "plurals") != 0, "no R.string or R.plurals $key")
        }
    }

    // A key Compose Multiplatform names is the app's resource name (`stringsToAndroidRes`).
    @Suppress("DiscouragedApi")
    private fun idOf(key: String, type: String): Int = app.resources.getIdentifier(key, type, app.packageName)

    // ---- the readers -------------------------------------------------------------------------

    /** Runs [check] in each shipped language, on a 24-hour and a 12-hour device setting. */
    private fun forEveryReader(check: (Context, String) -> Unit) {
        for (hours in listOf("24", "12")) {
            Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, hours)
            for (tag in LANGUAGES) check(inLanguage(tag), "$tag/$hours")
        }
    }

    private fun inLanguage(tag: String): Context {
        val configuration = Configuration(app.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(configuration)
    }

    // ---- the corpus --------------------------------------------------------------------------

    /** A full payload for [type]: every name a type may need. */
    private fun full(type: String?): Map<String, String> = buildMap {
        type?.let { put(PushPayload.TYPE, it) }
        put(PushPayload.ACTOR, "Lana")
        put(PushPayload.SUBJECT, "Dentist „Emma“")
        put(PushPayload.DATE, "2026-10-03")
        put(PushPayload.DAY_COUNT, "3")
        put(PushPayload.ANSWER_BY, DEADLINES.first().toString())
        put(PushPayload.AMOUNT, "1234.5")
        put(PushPayload.SHARE, "617.25")
        put(PushPayload.CURRENCY, "CZK")
        put(PushPayload.TARGET_USER_ID, "me")
        put(PushPayload.FAMILY_ID, "a__b")
    }

    @Suppress("LongMethod")
    private fun pushCorpus(): List<Pair<String, Map<String, String>>> = buildList {
        for (type in PushWording.PUSH_TEXT.keys - NEWER_THAN_THE_MOVE) {
            add("$type full" to full(type))
            add("$type no actor" to full(type) - PushPayload.ACTOR)
            add("$type blank actor" to full(type) + (PushPayload.ACTOR to "  "))
            add("$type no answer-by" to full(type) - PushPayload.ANSWER_BY)
            add("$type unreadable answer-by" to full(type) + (PushPayload.ANSWER_BY to "soon"))
            add("$type bare" to mapOf(PushPayload.TYPE to type))
        }
        for (count in listOf("1", "2", "4", "5", "11", "21", "22", "25", "0", "many")) {
            for (type in listOf(
                PushPayload.DAY_SWAP_GROUP_OFFERED,
                PushPayload.DAY_SWAP_GROUP_ACCEPTED,
                PushPayload.DAY_SWAP_GROUP_DECLINED
            )) {
                add("$type count $count" to full(type) + (PushPayload.DAY_COUNT to count))
            }
        }
        for (date in listOf("2026-02-28", "2028-02-29", "2026-02-30", "not a date", "")) {
            add("swap date $date" to full(PushPayload.DAY_SWAP_ACCEPTED) + (PushPayload.DATE to date))
        }
        add("swap no date" to full(PushPayload.DAY_SWAP_DECLINED) - PushPayload.DATE)
        // Deleted account: the kept-until day from the instant, else the ISO day.
        val deleted = full(PushPayload.COPARENT_ACCOUNT_DELETED)
        add("deleted with instant" to deleted + (PushPayload.RETAINED_UNTIL to "1793570400000"))
        add("deleted near midnight" to deleted + (PushPayload.RETAINED_UNTIL to "1793487600000"))
        add("deleted unreadable instant" to deleted + (PushPayload.RETAINED_UNTIL to "x"))
        add("deleted no date" to deleted - PushPayload.DATE)
        // A new expense.
        val expense = full(PushPayload.EXPENSE_ADDED)
        add("expense no share" to expense - PushPayload.SHARE)
        add("expense unreadable share" to expense + (PushPayload.SHARE to "half"))
        add("expense euros" to expense + (PushPayload.CURRENCY to "EUR"))
        add("expense hryvnias" to expense + (PushPayload.CURRENCY to "UAH") + (PushPayload.AMOUNT to "1000000"))
        add("expense unknown currency" to expense + (PushPayload.CURRENCY to "XXZ"))
        add("expense lower-case currency" to expense + (PushPayload.CURRENCY to "czk"))
        add("expense no currency" to expense - PushPayload.CURRENCY)
        add("expense unreadable amount" to expense + (PushPayload.AMOUNT to "lots"))
        add("expense no amount" to expense - PushPayload.AMOUNT)
        add("expense exponent" to expense + (PushPayload.AMOUNT to "1.5E3"))
        // Every swap offer's wording, with and without its deadline.
        for (type in listOf(PushPayload.DAY_SWAP_OFFERED, PushPayload.DAY_SWAP_GROUP_OFFERED)) {
            val offer = full(type)
            add("$type gives one" to offer + (PushPayload.DATES_TO_YOU to "2026-11-06"))
            add("$type gives two unsorted" to offer + (PushPayload.DATES_TO_YOU to "2026-11-07, 2026-11-06"))
            add("$type gives five" to offer + (PushPayload.DATES_TO_YOU to FIVE_DAYS))
            add("$type asks one" to offer + (PushPayload.DATES_TO_SENDER to "2026-11-14"))
            add("$type asks two" to offer + (PushPayload.DATES_TO_SENDER to "2026-11-14,2026-11-15"))
            add("$type asks five" to offer + (PushPayload.DATES_TO_SENDER to FIVE_DAYS))
            add(
                "$type both" to offer + (PushPayload.DATES_TO_YOU to "2026-11-06") +
                    (PushPayload.DATES_TO_SENDER to "2026-11-14,2026-12-31")
            )
            add(
                "$type both no deadline" to offer - PushPayload.ANSWER_BY + (PushPayload.DATES_TO_YOU to "2026-11-06") +
                    (PushPayload.DATES_TO_SENDER to "2026-11-14")
            )
            add("$type some unreadable" to offer + (PushPayload.DATES_TO_YOU to "2026-11-06,soon,2026-02-30"))
            add("$type all unreadable" to offer + (PushPayload.DATES_TO_YOU to "soon, later"))
            add("$type leap day" to offer + (PushPayload.DATES_TO_SENDER to "2028-02-29"))
            add("$type empty fields" to offer + (PushPayload.DATES_TO_YOU to "") + (PushPayload.DATES_TO_SENDER to ","))
            add(
                "$type gives blank actor" to offer + (PushPayload.ACTOR to "") +
                    (PushPayload.DATES_TO_YOU to "2026-11-06")
            )
        }
        // The deadline sentence after bodies that do and do not end a sentence, at several moments.
        for (millis in DEADLINES) {
            val answerBy = PushPayload.ANSWER_BY to "$millis"
            add("change request at $millis" to full(PushPayload.CHANGE_REQUEST_CREATED) + answerBy)
            add("last chance at $millis" to full(PushPayload.REQUEST_LAST_CHANCE) + answerBy)
        }
        // What is dropped, and the chat message itself.
        add("unknown type" to full("a_type_from_a_newer_build"))
        add("no type" to full(null))
        add("chat message" to chat())
        add("chat message no actor" to chat() - PushPayload.ACTOR)
        add("chat message blank actor" to chat() + (PushPayload.ACTOR to " "))
        add("chat message no preview" to chat() - PushPayload.PREVIEW)
    }

    private fun chat(vararg fields: Pair<String, String>): Map<String, String> = mapOf(
        PushPayload.TYPE to PushPayload.CHAT_MESSAGE,
        PushPayload.ACTOR to "Lana",
        PushPayload.PREVIEW to "New event: Zahnarzt",
        PushPayload.CONVERSATION_ID to "a__b"
    ) + fields

    private fun card(
        kind: String,
        entityType: String,
        title: String?,
        whenIso: String? = null,
        amount: String? = null,
        currency: String? = null
    ): Map<String, String> = chat(
        PushPayload.ACTIVITY_KIND to kind,
        PushPayload.ACTIVITY_ENTITY_TYPE to entityType,
        PushPayload.ACTIVITY_ENTITY_ID to "id1"
    ) + listOfNotNull(
        title?.let { PushPayload.ACTIVITY_TITLE to it },
        whenIso?.let { PushPayload.ACTIVITY_WHEN_ISO to it },
        amount?.let { PushPayload.ACTIVITY_AMOUNT to it },
        currency?.let { PushPayload.ACTIVITY_CURRENCY to it }
    )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun cardCorpus(): List<Pair<String, Map<String, String>>> = buildList {
        for (kind in ActivityKind.entries) {
            val name = kind.name
            when {
                kind == ActivityKind.SETTLE_UP_OWED || kind == ActivityKind.SETTLE_UP_OWING -> {
                    add(name to card(name, "EXPENSE", "2026-10", "2026-10-01", "450.00", "CZK"))
                    add("$name euros" to card(name, "EXPENSE", "2026-10", "2026-10-01", "450.00", "EUR"))
                    add("$name no when" to card(name, "EXPENSE", "2026-10", null, "450.00", "CZK"))
                    add("$name unreadable when" to card(name, "EXPENSE", "2026-10", "October", "450.00", "CZK"))
                    add("$name no currency" to card(name, "EXPENSE", "2026-10", "2026-10-01", "450.00"))
                    add("$name unreadable amount" to card(name, "EXPENSE", "2026-10", "2026-10-01", "lots", "CZK"))
                    add("$name no amount" to card(name, "EXPENSE", "2026-10", "2026-10-01", null, "CZK"))
                    add("$name unknown currency" to card(name, "EXPENSE", "2026-10", "2026-10-01", "450.00", "XXZ"))
                }
                kind == ActivityKind.SUPPORT_PROPOSED || kind == ActivityKind.SUPPORT_ACCEPTED -> {
                    add(name to card(name, "SUPPORT_AGREEMENT", "5", null, "3500.00", "CZK"))
                    add("$name no currency" to card(name, "SUPPORT_AGREEMENT", "5", null, "3500.00"))
                }
                kind.isPayment -> {
                    add(name to card(name, "PAYMENT", "2026-10", null, "450.00", "CZK"))
                    add("$name unreadable month" to card(name, "PAYMENT", "October", null, "450.00", "EUR"))
                    add("$name no amount" to card(name, "PAYMENT", "2026-10"))
                }
                else -> for (type in ActivityEntityType.entries) {
                    add("$name about ${type.name}" to card(name, type.name, "Swimming"))
                    add("$name about ${type.name} no title" to card(name, type.name, null))
                }
            }
        }
        for (date in listOf("2026-10-16", "2026-02-30", "16 October", "")) {
            add("day swap card $date" to card("DAY_SWAP_OFFERED", "DAY_SWAP", date))
        }
        for (wire in StickerKind.entries.map { it.wire } + listOf("a_kind_from_a_newer_build", "")) {
            add("sticker $wire" to card("STICKER_SENT", "STICKER", wire))
        }
        add("custody proposal with family id" to card("CUSTODY_PROPOSED", "CUSTODY_PROPOSAL", "uidA__uidB"))
        add("split proposal" to card("SPLIT_PROPOSED", "SPLIT_PROPOSAL", "", amount = "3500"))
        add("unknown kind" to card("A_KIND_FROM_A_NEWER_BUILD", "EVENT", "Swimming"))
        add("unknown entity" to card("EVENT_CREATED", "A_THING", "Swimming"))
        add("blank entity id" to chat(
            PushPayload.ACTIVITY_KIND to "EVENT_CREATED",
            PushPayload.ACTIVITY_ENTITY_TYPE to "EVENT",
            PushPayload.ACTIVITY_ENTITY_ID to " "
        ))
        add("card with blank actor" to card("EVENT_CREATED", "EVENT", "Zahnarzt") + (PushPayload.ACTOR to ""))
    }

    private companion object {
        val LANGUAGES = listOf("en", "cs", "de", "ru", "uk")

        /**
         * Types worded after the move, which the legacy copy never knew: each has an oracle of its
         * own ([theServersRequestReminderIsWordedAsThePhonesOwn]).
         */
        val NEWER_THAN_THE_MOVE = setOf(PushPayload.REQUEST_REMINDER)

        /**
         * In Prague: 13 October 2026 at 10:00 and at 19:00 (a 12-hour clock's PM), and midnight
         * starting 1 January 2027 (the year changes in the reader's zone, not in UTC).
         */
        val DEADLINES = listOf(1_791_878_400_000L, 1_791_910_800_000L, 1_798_758_000_000L)

        val NAMES = listOf("Lana", null, "", "  ", "Jiří")

        const val FIVE_DAYS = "2026-11-06,2026-11-07,2026-11-08,2026-11-09,2026-11-10"

        val SUPPORT_CASES = listOf(
            Triple(LocalDate.of(2026, 11, 5), "CZK", 350_000L),
            Triple(LocalDate.of(2026, 12, 28), "EUR", 12_345L),
            Triple(LocalDate.of(2027, 3, 29), "UAH", 100_000_000L),
            Triple(LocalDate.of(2026, 10, 25), "RUB", 99L),
            Triple(LocalDate.of(2026, 1, 1), "XXZ", 100L)
        )
    }
}
