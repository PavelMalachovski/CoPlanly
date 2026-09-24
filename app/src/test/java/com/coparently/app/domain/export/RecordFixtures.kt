package com.coparently.app.domain.export

import com.coparently.app.data.versions.EventVersionKind
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Shared fixtures for the export tests. Words are English and distinctive, so a test can find them. */
internal object RecordFixtures {

    const val ALICE = "alice-uid"
    const val BOB = "bob-uid"
    const val FAMILY = "alice-uid__bob-uid"
    val ZONE: ZoneId = ZoneId.of("Europe/Prague")
    val FROM: LocalDate = LocalDate.of(2026, 3, 1)
    val TO: LocalDate = LocalDate.of(2026, 3, 31)

    /** 2026-03-10 09:00 in Prague (UTC+1). */
    const val MARCH_10_0900 = 1_773_129_600_000L

    fun labels() = RecordLabels(
        title = "TITLE",
        statement = listOf("NOT A TRUTH RECORD", "MESSAGES ARE FIXED"),
        period = "Period",
        generated = "Generated",
        timeZone = "Zone",
        parents = "Parents",
        incomplete = "INCOMPLETE",
        sectionEvents = "Event",
        sectionMessages = "Message",
        sectionExpenses = "Expense",
        nothingInPeriod = "Nothing",
        columns = RecordColumns(
            section = "Section",
            item = "Item",
            revision = "Rev",
            action = "Action",
            by = "By",
            deviceTime = "Device time",
            serverTime = "Server time",
            text = "Text",
            starts = "Starts",
            ends = "Ends",
            parent = "Parent",
            notes = "Notes",
            amount = "Amount",
            currency = "Currency"
        ),
        actions = actionLabels(),
        notYetOnServer = "Not yet on server",
        noServerTime = "None kept",
        revision = "Revision",
        page = "Page",
        verification = VerificationLabels(
            recordId = "Record ID",
            verifyAt = "Verify at",
            instruction = "CHECK IT AT THE ADDRESS ABOVE",
            instructionNoUrl = "REGISTERED UNDER THIS ID",
            notRegistered = "NOT REGISTERED, CANNOT BE VERIFIED",
            notRegisteredShort = "NOT REGISTERED"
        ),
        plan = planLabels(),
        journal = JournalLabels(
            section = "Journal",
            privateNote = "ONE PARENT'S PRIVATE NOTES, NEVER SHARED",
            clockNote = "THAT PHONE'S CLOCK",
            none = "NO JOURNAL ENTRIES",
            written = "Written",
            edited = "Last edited"
        )
    )

    /** The action words, kept apart from [labels] so that function stays within detekt's length. */
    fun actionLabels() = RecordActions(
        created = "Created",
        updated = "Changed",
        deleted = "Deleted",
        currentState = "Current state",
        sent = "Sent",
        notSent = "Not sent",
        recorded = "Recorded",
        serverRecorded = "RECORDED BY THE SERVER"
    )

    /** The parenting plan's section words, short and upper-case so a test can find them. */
    fun planLabels() = PlanLabels(
        section = "Plan",
        disclaimer = "NOT THE MINISTRY FORM",
        currentState = "CURRENT STATE AT EXPORT",
        notFromServer = "PLAN FROM THIS PHONE",
        unsentHere = "UNSENT EDITS NOT SHOWN",
        noPlan = "NO PLAN RECORDED",
        lastChanged = "Last changed",
        agreed = "Agreed",
        notAgreed = "Not agreed",
        notAnswered = "Not answered",
        retired = "NO LONGER ASKED",
        questions = mapOf(
            "residence_home" to "Where will the child live?",
            "care_weekday" to "Who cares for the child on which weekdays?"
        ),
        cited = "FROM THE PLAN ANSWER TO"
    )

    /** A record id as the server mints them. */
    const val RECORD_ID = "7K3Q0ABCDEFGHJKM"

    fun scope(families: Set<String> = setOf(FAMILY)) = RecordScope(
        from = FROM,
        to = TO,
        zone = ZONE,
        generatedAtMillis = MARCH_10_0900,
        families = families,
        parents = listOf("Alice", "Bob"),
        nameForUid = { uid ->
            when (uid) {
                ALICE -> "Alice"
                BOB -> "Bob"
                else -> "Unknown"
            }
        },
        nameForSlot = { slot -> if (slot == "mom") "Alice" else "Bob" }
    )

    fun facts(
        title: String = "Dentist",
        start: LocalDateTime? = LocalDateTime.of(2026, 3, 12, 15, 0),
        end: LocalDateTime? = LocalDateTime.of(2026, 3, 12, 16, 0),
        recurrence: String = "",
        recurrenceEnd: LocalDate? = null
    ) = EventFacts(
        title = title,
        description = "",
        start = start,
        end = end,
        parentSlot = "mom",
        recurrence = recurrence,
        recurrenceEnd = recurrenceEnd
    )

    // Each argument is one field of the revision; a fixture that hid them would hide the point.
    @Suppress("LongParameterList")
    fun revision(
        versionId: String,
        eventId: String = "e1",
        kind: EventVersionKind = EventVersionKind.UPDATED,
        editor: String = ALICE,
        deviceTime: Long? = MARCH_10_0900,
        recordedAt: Long? = MARCH_10_0900 + 2_000,
        familyId: String = FAMILY,
        facts: EventFacts = facts(),
        byServer: Boolean = false,
        writeKey: String? = null
    ) = EventRevisionInput(
        versionId = versionId,
        eventId = eventId,
        kind = kind,
        editorUid = editor,
        deviceTimeMillis = deviceTime,
        recordedAtMillis = recordedAt,
        familyId = familyId,
        facts = facts,
        recordedByServer = byServer,
        writeKey = writeKey
    )

    fun expense(id: String, date: LocalDate, familyId: String? = FAMILY, title: String = "School trip") = Expense(
        id = id,
        title = title,
        amount = 42.5,
        currency = "CZK",
        category = ExpenseCategory.entries.first(),
        paidBy = BOB,
        date = date,
        createdByFirebaseUid = BOB,
        familyId = familyId
    )

    fun sources(
        revisions: List<EventRevisionInput> = emptyList(),
        currentEvents: List<CurrentEventInput> = emptyList(),
        messages: List<MessageInput> = emptyList(),
        expenses: List<Expense> = emptyList(),
        serverReached: Boolean = true
    ) = RecordSources(revisions, currentEvents, messages, expenses, serverReached)

    /** A citation of the weekday-care answer, as a proposal from the plan carries it (MON-21). */
    const val CARE_CITATION = "p1|care_weekday|0123456789abcdef"

    /** Bob's schedule-proposal card in the chat, citing [citation] — or nothing, like an older build's. */
    fun proposalCard(citation: String? = CARE_CITATION) = MessageInput(
        messageId = "m-proposal",
        senderUid = BOB,
        sentAtMillis = MARCH_10_0900,
        text = "Bob proposed a new custody schedule",
        delivered = true,
        planCitation = citation
    )

    /** 2026-03-09 18:00 in Prague — when Bob last changed his half of the plan. */
    const val MARCH_9_1800 = 1_773_075_600_000L

    /** A plan read from the server, Alice first; halves default to none (a family with no plan). */
    fun planOf(
        halves: Map<String, ParentingPlanEntry> = emptyMap(),
        serverReached: Boolean = true,
        unsentHere: Boolean = false
    ) = PlanSource(listOf(ALICE, BOB), halves, serverReached, unsentHere)
}
