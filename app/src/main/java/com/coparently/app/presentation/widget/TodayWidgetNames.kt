package com.coparently.app.presentation.widget

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.Parents
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two parents' names and colours as the app last loaded them, for the Today widget.
 *
 * The widget draws with no screen open, often in a process started only for it, and the
 * co-parent's name lives in Firestore alone: Room holds this device's own `users` row and nobody
 * else's (CLAUDE.md, "Only the signed-in user has a Room users row"). Attaching the pairing
 * listeners behind [com.coparently.app.presentation.common.ParentsSource] from a widget update
 * would pay three Firestore listeners and a document read for a label, so the app remembers the
 * last loaded [Parents] here while it is on screen, and the widget reads them back.
 *
 * Kept in [EncryptedPreferences], beside the account's other local state, and so cleared with it
 * on sign-out, an account switch and account deletion. Keyed to the signed-in uid as well:
 * [recall] refuses a snapshot whose own parent is somebody else, so the home screen never names
 * the previous account's family for the moment before the new one's names load.
 *
 * One key per field, never a serialised object: a name is free text, and a separator or a JSON
 * encoder would be one more format to get right for a label.
 */
@Singleton
class TodayWidgetNames @Inject constructor(
    private val preferences: EncryptedPreferences
) {

    /**
     * Stores [parents] when they are a real answer that differs from the stored one.
     *
     * @return True when something changed, so the caller knows the widget needs redrawing.
     */
    @Synchronized
    fun remember(parents: Parents): Boolean {
        if (!parents.loaded) return false
        val values = fieldsOf(parents.me) + fieldsOf(parents.coParent)
        val changed = KEYS.indices.any { preferences.getString(KEYS[it]).orEmpty() != values[it] }
        if (changed) KEYS.indices.forEach { preferences.putString(KEYS[it], values[it]) }
        return changed
    }

    /**
     * The stored parents, or null when nothing is stored or it belongs to an account other than
     * [currentUid].
     */
    fun recall(currentUid: String): Parents? {
        val values = KEYS.map { preferences.getString(it).orEmpty() }
        val me = parentAt(values, 0)?.takeIf { it.uid == currentUid } ?: return null
        val coParent = parentAt(values, FIELDS_PER_PARENT)
        return Parents(me = me, coParent = coParent, isPaired = coParent != null, loaded = true)
    }

    private fun fieldsOf(parent: NamedParent?): List<String> = listOf(
        parent?.uid.orEmpty(),
        parent?.slot.orEmpty(),
        parent?.name.orEmpty(),
        parent?.colorCode.orEmpty()
    )

    private fun parentAt(values: List<String>, offset: Int): NamedParent? {
        val uid = values[offset]
        val slot = values[offset + 1]
        if (uid.isBlank() || slot.isBlank()) return null
        return NamedParent(
            uid = uid,
            slot = slot,
            name = values[offset + 2],
            colorCode = values[offset + COLOR_OFFSET].ifBlank { null }
        )
    }

    private companion object {
        const val FIELDS_PER_PARENT = 4
        const val COLOR_OFFSET = 3

        /** Four fields for this device's parent, then four for the co-parent. */
        val KEYS = listOf("me", "coparent").flatMap { who ->
            listOf("uid", "slot", "name", "color").map { field -> "today_widget_${who}_$field" }
        }
    }
}
