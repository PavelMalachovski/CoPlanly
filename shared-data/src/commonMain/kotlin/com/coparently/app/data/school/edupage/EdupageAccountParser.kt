package com.coparently.app.data.school.edupage

import com.coparently.app.data.school.edupage.EdupageJson.entry
import com.coparently.app.data.school.edupage.EdupageJson.items
import com.coparently.app.data.school.edupage.EdupageJson.obj
import com.coparently.app.data.school.edupage.EdupageJson.text
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One pupil of an EduPage account.
 *
 * @property studentId EduPage's id of the pupil, the one its child switch takes.
 * @property name First and last name.
 * @property className The class's short name, empty when unknown.
 */
data class EdupageChild(val studentId: String, val name: String, val className: String)

/**
 * What the sign-in page says about the account.
 *
 * @property schoolName The school's name, empty when the page names none.
 * @property displayName The signed-in person's name, empty when the page names none.
 * @property isParent True for a parent account (`Rodic…`), false for a pupil's own.
 * @property children The pupils the account reads: the parent's children, or the pupil itself.
 * @property year The starting year of the school year.
 */
data class EdupageAccount(
    val schoolName: String,
    val displayName: String,
    val isParent: Boolean,
    val children: List<EdupageChild>,
    val year: Int
)

/** Reads an [EdupageSession.userhome] into an [EdupageAccount]; every field is read leniently. */
object EdupageAccountParser {

    private const val PARENT_PREFIX = "Rodic"

    /** The account [userhome] describes. */
    fun parse(userhome: JsonObject): EdupageAccount {
        val userId = userhome.text("userid").orEmpty()
        val isParent = userId.startsWith(PARENT_PREFIX)
        val dbi = userhome.obj("dbi")
        val children = if (dbi == null) {
            emptyList()
        } else if (isParent) {
            dbi.items("students").mapNotNull { childOf(it, dbi) }
        } else {
            listOfNotNull(ownChild(userId, dbi))
        }
        return EdupageAccount(
            schoolName = schoolName(userhome),
            displayName = displayName(userhome),
            isParent = isParent,
            children = children,
            year = userhome.obj("dp")?.text("year")?.toIntOrNull() ?: 0
        )
    }

    private fun ownChild(userId: String, dbi: JsonObject): EdupageChild? {
        val id = userId.removePrefix("Student").takeIf { it.isNotBlank() } ?: return null
        val row = dbi.entry("students", id) ?: return null
        return childOf(row.withId(id), dbi)
    }

    private fun JsonObject.withId(id: String): JsonObject =
        if (text("id") != null) this else JsonObject(this + ("id" to JsonPrimitive(id)))

    private fun childOf(row: JsonObject, dbi: JsonObject): EdupageChild? {
        val id = row.text("id")?.takeIf { it.isNotBlank() } ?: return null
        val name = listOfNotNull(row.text("firstname"), row.text("lastname"))
            .joinToString(" ").trim()
        val classId = row.text("classid").orEmpty()
        val className = dbi.entry("classes", classId)?.text("short").orEmpty()
        return EdupageChild(id, name, className)
    }

    private fun schoolName(userhome: JsonObject): String =
        listOf("schoolName", "school_name", "nazov_skoly")
            .firstNotNullOfOrNull { userhome.text(it)?.takeIf(String::isNotBlank) }
            ?: userhome.obj("school")?.text("name").orEmpty()

    private fun displayName(userhome: JsonObject): String {
        val row = userhome.obj("userrow")
        val full = listOfNotNull(row?.text("firstname"), row?.text("lastname")).joinToString(" ").trim()
        return full.ifBlank { userhome.text("user_name") ?: userhome.text("meno").orEmpty() }
    }
}
