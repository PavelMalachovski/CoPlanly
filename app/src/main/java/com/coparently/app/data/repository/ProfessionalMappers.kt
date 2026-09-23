package com.coparently.app.data.repository

import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole

/**
 * Reading a `professional_grants` document back out of Firestore (MON-18).
 *
 * Pure and separate from the repository for the reason [FriendMappers] is: this decides who may
 * read a family's calendar, and every field arrives as `Any?` from a document another build may
 * have written. **Every decode drops rather than guesses** — a grant with the wrong number of
 * parents, no family, no professional or no end is not a grant.
 */
object ProfessionalMappers {

    /**
     * A [ProfessionalGrant] from its stored map, or null when the document cannot describe one.
     *
     * @param id The document id.
     * @param data The document's data.
     */
    fun grantFrom(id: String, data: Map<String, Any?>?): ProfessionalGrant? {
        if (data == null || id.isBlank()) return null
        val parents = stringList(data["familyParents"])
        val familyId = (data["familyId"] as? String).orEmpty()
        val proUid = (data["proUid"] as? String).orEmpty()
        val expiresAtMillis = (data["expiresAtMillis"] as? Number)?.toLong() ?: 0L
        val complete = parents.size == 2 && familyId.isNotBlank() && proUid.isNotBlank()
        if (!complete || expiresAtMillis <= 0L) return null
        return ProfessionalGrant(
            id = id,
            familyId = familyId,
            familyParents = parents,
            proUid = proUid,
            role = ProfessionalRole.fromWire(data["role"] as? String),
            name = (data["name"] as? String).orEmpty(),
            photoUrl = (data["photoUrl"] as? String)?.takeIf { it.isNotBlank() },
            invitedBy = (data["invitedBy"] as? String).orEmpty(),
            grantedAtMillis = (data["grantedAtMillis"] as? Number)?.toLong() ?: 0L,
            expiresAtMillis = expiresAtMillis,
            // Only the family's own parents count as consent. The rule reads `hasAll(parents)`, so
            // a stray key would open nothing there; here it would print "active" beside it.
            consents = numberMap(data["consents"]).filterKeys { it in parents },
            parentNames = textMap(data["parentNames"]),
            parentSlots = textMap(data["parentSlots"])
        )
    }

    private fun stringList(value: Any?): List<String> =
        (value as? List<*>)?.mapNotNull { (it as? String)?.takeIf { uid -> uid.isNotBlank() } }.orEmpty()

    private fun numberMap(value: Any?): Map<String, Long> {
        val map = value as? Map<*, *> ?: return emptyMap()
        val out = mutableMapOf<String, Long>()
        map.forEach { (key, number) ->
            val millis = (number as? Number)?.toLong()
            if (key is String && millis != null && millis > 0L) out[key] = millis
        }
        return out
    }

    private fun textMap(value: Any?): Map<String, String> {
        val map = value as? Map<*, *> ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        map.forEach { (key, text) -> if (key is String && text is String) out[key] = text }
        return out
    }
}
