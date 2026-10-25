package com.coparently.app.presentation.family

import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.RecordPlace
import com.coparently.app.data.family.placementFamilyId
import com.coparently.app.data.local.entity.KnownFamilyEntity
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.Pet
import java.time.LocalDate

/**
 * One family this parent was in and is no longer, with everything this phone holds of it — what
 * Family → Earlier families shows, read-only (owner decision, 25 October 2026: "full isolation").
 *
 * @property familyId The ended family.
 * @property coParentUid The other parent of it.
 * @property coParentName Their name as this phone kept it while the family was live
 *   (`known_families`), or null when it never did — a family that ended before that table.
 * @property lastDay The latest day any of its records is about, or null when it has none here.
 */
data class EarlierFamily(
    val familyId: String,
    val coParentUid: String,
    val coParentName: String?,
    val events: List<Event> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val children: List<ChildInfo> = emptyList(),
    val pets: List<Pet> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val lastDay: LocalDate? = null
) {
    /** Whether this phone holds none of its records — only its documents and its export remain. */
    val holdsNoRecords: Boolean
        get() = listOf(events, expenses, children, pets, journal).all { it.isEmpty() }
}

/** Every record this phone holds, in every family, for [EarlierFamilies.of]. */
data class EveryFamilyRecords(
    val events: List<Event> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val children: List<ChildInfo> = emptyList(),
    val pets: List<Pet> = emptyList(),
    val journal: List<JournalEntry> = emptyList()
)

/** Which families ended, and what each holds. Pure, so the JVM tests reach every rule. */
object EarlierFamilies {

    /**
     * The ended families of the parent [view] is about, newest first.
     *
     * A family has ended when a record or a kept row names it, it names this parent, and it is
     * neither the family on screen nor another live one ([RecordPlace.EARLIER_FAMILY], the rule
     * every screen applies). A record of no family belongs where [FamilyView.placeOf] puts it: a
     * co-parent's to the family with them, this parent's own to none.
     */
    fun of(view: FamilyView, records: EveryFamilyRecords, known: List<KnownFamilyEntity>): List<EarlierFamily> {
        val me = view.me ?: return emptyList()
        fun familyOf(familyId: String?, author: String?): String? {
            if (view.placeOf(familyId, author) != RecordPlace.EARLIER_FAMILY) return null
            return familyId?.takeIf { it.isNotBlank() } ?: author?.let { FamilyKey.orNull(me, it) }
        }
        val events = records.events.groupBy { familyOf(it.placementFamilyId, it.createdByFirebaseUid) }
        val expenses = records.expenses.groupBy { familyOf(it.familyId, it.createdByFirebaseUid) }
        val children = records.children.groupBy { familyOf(it.familyId, it.createdByFirebaseUid) }
        val pets = records.pets.groupBy { familyOf(it.familyId, it.createdByFirebaseUid) }
        val journal = records.journal.groupBy { familyOf(it.familyId, it.createdByFirebaseUid) }
        val keptNames = known.associateBy { it.familyId }
        val keptEnded = known.map { it.familyId }.filter { view.placeOf(it) == RecordPlace.EARLIER_FAMILY }
        val ended = (events.keys + expenses.keys + children.keys + pets.keys + journal.keys + keptEnded)
            .filterNotNull()
            .toSet()
        return ended.mapNotNull { family ->
            val other = FamilyKey.membersOf(family)?.toList()?.singleOrNull { it != me } ?: return@mapNotNull null
            val ofFamily = EarlierFamily(
                familyId = family,
                coParentUid = other,
                coParentName = keptNames[family]?.coParentName?.trim()?.takeIf { it.isNotEmpty() },
                events = events[family].orEmpty().sortedByDescending { it.startDateTime },
                expenses = expenses[family].orEmpty().sortedByDescending { it.date },
                children = children[family].orEmpty().sortedBy { it.childName },
                pets = pets[family].orEmpty().sortedBy { it.name },
                journal = journal[family].orEmpty().sortedByDescending { it.entryDate }
            )
            ofFamily.copy(lastDay = lastDayOf(ofFamily))
        }.sortedWith(compareByDescending<EarlierFamily> { it.lastDay }.thenBy { it.familyId })
    }

    private fun lastDayOf(family: EarlierFamily): LocalDate? =
        listOfNotNull(
            family.events.maxOfOrNull { it.startDateTime.toLocalDate() },
            family.expenses.maxOfOrNull { it.date },
            family.journal.maxOfOrNull { it.entryDate }
        ).maxOrNull()
}
