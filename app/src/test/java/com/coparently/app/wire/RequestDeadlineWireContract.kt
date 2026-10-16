package com.coparently.app.wire

import com.coparently.app.data.requests.RequestDeadlineDocument
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import java.time.LocalDate

/**
 * `request_deadlines`: read by `FirestoreRequestDeadlineDataSource` through
 * `RequestDeadlineDocument.parse`, written by `RequestDeadlineRepositoryImpl.start` through
 * `RequestDeadlineDocument.createFields` (phase 3a, spec §5.1).
 *
 * **There is no round trip.** No build rewrites a whole clock: the create is the only `set()`, and
 * every later write is an `update()` of the fields one phone owns (`firestore.rules` admits exactly
 * five such shapes). A field an older build does not know therefore cannot be lost by it writing the
 * document back, and an older build never reads this collection at all. What the fixtures pin
 * instead is the reading: a clock with only the required keys, one from a newer build with a field
 * this one does not know, and a kind this build cannot show.
 */
internal object RequestDeadlineWireContract : WireContract {

    override val collection = RequestDeadlineDocument.COLLECTION

    /** What `createFields` always puts in the document: the required keys, minus the server's `createdAt`. */
    override val alwaysWrites = setOf(
        RequestDeadlineDocument.KIND,
        RequestDeadlineDocument.REF,
        RequestDeadlineDocument.FAMILY_ID,
        RequestDeadlineDocument.PROPOSER_UID,
        RequestDeadlineDocument.RESPONDER_UID,
        RequestDeadlineDocument.PARTICIPANTS,
        RequestDeadlineDocument.ANSWER_BY_MILLIS
    )

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        // `FirestoreRequestDeadlineDataSource` turns the three server Timestamps into millis before
        // parsing; so do we.
        val times = RequestDeadlineDocument.ServerTimes(
            createdAtMillis = (document[RequestDeadlineDocument.CREATED_AT] as? WireTimestamp)?.millis,
            lastChanceAtMillis = (document[RequestDeadlineDocument.LAST_CHANCE_AT] as? WireTimestamp)?.millis,
            closedAtMillis = (document[RequestDeadlineDocument.CLOSED_AT] as? WireTimestamp)?.millis
        )
        val clock = RequestDeadlineDocument.parse(CLOCK_ID, document, times) ?: return null
        return mapOf(
            "kind" to clock.kind.wire,
            "ref" to clock.ref,
            "familyId" to clock.familyId,
            "proposerUid" to clock.proposerUid,
            "responderUid" to clock.responderUid,
            "answerByMillis" to clock.answerByMillis,
            "capMillis" to clock.capMillis,
            "createdAtMillis" to clock.createdAtMillis,
            "firstDate" to clock.firstDate?.toString(),
            "dayCount" to clock.dayCount,
            "deliveredAtMillis" to clock.deliveredAtMillis,
            "remindersPlanned" to clock.remindersPlanned,
            "reminderTimesMillis" to clock.reminderTimesMillis,
            "seenAtMillis" to clock.seenAtMillis,
            "remindersShown" to clock.remindersShown,
            "lastChanceAtMillis" to clock.lastChanceAtMillis,
            "lastChanceUntilMillis" to clock.lastChanceUntilMillis,
            "outcome" to clock.outcome?.wire,
            "closedAtMillis" to clock.closedAtMillis
        )
    }

    override fun roundTrip(document: Map<String, Any?>): Map<String, Any?>? = null

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "create",
            about = "A swap offer's clock as RequestDeadlineRepositoryImpl.start creates it, before the data " +
                "source adds createdAt.",
            document = RequestDeadlineDocument.createFields(
                start = RequestStart(
                    kind = RequestKind.SWAP,
                    ref = "grp-current-1",
                    familyId = "uidA__uidB",
                    responderUid = "uidB",
                    // 2026-05-12T20:00:00Z, and the first swapped day, 2026-05-16T00:00:00Z, as its cap.
                    answerByMillis = 1_778_616_000_000L,
                    capMillis = 1_778_889_600_000L,
                    firstDate = LocalDate.of(2026, 5, 16),
                    dayCount = 2
                ),
                proposerUid = "uidA"
            )
        )
    )

    private const val CLOCK_ID = "fixture"
}
