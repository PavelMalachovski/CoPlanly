package com.coparently.app.domain.school

/**
 * Whether a school connection can still import (MON-8).
 *
 * @property stored How it is written into the connection's stored record. Never renamed.
 */
enum class SchoolConnectionStatus(val stored: String) {
    /** The last update worked, or none has run yet. */
    OK("ok"),

    /**
     * The school refused the stored sign-in (`invalid_grant`): the parent has to type the
     * password once more. Nothing is deleted — the connection, the child it is for and every
     * imported event stay as they are.
     */
    NEEDS_PASSWORD("needs_password"),

    /** The last update failed for another reason, such as no network or a server error. */
    ERROR("error");

    companion object {
        /** The status [stored] names; an unknown value reads as [ERROR]. */
        fun of(stored: String?): SchoolConnectionStatus = entries.firstOrNull { it.stored == stored } ?: ERROR
    }
}

/**
 * One child's link to their school's system, as the screens show it (MON-8).
 *
 * Bakaláři signs a parent in **per child**, so a family with two children at school has two
 * connections. The tokens that keep it signed in are deliberately not here: they stay in the
 * data layer's store and never reach a screen.
 *
 * @property id Stable id of the connection.
 * @property baseUrl The school server's address, `https://…` with no trailing slash.
 * @property username What the parent signs in with, shown so they know which account it is.
 * @property schoolName The school, as the directory or the server named it.
 * @property studentName The child as the school names them, with their class.
 * @property childId The CoPlanly child the imported events are about.
 * @property familyId The family the imported events belong to, or null for an account that had
 *   no family when it connected.
 * @property status Whether it can still import.
 * @property lastSuccessAtMillis When an update last worked, or null when none has.
 */
data class SchoolConnection(
    val id: String,
    val baseUrl: String,
    val username: String,
    val schoolName: String,
    val studentName: String,
    val childId: String,
    val familyId: String?,
    val status: SchoolConnectionStatus,
    val lastSuccessAtMillis: Long?
)
