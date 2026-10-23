package com.coparently.app.data.stickers

import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.domain.stickers.StickerKind
import com.google.firebase.Timestamp

/**
 * The wire form of `stickers/{id}` (October 2026), in both directions — the one place it is
 * defined, as `EventDocument` is for events. `firestore.rules`' `stickerCreateValid` admits
 * exactly the keys [parentSticker] and [appSticker] write.
 */
object StickerDocument {

    /** The value `from` holds for a sticker the app gave. */
    const val FROM_APP = "app"

    /** A sticker [fromUid] sends [toUid] in [familyId]; `createdAt` is the caller's server timestamp. */
    fun parentSticker(
        fromUid: String,
        toUid: String,
        familyId: String,
        kind: StickerKind,
        createdAt: Any
    ): Map<String, Any> =
        mapOf(
            "to" to toUid,
            "from" to fromUid,
            "participants" to listOf(fromUid, toUid),
            "familyId" to familyId,
            "kind" to kind.wire,
            "createdAt" to createdAt
        )

    /** A sticker the app gives [uid]; [familyId] blank when it belongs to no family. */
    fun appSticker(uid: String, familyId: String, kind: StickerKind, createdAt: Any): Map<String, Any> =
        mapOf(
            "to" to uid,
            "from" to FROM_APP,
            "participants" to listOf(uid),
            "familyId" to familyId,
            "kind" to kind.wire,
            "createdAt" to createdAt
        )

    /**
     * The sticker stored as [data] under [id], or null for one this build cannot word: a kind it
     * does not know, or no receiver. Every field is read with `as?`, because a document is data,
     * not a promise.
     */
    fun read(id: String, data: Map<String, Any?>): Sticker? {
        val kind = StickerKind.fromWire(data["kind"] as? String) ?: return null
        val to = (data["to"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val from = (data["from"] as? String)?.takeIf { it.isNotBlank() && it != FROM_APP }
        return Sticker(
            id = id,
            toUid = to,
            fromUid = from,
            familyId = data["familyId"] as? String ?: "",
            kind = kind,
            createdAtMillis = millisOf(data["createdAt"]),
            seenAtMillis = millisOf(data["seenAt"])
        )
    }

    private fun millisOf(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> null
    }
}
