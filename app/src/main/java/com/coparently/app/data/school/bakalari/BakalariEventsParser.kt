package com.coparently.app.data.school.bakalari

import com.coparently.app.data.school.bakalari.BakalariJson.bool
import com.coparently.app.data.school.bakalari.BakalariJson.objects
import com.coparently.app.data.school.bakalari.BakalariJson.text
import com.coparently.app.domain.school.SchoolEvent
import com.coparently.app.domain.school.SchoolEventStudent
import com.coparently.app.domain.school.SchoolEventTime

/**
 * Reads `GET /api/3/events` into [SchoolEvent]s (MON-8).
 *
 * Every event visible to the account comes back, other classes' included; which of them belong
 * in the child's calendar is `SchoolEventAudience`'s decision, not the parser's. Teachers and
 * rooms are dropped here. A time block whose start cannot be read is dropped; an event with no
 * id or no readable block is dropped whole.
 */
object BakalariEventsParser {

    /**
     * The events in [json].
     *
     * @throws BakalariException.Malformed when [json] is not a JSON object.
     */
    fun parse(json: String): List<SchoolEvent> =
        BakalariJson.objectOf(json).objects("Events").mapNotNull { event ->
            val id = event.text("Id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val times = event.objects("Times").mapNotNull { time ->
                val startKey = time.text("StartTime") ?: return@mapNotNull null
                val start = BakalariJson.wallClock(startKey) ?: return@mapNotNull null
                SchoolEventTime(
                    wholeDay = time.bool("WholeDay") == true,
                    start = start,
                    end = BakalariJson.wallClock(time.text("EndTime")),
                    startKey = startKey
                )
            }
            if (times.isEmpty()) return@mapNotNull null
            SchoolEvent(
                id = id,
                title = event.text("Title").orEmpty(),
                description = event.text("Description")?.takeIf { it.isNotBlank() }
                    ?: event.text("Note")?.takeIf { it.isNotBlank() },
                times = times,
                classIds = event.objects("Classes").map { it.text("Id").orEmpty() },
                students = event.objects("Students").map { student ->
                    SchoolEventStudent(id = student.text("Id").orEmpty(), name = student.text("Name").orEmpty())
                },
                hasOtherTargets = event.objects("ClassSets").isNotEmpty()
            )
        }
}
