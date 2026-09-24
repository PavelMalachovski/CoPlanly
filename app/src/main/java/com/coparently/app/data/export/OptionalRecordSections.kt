package com.coparently.app.data.export

import com.coparently.app.domain.export.PlanSource
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.repository.JournalRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The sections a parent may add to a communication record, each behind its own checkbox: the
 * parenting plan (MON-5) and their private journal (MON-22).
 *
 * One dependency for the export flow rather than one per section, because neither is part of what
 * [CommunicationRecordSource] gathers for every record, and the two share that one property.
 */
@Singleton
class OptionalRecordSections @Inject constructor(
    private val plans: ParentingPlanRecordSource,
    private val journal: JournalRepository
) {

    /** The family's parenting plan; see [ParentingPlanRecordSource.read]. */
    suspend fun plan(myUid: String, partnerUid: String?): PlanSource = plans.read(myUid, partnerUid)

    /**
     * [myUid]'s own journal entries about a day in [from]…[to].
     *
     * From this phone's Room table, the only place they exist — which is also why this cannot fail
     * the way a server read can, and why only the signed-in parent's entries can ever be in it.
     */
    suspend fun journal(myUid: String, from: LocalDate, to: LocalDate): List<JournalEntry> =
        journal.entriesBetween(myUid, from, to)
}
