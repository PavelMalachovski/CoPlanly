package com.coparently.app.domain.export

import com.coparently.app.data.versions.EventVersionKind
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
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
        actions = RecordActions(
            created = "Created",
            updated = "Changed",
            deleted = "Deleted",
            currentState = "Current state",
            sent = "Sent",
            notSent = "Not sent",
            recorded = "Recorded"
        ),
        notYetOnServer = "Not yet on server",
        noServerTime = "None kept",
        revision = "Revision",
        page = "Page"
    )

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
        deviceTime: Long = MARCH_10_0900,
        recordedAt: Long? = MARCH_10_0900 + 2_000,
        familyId: String = FAMILY,
        facts: EventFacts = facts()
    ) = EventRevisionInput(
        versionId = versionId,
        eventId = eventId,
        kind = kind,
        editorUid = editor,
        deviceTimeMillis = deviceTime,
        recordedAtMillis = recordedAt,
        familyId = familyId,
        facts = facts
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
}
