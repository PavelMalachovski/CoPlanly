package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.coparently.app.R

/**
 * Everything a composable needs to turn a slot into a name: the two parents, and the three
 * fallbacks for when one of them cannot be named.
 *
 * This exists so a screen passes *one* value down its tree instead of five. Threading `me`,
 * `coParent` and three strings through `MonthView`, `DayWeekView`, `DayAgendaCard`,
 * `ExpenseList` and the rest would be noise at every level, and — the reason
 * that matters — it is how one composable quietly ends up resolving labels from a different
 * pair than the sibling rendered next to it.
 *
 * The fallbacks are resolved by [rememberParentNames] in composable scope. A ViewModel must not
 * resolve them: it has no `Context` to do it with and must not acquire one just for this.
 */
@Immutable
data class ParentNames(
    val parents: Parents,
    val youFallback: String,
    val coParentFallback: String,
    val unknownFallback: String
) {
    /**
     * The name to show for [slot], or a fallback when that person cannot be identified.
     *
     * A null slot — an event with no owner, a day no custody model covers — is not a parent at
     * all, so it takes the same "cannot be identified" answer as an unrecognised one.
     */
    fun labelFor(slot: String?): String = if (slot == null) {
        unknownFallback
    } else {
        parentLabel(
            slot = slot,
            me = parents.me,
            coParent = parents.coParent,
            youFallback = youFallback,
            coParentFallback = coParentFallback,
            unknownFallback = unknownFallback
        )
    }

    /**
     * The name to show for whoever holds [uid], or a fallback when that uid cannot be
     * identified.
     *
     * Distinct from [labelFor]: that resolves a *slot* via [parentLabel], which collapses to the
     * same value for both parents on a pair that has not been migrated yet (both still reading
     * `"mom"` — see [Parents.roleByUid]). A caller that already holds a uid — a document's
     * `lastModifiedBy`, say — must resolve it here, via [parentLabelByUid], rather than detouring
     * through a slot: that detour is what would report the co-parent's write as the signed-in
     * parent's own on exactly the pairs this branch's slot-assignment work exists to serve.
     *
     * A null uid is not a parent at all, so it takes the same "cannot be identified" answer as
     * an unrecognised one — the same rule [labelFor] applies to a null slot.
     */
    fun labelForUid(uid: String?): String = if (uid == null) {
        unknownFallback
    } else {
        parentLabelByUid(
            uid = uid,
            me = parents.me,
            coParent = parents.coParent,
            youFallback = youFallback,
            coParentFallback = coParentFallback,
            unknownFallback = unknownFallback
        )
    }

    /**
     * That parent's avatar, or null when they have none and the initial-letter fallback applies.
     *
     * For a Google sign-in this is the account's own picture (see [NamedParent.photoUrl]). Keyed
     * on the uid rather than the slot because a payer, an event's author and a friend grant are
     * all identified by uid, and a pair still sharing one slot would otherwise return the same
     * face for both parents.
     */
    fun photoForUid(uid: String?): String? = uid?.let { id ->
        listOfNotNull(parents.me, parents.coParent).firstOrNull { it.uid == id }?.photoUrl
    }

    /**
     * The slot of whoever holds [uid], or null for anybody else — what a uid-keyed author's colour
     * is looked up by, since `ParentColors` resolves slots. A pair still sharing one slot answers
     * the same slot for both, as [Parents.roleByUid] does.
     */
    fun slotForUid(uid: String?): String? = uid?.let { parents.roleByUid[it] }

    /**
     * Whether [slot] resolves to a named person rather than to the unknown fallback.
     *
     * [parentLabel] itself is not asked this — it already gives the correct answer to "who is
     * this" ("we do not know" is a legitimate answer, not a gap). A slot *picker* asks a
     * different question: not knowing who a slot belongs to is not a reason to leave its card
     * captionless, so a caller that needs to fall back to something other than
     * [unknownFallback] — an ordinal, say — checks this first.
     */
    fun isKnown(slot: String): Boolean = slot == parents.me?.slot || slot == parents.coParent?.slot

    /**
     * Whether [slot] is unmistakably the reading parent's own: the signed-in parent's slot, and not
     * one both parents still share. A screen words such a slot as "you" rather than by the reader's
     * own name (UX audit H4: Alice was told "Today with Alice" and "Alice paid").
     */
    fun isReader(slot: String?): Boolean =
        slot != null && slot == parents.me?.slot && slot != parents.coParent?.slot

    /** Whether [uid] is the reading parent's own. */
    fun isReaderUid(uid: String?): Boolean = uid != null && uid == parents.me?.uid

    /**
     * [labelFor], except that the reader's own slot is "You" — for a label that stands alone, before
     * a colon or as a column heading, where no verb has to agree with it. A sentence takes its own
     * string for the reader instead, because "You" changes the verb in every language.
     */
    fun labelOrYou(slot: String?): String = if (isReader(slot)) youFallback else labelFor(slot)

    /** [labelForUid], except that the reader is "You"; see [labelOrYou]. */
    fun labelOrYouForUid(uid: String?): String = if (isReaderUid(uid)) youFallback else labelForUid(uid)
}

/**
 * Binds [parents] to the localized fallbacks, once per screen.
 *
 * @param parents The two parents, collected from the screen's ViewModel.
 */
@Composable
fun rememberParentNames(parents: Parents): ParentNames {
    val you = stringResource(R.string.parent_label_you)
    val coParent = stringResource(R.string.parent_label_coparent)
    val unknown = stringResource(R.string.parent_label_unknown)
    return remember(parents, you, coParent, unknown) {
        ParentNames(parents, you, coParent, unknown)
    }
}
