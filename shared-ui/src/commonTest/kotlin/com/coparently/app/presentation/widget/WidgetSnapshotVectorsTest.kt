package com.coparently.app.presentation.widget

import com.coparently.app.domain.serialization.StoredJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The text [StoredJson] writes for a [WidgetSnapshot], pinned as literals on every platform it runs
 * on, as `StoredJsonVectorsTest` in `:shared` pins the shared module's models (which cannot see this
 * module's types).
 *
 * Each expected string is **Gson 2.11.0's own output** for the same value, obtained by running a
 * default `Gson()` (HTML escaping on, as the app's call sites had it, so `&` is written `\u0026`) on
 * the JVM over a twin of each class (same field names) and pasting the result — never written by
 * hand — with each class's keys sorted by name, as Gson wrote them on a phone (`GsonFieldOrder`).
 * The iPhone writes this file and its widget extension reads it, so the simulator is held to the
 * same bytes. Each vector is also read back, to the value it was written from.
 */
class WidgetSnapshotVectorsTest {

    @Test
    fun aSignedInSnapshotWritesWhatGsonWrote() {
        val title = "Today · Wed, 18 Nov"
        val custody = WidgetSnapshotLine("Today with you", WidgetColour(light = "#A0124F", dark = "#FF99BB"))
        val window = WidgetSnapshotLine(
            "15:00–19:00 with Bob",
            WidgetColour(light = "#1550C0", dark = "#9EC2FF")
        )
        val handover = WidgetSnapshotLine("Handover to Bob in 5 days", colour = null)
        val dentist = WidgetSnapshotEvent(
            time = "09:00",
            title = "Dentist & check-up",
            mark = WidgetColour(light = "#E91E63", dark = "#FF4081")
        )
        val snapshot = WidgetSnapshot(
            writtenAtMillis = WRITTEN_AT,
            language = "en",
            signedIn = true,
            days = listOf(
                WidgetDay(
                    date = "2026-11-18",
                    compact = WidgetDayLines(title, custody, listOf(window), handover, emptyList(), "1 event today"),
                    compactLarge = WidgetDayLines(title, custody, emptyList(), handover, emptyList(), null),
                    tall = WidgetDayLines(title, custody, listOf(window), handover, listOf(dentist), null)
                )
            ),
            staleLine = "Open CoPlanly to update"
        )
        vector(
            snapshot,
            """{"days":[{"compact":{"custody":{"colour":{"dark":"#FF99BB","light":"#A0124F"},""" +
                """"text":"Today with you"},"events":[],"footer":"1 event today",""" +
                """"handover":{"text":"Handover to Bob in 5 days"},"title":"Today · Wed, 18 Nov",""" +
                """"windows":[{"colour":{"dark":"#9EC2FF","light":"#1550C0"},"text":"15:00–19:00 with Bob"}]},""" +
                """"compactLarge":{"custody":{"colour":{"dark":"#FF99BB","light":"#A0124F"},""" +
                """"text":"Today with you"},"events":[],"handover":{"text":"Handover to Bob in 5 days"},""" +
                """"title":"Today · Wed, 18 Nov","windows":[]},"date":"2026-11-18",""" +
                """"tall":{"custody":{"colour":{"dark":"#FF99BB","light":"#A0124F"},"text":"Today with you"},""" +
                """"events":[{"mark":{"dark":"#FF4081","light":"#E91E63"},"time":"09:00",""" +
                """"title":"Dentist \u0026 check-up"}],"handover":{"text":"Handover to Bob in 5 days"},""" +
                """"title":"Today · Wed, 18 Nov","windows":[{"colour":{"dark":"#9EC2FF","light":"#1550C0"},""" +
                """"text":"15:00–19:00 with Bob"}]}}],"language":"en","signedIn":true,""" +
                """"staleLine":"Open CoPlanly to update","version":1,"writtenAtMillis":1763460000000}"""
        )
    }

    @Test
    fun aSignedOutSnapshotLeavesItsNullsOut() {
        val title = "Today · Wed, 18 Nov"
        val lines = WidgetDayLines(title, null, emptyList(), null, emptyList(), "Sign in to CoPlanly to see your day.")
        vector(
            WidgetSnapshot(
                writtenAtMillis = WRITTEN_AT,
                language = "en",
                signedIn = false,
                days = listOf(WidgetDay("2026-11-18", lines, lines, lines)),
                staleLine = "Open CoPlanly to update"
            ),
            """{"days":[{"compact":{"events":[],"footer":"Sign in to CoPlanly to see your day.",""" +
                """"title":"Today · Wed, 18 Nov","windows":[]},"compactLarge":{"events":[],""" +
                """"footer":"Sign in to CoPlanly to see your day.","title":"Today · Wed, 18 Nov","windows":[]},""" +
                """"date":"2026-11-18","tall":{"events":[],"footer":"Sign in to CoPlanly to see your day.",""" +
                """"title":"Today · Wed, 18 Nov","windows":[]}}],"language":"en","signedIn":false,""" +
                """"staleLine":"Open CoPlanly to update","version":1,"writtenAtMillis":1763460000000}"""
        )
    }

    private fun vector(value: WidgetSnapshot, expected: String) {
        assertEquals(expected, StoredJson.encode(WidgetSnapshot.serializer(), value))
        assertEquals(value, StoredJson.decode(WidgetSnapshot.serializer(), expected))
    }

    private companion object {
        const val WRITTEN_AT = 1_763_460_000_000L
    }
}
