package com.coparently.app.domain.school

import java.time.LocalDateTime
import java.util.Locale

/**
 * One event from a school's calendar (MON-8): a trip, a parents' evening, a sports day.
 *
 * @property id The school server's id for the event. Stable per server, not across servers.
 * @property title What the school called it.
 * @property description The school's longer text, or null.
 * @property times Each block of time the event takes. One event can have several — a whole day
 *   and a separate morning — and each becomes its own calendar event.
 * @property classIds The classes it is for. Empty with [students] empty means the whole school.
 * @property students The pupils it names.
 * @property hasOtherTargets Whether it names a group this build cannot match to a child, such as
 *   a set of classes. Such an event is not school-wide, and is not imported.
 */
data class SchoolEvent(
    val id: String,
    val title: String,
    val description: String?,
    val times: List<SchoolEventTime>,
    val classIds: List<String>,
    val students: List<SchoolEventStudent>,
    val hasOtherTargets: Boolean = false
)

/**
 * One block of time of a [SchoolEvent], in the school's own wall clock.
 *
 * @property wholeDay Whether the block is a whole day. The server ends one at 23:59, not at the
 *   next midnight.
 * @property start When it starts.
 * @property end When it ends, or null when the server gave no usable end.
 * @property startKey The start exactly as the server wrote it, which with the event's id makes
 *   the block's identity.
 */
data class SchoolEventTime(
    val wholeDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val startKey: String
)

/**
 * A pupil an event names.
 *
 * @property id The server's id for the pupil.
 * @property name The pupil's name as the server writes it ("Surname Name").
 */
data class SchoolEventStudent(val id: String, val name: String)

/**
 * The child a connection reads for, as the school's server describes them.
 *
 * @property userUid The account's `UserUID`.
 * @property fullName The child's name and class ("Surname Name, 5.A").
 * @property classId The id of the child's class, or blank when the server sent none.
 */
data class SchoolStudent(
    val userUid: String,
    val fullName: String,
    val classId: String
) {
    /** [fullName] without the class after the last comma. */
    val name: String get() = fullName.substringBeforeLast(',').trim()
}

/**
 * Which school events belong in a child's calendar (MON-8, owner decision).
 *
 * The child's own class, the child by name, or the whole school — and nothing else. Another
 * class's trip is not this child's business, and a parent reading "5.B goes to the theatre" on
 * their own calendar would reasonably take it for their child's.
 */
object SchoolEventAudience {

    /**
     * Whether [event] is for [student].
     *
     * A pupil is recognised by id — the part of `UserUID` after the slash, or the whole of it —
     * or by name. The documentation names no field that ties the pupil in `Students` to the
     * account, so both are tried; the name is compared ignoring case and repeated spaces.
     */
    fun isForChild(event: SchoolEvent, student: SchoolStudent): Boolean {
        if (event.students.any { it.matches(student) }) return true
        if (student.classId.isNotBlank() && student.classId in event.classIds) return true
        return event.classIds.isEmpty() && event.students.isEmpty() && !event.hasOtherTargets
    }

    private fun SchoolEventStudent.matches(student: SchoolStudent): Boolean {
        val ids = setOf(student.userUid, student.userUid.substringAfterLast('/')).filter { it.isNotBlank() }
        if (id.isNotBlank() && id in ids) return true
        val mine = normalise(student.name)
        return mine.isNotEmpty() && normalise(name) == mine
    }

    private fun normalise(name: String): String =
        name.trim().split(Regex("\\s+")).joinToString(" ").lowercase(Locale.ROOT)
}
