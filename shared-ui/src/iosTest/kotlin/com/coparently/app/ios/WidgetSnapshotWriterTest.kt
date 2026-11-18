package com.coparently.app.ios

import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.widget.WidgetClock
import com.coparently.app.presentation.widget.WidgetSnapshot
import com.coparently.app.pushwording.WordingStrings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Today widget's snapshot as the iPhone writes it into the App Group (phase 4 part D): through a
 * `.part` file and a rename, a reload only when what the widget draws changed, the signed-out
 * snapshot naming nothing of the account that left, and a change of language written again.
 *
 * Over a temporary directory of the test's own, with the sources and the wording as fakes, so no
 * Room, Firebase or App Group is touched.
 */
class WidgetSnapshotWriterTest {

    private val directory = Path(NSTemporaryDirectory(), "widget-${NSUUID().UUIDString}")
    private val reload = CountingReload()
    private val sources = FakeSources()
    private val wording = FakeWording()
    private val writer = WidgetSnapshotWriter(directory, reload, sources, wording)

    @AfterTest
    fun removeTheDirectory() {
        NSFileManager.defaultManager.removeItemAtPath(directory.toString(), error = null)
    }

    @Test
    fun writesThroughAPartFile() = runTest {
        assertTrue(writer.write())

        assertTrue(SystemFileSystem.exists(Path(directory, WidgetSnapshotWriter.FILE_NAME)))
        assertFalse(SystemFileSystem.exists(Path(directory, WidgetSnapshotWriter.PART_NAME)))
        assertEquals(1, reload.count)
        assertTrue(stored().signedIn)
    }

    @Test
    fun unchangedSnapshotSkipsTheReload() = runTest {
        assertTrue(writer.write())
        // A later moment, nothing else: `writtenAtMillis` is not what the widget draws.
        sources.now += 60_000L

        assertFalse(writer.write())

        assertEquals(1, reload.count)
    }

    @Test
    fun widgetSnapshotOfAnotherAccountIsNeverKept() = runTest {
        writer.write()
        val signedIn = SystemFileSystem.source(Path(directory, WidgetSnapshotWriter.FILE_NAME)).buffered()
            .use { it.readString() }
        assertTrue("Bob" in signedIn && "Dentist" in signedIn, "the account's day was written: $signedIn")

        writer.writeSignedOut()

        val text = SystemFileSystem.source(Path(directory, WidgetSnapshotWriter.FILE_NAME)).buffered()
            .use { it.readString() }
        val snapshot = stored()
        assertFalse(snapshot.signedIn)
        assertEquals(1, snapshot.days.size)
        listOf("Alice", "Bob", "Dentist", UID).forEach { word ->
            assertFalse(word in text, "the signed-out snapshot names $word: $text")
        }
        assertEquals(2, reload.count)
    }

    @Test
    fun languageChangeRewritesTheSnapshot() = runTest {
        wording.tag = "en"
        writer.write()
        wording.tag = "cs"

        assertTrue(writer.write())

        assertEquals(2, reload.count)
        assertEquals("cs", stored().language)
    }

    private fun stored(): WidgetSnapshot {
        val text = SystemFileSystem.source(Path(directory, WidgetSnapshotWriter.FILE_NAME)).buffered()
            .use { it.readString() }
        return StoredJson.decode(WidgetSnapshot.serializer(), text)
    }

    private class CountingReload : WidgetReload {
        var count = 0

        override fun reloadAll() {
            count++
        }
    }

    /** Alice signed in, a week-on-week-off pattern that gives her today, and a dentist today. */
    private class FakeSources : WidgetSources {
        var now = 1_000L

        override fun signedInUid(): String = UID

        override fun today(): LocalDate = TODAY

        override fun nowMillis(): Long = now

        override suspend fun eventsOn(day: LocalDate): List<Event> =
            if (day == TODAY) listOf(dentist) else emptyList()

        override suspend fun custody(): Pair<CustodyModel?, Map<String, DayOverride>> = pattern to emptyMap()

        override fun parents(uid: String): Parents? = if (uid == UID) parents else null

        private val dentist = LocalDateTime(TODAY, LocalTime(14, 0)).let { start ->
            Event(
                id = "e1",
                title = "Dentist",
                startDateTime = start,
                eventType = "general",
                parentOwner = "mom",
                createdAt = start,
                updatedAt = start
            )
        }

        private val pattern = CustodyModel(
            id = "m1",
            modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
            patternDays = 14,
            momDayIndices = (0..6).toSet(),
            startDate = LocalDate(2026, 8, 31)
        )

        private val parents = Parents(
            me = NamedParent(uid = UID, slot = "mom", name = "Alice"),
            coParent = NamedParent(uid = "uid-dad", slot = "dad", name = "Bob"),
            isPaired = true,
            loaded = true
        )
    }

    /** Every string as "key(args)", in a language the test sets, and ISO dates and times. */
    private class FakeWording : WidgetWording {
        var tag = "en"

        override fun language(): String = tag

        override fun strings(language: String): WordingStrings = object : WordingStrings {
            override fun string(key: String, vararg args: Any): String =
                "$language:$key(${args.joinToString(",")})"

            override fun plural(key: String, count: Int, vararg args: Any): String =
                "$language:$key(${args.joinToString(",")})"
        }

        override fun clock(language: String): WidgetClock = object : WidgetClock {
            override fun titleDate(date: LocalDate): String = date.toString()

            override fun time(time: LocalTime): String = time.toString()
        }
    }

    private companion object {
        const val UID = "uid-mom"

        // Wednesday 2 September 2026, in Alice's week of the pattern above.
        val TODAY = LocalDate(2026, 9, 2)
    }
}
