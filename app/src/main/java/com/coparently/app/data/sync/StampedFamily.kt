package com.coparently.app.data.sync

/**
 * The family a record this phone writes back carries when the server and the phone disagree only
 * in that the server has stamped it.
 *
 * `stampOwnBlankFamilyIds` stamps a record on the evidence [FamilyIdBackfill] reads, and the rules
 * refuse a write that blanks a stamp — the creator's too. So a phone that missed its own backfill,
 * and still holds the record unstamped, takes the server's stamp rather than sending `""` over it
 * on every pass, which would leave the record queued for ever (isolation audit, October 2026).
 */
internal object StampedFamily {

    /** This phone's family when it has one, else the server's stamp, else null. */
    fun of(local: String?, remote: String?): String? =
        local?.takeIf { it.isNotBlank() } ?: remote?.takeIf { it.isNotBlank() }
}
