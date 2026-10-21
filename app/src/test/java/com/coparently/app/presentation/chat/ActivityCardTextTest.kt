package com.coparently.app.presentation.chat

import com.coparently.app.R
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import org.junit.Test
import kotlin.test.assertEquals

/**
 * That every announced change has its own sentence, and what each sentence is about.
 *
 * Exhaustiveness itself is enforced by the compiler — the mapping is a `when` over an enum with no
 * `else`, so a new [ActivityKind] does not build until it has been given one. What a test can add
 * is **distinctness**: a copy-paste that pointed two kinds at the same resource would compile
 * happily and quietly tell a parent their expense was deleted when it was edited.
 */
class ActivityCardTextTest {

    @Test
    fun `every kind has its own string, with none shared by two`() {
        val resources = ActivityKind.entries.map { ActivityCardText.resourceFor(it) }

        assertEquals(ActivityKind.entries.size, resources.toSet().size)
    }

    @Test
    fun `an expense split's four cards are worded as a split, not as custody`() {
        assertEquals(
            listOf(
                R.string.activity_split_proposed,
                R.string.activity_split_accepted,
                R.string.activity_split_declined,
                R.string.activity_split_withdrawn
            ),
            listOf(
                ActivityKind.SPLIT_PROPOSED,
                ActivityKind.SPLIT_ACCEPTED,
                ActivityKind.SPLIT_DECLINED,
                ActivityKind.SPLIT_WITHDRAWN
            ).map { ActivityCardText.resourceFor(it) }
        )
    }

    @Test
    fun `a withdrawn swap and a withdrawn custody proposal have their own sentences`() {
        assertEquals(
            R.string.activity_day_swap_withdrawn,
            ActivityCardText.resourceFor(ActivityKind.DAY_SWAP_WITHDRAWN)
        )
        assertEquals(
            R.string.activity_custody_withdrawn,
            ActivityCardText.resourceFor(ActivityKind.CUSTODY_WITHDRAWN)
        )
    }

    @Test
    fun `every kind resolves to a real resource`() {
        ActivityKind.entries.forEach { kind ->
            assertEquals(true, ActivityCardText.resourceFor(kind) != 0, "$kind has no string")
        }
    }

    /** A card about [entityType] as the app announces it, whatever it announces. */
    private fun card(entityType: ActivityEntityType, title: String) = ActivityAnnouncement(
        kind = ActivityKind.REQUEST_LAST_CHANCE,
        entityType = entityType,
        entityId = "id-1",
        title = title
    )

    @Test
    fun `a custody card's subject is worded, a swap's is its date, anything else its title`() {
        // A proposal has no name of its own, so its card is worded: a request's card carries "".
        assertEquals(
            CardSubjectText.Res(R.string.activity_subject_custody_schedule),
            ActivityCardText.subjectOf(card(ActivityEntityType.CUSTODY_PROPOSAL, title = ""))
        )
        // The cards that existed before carry the family id as their title, and their sentences
        // take no argument at all: the worded subject is what they were already drawn with.
        assertEquals(
            CardSubjectText.Res(R.string.activity_subject_custody_schedule),
            ActivityCardText.subjectOf(card(ActivityEntityType.CUSTODY_PROPOSAL, title = "uidA__uidB"))
        )
        // An expense split has no name of its own either: its "No answer" card says what it was about.
        assertEquals(
            CardSubjectText.Res(R.string.activity_subject_expense_split),
            ActivityCardText.subjectOf(card(ActivityEntityType.SPLIT_PROPOSAL, title = ""))
        )
        assertEquals(
            CardSubjectText.IsoDate("2026-10-16"),
            ActivityCardText.subjectOf(card(ActivityEntityType.DAY_SWAP, title = "2026-10-16"))
        )
        listOf(ActivityEntityType.EVENT, ActivityEntityType.CHANGE_REQUEST, ActivityEntityType.EXPENSE)
            .forEach { type ->
                assertEquals(
                    CardSubjectText.Raw("Swimming"),
                    ActivityCardText.subjectOf(card(type, title = "Swimming")),
                    "$type"
                )
            }
    }
}
