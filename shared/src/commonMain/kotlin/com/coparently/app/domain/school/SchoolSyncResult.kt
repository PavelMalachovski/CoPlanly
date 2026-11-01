package com.coparently.app.domain.school

/**
 * What one school update came to (MON-8). Facts, not sentences: the screen words them.
 */
sealed interface SchoolSyncResult {

    /**
     * The update ran.
     *
     * @property created Events created.
     * @property updated Events whose school data changed.
     * @property deleted School-hours events removed because the day has no school any more.
     */
    data class Success(val created: Int, val updated: Int, val deleted: Int) : SchoolSyncResult

    /** The school refused the stored sign-in; the parent has to type the password again. */
    data object NeedsPassword : SchoolSyncResult

    /** The update failed for [reason]; the connection is marked and the next one tries again. */
    data class Failed(val reason: SchoolSyncFailure) : SchoolSyncResult

    /** There is no such connection for the signed-in account, or nobody is signed in. */
    data object NotFound : SchoolSyncResult

    /**
     * The connection names no family, the parent has two or more, and the child's record names
     * none either: nothing was imported, because a guess would share one family's child's school
     * calendar with the other family's adult. The parent connects the school again and chooses.
     */
    data object NeedsFamily : SchoolSyncResult
}

/** Why a school update failed, as far as the parent can act on it. */
enum class SchoolSyncFailure {
    /** No network, or the school's server did not answer. */
    NETWORK,

    /** The school's server answered with an error or with something unreadable. */
    SERVER
}
