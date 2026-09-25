package com.coparently.app.data.school.bakalari

import com.coparently.app.data.school.bakalari.BakalariJson.obj
import com.coparently.app.data.school.bakalari.BakalariJson.objects
import com.coparently.app.data.school.bakalari.BakalariJson.strings
import com.coparently.app.data.school.bakalari.BakalariJson.text
import com.coparently.app.domain.school.SchoolStudent

/**
 * Who a Bakaláři login belongs to, from `GET /api/3/user` (MON-8).
 *
 * A parent's login is bound to **one** child: the response carries that child's name and class,
 * and no endpoint lists siblings. That is why a connection is one child.
 *
 * @property student The child, for matching the school's events to them.
 * @property className The class's short name ("5.A"), or blank.
 * @property schoolName The school as the server names itself, or blank.
 * @property canReadTimetable Whether the account has the timetable right.
 * @property canReadEvents Whether the account has the events right.
 */
data class BakalariAccount(
    val student: SchoolStudent,
    val className: String,
    val schoolName: String,
    val canReadTimetable: Boolean,
    val canReadEvents: Boolean
) {
    /** "Surname Name, class", the way the server writes it. */
    val displayName: String get() = student.fullName

    companion object {
        /**
         * The account [json] describes.
         *
         * @throws BakalariException.Malformed when [json] is not a JSON object.
         */
        fun parse(json: String): BakalariAccount {
            val root = BakalariJson.objectOf(json)
            val modules = root.objects("EnabledModules").associate { module ->
                module.text("Module").orEmpty() to module.strings("Rights")
            }
            val schoolClass = root.obj("Class")
            return BakalariAccount(
                student = SchoolStudent(
                    userUid = root.text("UserUID").orEmpty(),
                    fullName = root.text("FullName").orEmpty().trim(),
                    classId = schoolClass?.text("Id").orEmpty()
                ),
                className = schoolClass?.text("Abbrev").orEmpty().trim(),
                schoolName = root.text("SchoolOrganizationName").orEmpty().trim(),
                canReadTimetable = "ShowTimetable" in modules["Timetable"].orEmpty(),
                canReadEvents = "ShowEvents" in modules["Events"].orEmpty()
            )
        }
    }
}
