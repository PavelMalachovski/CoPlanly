package com.coparently.app.data.requests

import com.coparently.app.domain.activity.ActivityEntityType

/** What a request's own record says when a step of its clock looks (phase 3a). */
enum class RecordStatus {
    /** Still waiting for the answering parent. */
    PENDING,

    /** Decided: accepted, declined or withdrawn by its author. */
    ANSWERED,

    /**
     * No longer there, replaced by a newer request, not the clock's to time — or in a family this
     * parent is no longer in, whose requests nothing acts on any more.
     */
    GONE,

    /** The record could not be read; whoever asked tries again. */
    UNKNOWN
}

/**
 * What a request's chat cards name, so the "Last chance" and "No answer" cards go where the
 * request's other cards go (see `ActivityKind.REQUEST_LAST_CHANCE`).
 *
 * @property entityType The request's own kind of card.
 * @property entityId What a tap on the card opens.
 * @property title The name the card shows; blank for a proposal, which the reader words.
 */
data class CardSubject(val entityType: ActivityEntityType, val entityId: String, val title: String)

/**
 * One look at a request's record.
 *
 * @property status Where the record stands.
 * @property card What the request's cards name; null when the record was not found.
 */
data class RecordLook(val status: RecordStatus, val card: CardSubject?)
