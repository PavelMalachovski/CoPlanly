package com.coparently.app.domain.professionals

/**
 * What a professional is to the family (MON-18). A label for the parents' list, never a
 * permission: every role reads exactly the same two things.
 *
 * @property wire The stored value — `firestore.rules`' `isProfessionalRole` and
 *   `PROFESSIONAL_ROLES` in `functions/index.js` list the same five, and none is ever renamed.
 */
enum class ProfessionalRole(val wire: String) {
    /** A family mediator. */
    MEDIATOR("mediator"),

    /** Either parent's lawyer. */
    LAWYER("lawyer"),

    /** A guardian ad litem (in Czechia, the court-appointed "kolizní opatrovník"). */
    GUARDIAN_AD_LITEM("guardian_ad_litem"),

    /** A family or child therapist. */
    THERAPIST("therapist"),

    /** Anybody else the two parents agree on. */
    OTHER("other");

    companion object {
        /** The role stored as [value], or [OTHER] for anything this build does not know. */
        fun fromWire(value: String?): ProfessionalRole =
            entries.firstOrNull { it.wire == value } ?: OTHER
    }
}

/**
 * A professional's read access to one family's calendar, parenting plan and custody schedule
 * (MON-18). Mirrors `professional_grants/{familyId}__{proUid}`.
 *
 * Beside the calendar friend (item 16), and different from it in the one way that matters: a
 * friend is let in by either parent, a professional by **both**. [consents] holds one key per
 * parent who has said yes, each written by that parent alone; the grant opens nothing until it
 * holds both. Revoking needs only one — either parent deletes the document.
 *
 * @property id The document id, `{familyId}__{proUid}`.
 * @property familyId The one family this grant reads. Never another of either parent's families.
 * @property familyParents The family's two parents, sorted.
 * @property proUid The professional's own uid.
 * @property role What they are; see [ProfessionalRole].
 * @property name Their display name, copied at acceptance so neither side reads the other's profile.
 * @property photoUrl Their Google avatar at acceptance, or null.
 * @property invitedBy The parent who made the invitation — whose consent came with it.
 * @property grantedAtMillis When the code was redeemed, epoch millis.
 * @property expiresAtMillis When access ends, epoch millis. Never absent: see [ProfessionalGrantPolicy].
 * @property consents Parent uid → when that parent consented, epoch millis.
 * @property parentNames Parent uid → display name, copied at acceptance for the professional's view.
 * @property parentSlots Parent uid → `"mom"`/`"dad"` slot id, so the professional can say whose day
 *   a custody day is without reading either profile.
 */
data class ProfessionalGrant(
    val id: String,
    val familyId: String,
    val familyParents: List<String>,
    val proUid: String,
    val role: ProfessionalRole,
    val name: String,
    val photoUrl: String? = null,
    val invitedBy: String,
    val grantedAtMillis: Long,
    val expiresAtMillis: Long,
    val consents: Map<String, Long> = emptyMap(),
    val parentNames: Map<String, String> = emptyMap(),
    val parentSlots: Map<String, String> = emptyMap()
) {
    /** The display name of the parent holding [slot] (`"mom"`/`"dad"`), or null when unknown. */
    fun parentNameForSlot(slot: String): String? =
        parentSlots.entries.firstOrNull { it.value == slot }?.key
            ?.let { uid -> parentNames[uid]?.takeIf { it.isNotBlank() } }
}

/** Where a grant stands, from one viewer's side. */
enum class ProfessionalGrantStatus {
    /** The co-parent has consented and this parent has not: the one status that asks them to act. */
    WAITING_FOR_YOU,

    /** This parent has consented and the co-parent has not. On the professional's side: either. */
    WAITING_FOR_CO_PARENT,

    /** Both parents consented and the end has not come. */
    ACTIVE,

    /** The end has come, whatever the consents say. */
    EXPIRED
}

/**
 * Whether a professional grant opens anything — the single statement the app, `firestore.rules`
 * (`professionalGrantActive`) and the nightly sweep must agree with.
 *
 * The same strict comparison as [com.coparently.app.domain.friends.CalendarFriendPolicy]: a grant
 * ending at noon is expired at noon. **Fails closed**: a missing or non-positive end is expired,
 * never unlimited, and a grant is active only with a consent from each of its two parents.
 */
object ProfessionalGrantPolicy {

    /** The ceiling on a grant's length; `PROFESSIONAL_MAX_DAYS` server-side, and the rules' own. */
    const val MAX_DURATION_DAYS: Long = 180L

    /** True once [grant] no longer admits a read at [nowMillis]. */
    fun isExpired(grant: ProfessionalGrant, nowMillis: Long): Boolean =
        grant.expiresAtMillis <= 0L || grant.expiresAtMillis <= nowMillis

    /** True when both of the family's two parents have consented. */
    fun hasBothConsents(grant: ProfessionalGrant): Boolean =
        grant.familyParents.size == 2 && grant.familyParents.all { it in grant.consents }

    /** True when [grant] admits a read at [nowMillis]: both consents, and not expired. */
    fun isActive(grant: ProfessionalGrant, nowMillis: Long): Boolean =
        hasBothConsents(grant) && !isExpired(grant, nowMillis)

    /**
     * Where [grant] stands for [viewerUid] at [nowMillis].
     *
     * A viewer who is not one of the two parents — the professional — never sees
     * [ProfessionalGrantStatus.WAITING_FOR_YOU]: there is nothing they can do about it.
     */
    fun statusFor(grant: ProfessionalGrant, viewerUid: String?, nowMillis: Long): ProfessionalGrantStatus =
        when {
            isExpired(grant, nowMillis) -> ProfessionalGrantStatus.EXPIRED
            hasBothConsents(grant) -> ProfessionalGrantStatus.ACTIVE
            viewerUid != null && viewerUid in grant.familyParents && viewerUid !in grant.consents ->
                ProfessionalGrantStatus.WAITING_FOR_YOU
            else -> ProfessionalGrantStatus.WAITING_FOR_CO_PARENT
        }
}

private const val MONTH_DAYS: Long = 30L
private const val QUARTER_DAYS: Long = 90L

// A day under the ceiling, not on it: the invitation rule compares the end with the server's
// clock, and a phone running a few minutes fast would otherwise mint an offer the rule refuses.
private const val HALF_YEAR_DAYS: Long = ProfessionalGrantPolicy.MAX_DURATION_DAYS - 1

/**
 * The lengths a parent may offer. The longest sits just under the ceiling; there is deliberately no
 * "until revoked".
 *
 * @property days How long the grant lasts from the moment the invitation is made.
 */
enum class ProfessionalAccessDuration(val days: Long) {
    /** A short mediation. */
    MONTH(MONTH_DAYS),

    /** The default: a mediation or an assessment. */
    QUARTER(QUARTER_DAYS),

    /** Just under the ceiling — a custody case. */
    HALF_YEAR(HALF_YEAR_DAYS);

    /** The end to put on an invitation made at [nowMillis], epoch millis. */
    fun expiryFrom(nowMillis: Long): Long = nowMillis + days * MILLIS_PER_DAY

    companion object {
        /** What the invite sheet starts on. */
        val DEFAULT = QUARTER

        private const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000
    }
}
