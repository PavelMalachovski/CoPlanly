package com.coparently.app.domain.versions

/**
 * What a revision records: the event's creation, an edit, or its deletion.
 *
 * @property wire The value stored in the document; `firestore.rules` accepts exactly these three.
 */
enum class EventVersionKind(val wire: String) {
    CREATED("created"),
    UPDATED("updated"),
    DELETED("deleted");

    companion object {

        /** The kind stored as [wire], or null for a value this build does not know. */
        fun fromWire(wire: String?): EventVersionKind? = entries.firstOrNull { it.wire == wire }
    }
}
