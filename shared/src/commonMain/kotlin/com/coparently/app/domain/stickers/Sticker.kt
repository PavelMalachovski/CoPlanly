package com.coparently.app.domain.stickers

/**
 * How a parent's stickers address them (October 2026, owner decision;
 * `docs/DESIGN-stickers-and-subscription.md`): the one place the app says "dad" or "mom" about a
 * person, and only in a sticker's own sentence — every other label is the person's name.
 *
 * A required answer, asked once (`users/{uid}.parentRole`, Firestore only), and never guessed:
 * not from the name, the slot or the colour. [PARENT] is for a parent who is neither, or would
 * rather not say; their stickers read "a great parent".
 *
 * @property wire The value stored in `users/{uid}.parentRole`, which `firestore.rules` bounds.
 */
enum class ParentRole(val wire: String) {
    DAD("dad"),
    MOM("mom"),
    PARENT("parent");

    companion object {
        /** The role [value] names, or null for none or a word this build does not know. */
        fun fromWire(value: String?): ParentRole? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * What a sticker says. The app gives two kinds itself; a parent sends one of the rest, from a
 * fixed set — no free text, so nothing can be turned into sarcasm.
 *
 * @property wire The value stored in `stickers/{id}.kind`, which `firestore.rules` lists.
 * @property fromApp Whether only the app gives it.
 */
enum class StickerKind(val wire: String, val fromApp: Boolean = false) {
    GREAT_PARENT("great_parent"),
    THANK_YOU("thank_you"),
    KIDS_HAPPY("kids_happy"),
    GOOD_TEAM("good_team"),
    WELL_DONE("well_done"),
    PATIENCE("patience"),
    FLEXIBLE("flexible"),
    ON_TIME("on_time", fromApp = true),
    REGULAR("regular", fromApp = true);

    companion object {
        /** The phrases a parent may send, in the order the sheet lists them. */
        val PARENT_KINDS: List<StickerKind> = entries.filterNot { it.fromApp }

        /** The kind [value] names, or null for one this build does not know. */
        fun fromWire(value: String?): StickerKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * A sticker this parent received or sent.
 *
 * @property id The document id; an app sticker's spells its receiver, kind and ISO week.
 * @property toUid Who received it.
 * @property fromUid Who sent it, or null for the app.
 * @property familyId The family it was given in, blank for none; unpair removes the family's.
 * @property kind What it says.
 * @property createdAtMillis When the server stored it, or null until it has.
 * @property seenAtMillis When its receiver first saw it, or null while unseen.
 */
data class Sticker(
    val id: String,
    val toUid: String,
    val fromUid: String?,
    val familyId: String,
    val kind: StickerKind,
    val createdAtMillis: Long? = null,
    val seenAtMillis: Long? = null
)

/**
 * Whether this parent has said how their stickers address them. [Loading] is not [Unanswered]:
 * until the server has said the field is missing, nobody is asked — a question that flashed up on
 * every cold start, and vanished, would be worse than none.
 */
sealed interface ParentRoleAnswer {
    /** Not known yet: the profile has not been read from the server. */
    data object Loading : ParentRoleAnswer

    /** The server says this parent has not answered. Home asks, once, with no "later". */
    data object Unanswered : ParentRoleAnswer

    /** This parent's answer. */
    data class Answered(val role: ParentRole) : ParentRoleAnswer
}
