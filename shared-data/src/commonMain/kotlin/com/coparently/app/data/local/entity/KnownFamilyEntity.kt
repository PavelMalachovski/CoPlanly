package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What this phone learnt about one family while it was live (schema 46, 25 October 2026).
 *
 * Two questions need it after the family's own documents stop answering:
 *
 * - **Who the family was with.** After an unpair the co-parent's `users/{uid}` is no longer
 *   readable (the rule is `isPartnerOf`), so Family → Earlier families names an ended family by the
 *   name kept here. A family that ended before this table existed has no row, and is named without
 *   a person.
 * - **Which slot each parent holds in it** (per-family slots). `families/{id}.slots` is the truth
 *   for a live family; the copy here answers offline and for a family whose document is gone.
 *
 * A cache of server answers, never uploaded: no outbox column. Every column but the id may be
 * null, because the name and the slots arrive from two listeners at different times.
 *
 * @property familyId `FamilyKey.of` the two parents.
 * @property coParentUid The other parent of the family, as seen from the signed-in account.
 * @property coParentName Their name when it was last read.
 * @property mySlot The signed-in parent's slot in this family (`"mom"` or `"dad"`, the stored
 *   identifiers — never shown).
 * @property coParentSlot The co-parent's slot in this family.
 * @property updatedAtMillis When the row was last written.
 */
@Entity(tableName = "known_families")
data class KnownFamilyEntity(
    @PrimaryKey
    val familyId: String,
    val coParentUid: String,
    val coParentName: String? = null,
    val mySlot: String? = null,
    val coParentSlot: String? = null,
    val updatedAtMillis: Long
)
