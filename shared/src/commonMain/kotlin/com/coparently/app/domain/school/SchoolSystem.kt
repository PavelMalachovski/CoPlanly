package com.coparently.app.domain.school

/**
 * Which school information system a connection reads.
 *
 * @property stored How it is written in a connection's record and in an event id's hash. Never
 *   renamed: renaming one would give every imported event a new id.
 */
enum class SchoolSystem(val stored: String) {
    /** Bakaláři; what every connection made before EduPage is. */
    BAKALARI("bakalari"),

    /** EduPage. */
    EDUPAGE("edupage");

    companion object {
        /** The system [stored] names; null or an unknown value is [BAKALARI]. */
        fun of(stored: String?): SchoolSystem = entries.firstOrNull { it.stored == stored } ?: BAKALARI
    }
}
