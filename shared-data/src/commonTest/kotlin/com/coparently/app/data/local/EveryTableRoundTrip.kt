package com.coparently.app.data.local

import com.coparently.app.data.local.entity.BudgetEntity
import com.coparently.app.data.local.entity.ChangeRequestEntity
import com.coparently.app.data.local.entity.ChildInfoEntity
import com.coparently.app.data.local.entity.ConversationEntity
import com.coparently.app.data.local.entity.CustodyModelEntity
import com.coparently.app.data.local.entity.CustodyScheduleEntity
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.local.entity.EventVersionOutboxEntity
import com.coparently.app.data.local.entity.ExpenseEntity
import com.coparently.app.data.local.entity.FamilyDocumentCacheEntity
import com.coparently.app.data.local.entity.JournalEntryEntity
import com.coparently.app.data.local.entity.KnownFamilyEntity
import com.coparently.app.data.local.entity.MessageEntity
import com.coparently.app.data.local.entity.ParentingPlanEntryEntity
import com.coparently.app.data.local.entity.PetEntity
import com.coparently.app.data.local.entity.UserEntity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.assertEquals

/**
 * One row in every table of [CoPlanlyDatabase], written and read back through the DAOs, so a
 * platform's generated code, its driver and [Converters] are run over every column type the
 * schema has. `IosDatabaseTest` runs it on the simulator, over a database `buildCoPlanlyDatabase`
 * opened; a new table gets a row here.
 *
 * The date-time values carry a fraction of a second, a second of zero and a year below 1000
 * on purpose: those are where the stored text could differ between platforms (`Converters`).
 */
object EveryTableRoundTrip {

    /** Writes a row to each of the 16 tables and asserts each reads back equal. */
    suspend fun assertEveryTableRoundTrips(database: CoPlanlyDatabase): Unit = with(database) {
        eventDao().insertEvent(EVENT)
        assertEquals(EVENT, eventDao().getEventById(EVENT.id), "events")
        userDao().insertUser(USER)
        assertEquals(USER, userDao().getUserById(USER.id), "users")
        custodyScheduleDao().insertSchedule(SCHEDULE)
        assertEquals(SCHEDULE, custodyScheduleDao().getScheduleById(SCHEDULE.id), "custody_schedules")
        custodyModelDao().insertModel(CUSTODY_MODEL)
        assertEquals(CUSTODY_MODEL, custodyModelDao().getModelById(CUSTODY_MODEL.id), "custody_models")
        childInfoDao().insertChildInfo(CHILD)
        assertEquals(CHILD, childInfoDao().getChildInfoById(CHILD.id), "child_info")
        petDao().insertPet(PET)
        assertEquals(PET, petDao().getPetById(PET.id), "pets")
        messageDao().insertConversation(CONVERSATION)
        assertEquals(CONVERSATION, messageDao().getConversationById(CONVERSATION.id), "conversations")
        messageDao().insertMessage(MESSAGE)
        assertEquals(listOf(MESSAGE), messageDao().getMessagesOnce(CONVERSATION.id), "messages")
        expenseDao().insertExpense(EXPENSE)
        assertEquals(EXPENSE, expenseDao().getExpenseById(EXPENSE.id), "expenses")
        budgetDao().insertBudget(BUDGET)
        assertEquals(BUDGET, budgetDao().getBudgetById(BUDGET.id), "budgets")
        changeRequestDao().insertChangeRequest(CHANGE_REQUEST)
        assertEquals(CHANGE_REQUEST, changeRequestDao().getChangeRequestById(CHANGE_REQUEST.id), "change_requests")
        parentingPlanDao().upsert(PLAN)
        assertEquals(PLAN, parentingPlanDao().getEntry(FAMILY, ALICE), "parenting_plan_entries")
        eventVersionOutboxDao().insert(REVISION)
        assertEquals(listOf(REVISION), eventVersionOutboxDao().undelivered(ALICE), "event_version_outbox")
        journalDao().upsert(NOTE)
        assertEquals(NOTE, journalDao().getEntry(NOTE.id, ALICE), "journal_entries")
        familyDocumentCacheDao().upsertAll(listOf(DOCUMENT))
        assertEquals(listOf(DOCUMENT), familyDocumentCacheDao().liveIn(FAMILY), "family_documents_cache")
        knownFamilyDao().upsert(KNOWN_FAMILY)
        assertEquals(KNOWN_FAMILY, knownFamilyDao().get(FAMILY), "known_families")
    }

    private const val ALICE = "uid-alice"
    private const val BOB = "uid-bob"
    private const val FAMILY = "uid-alice__uid-bob"
    private const val MILLIS = 1_793_000_000_000L
    private const val NANOS = 120_000_000
    private const val SHA256_HEX_LENGTH = 64

    // Declared before the rows, which read it: an object's properties initialise in order.
    private val AT = LocalDateTime(2026, 11, 2, 9, 15, 30, 500_000_000)

    private val EVENT = EventEntity(
        id = "event-1",
        title = "Dentist",
        description = "Bring the card",
        startDateTime = AT,
        endDateTime = LocalDateTime(2026, 11, 2, 10, 30, 0),
        eventType = "doctor",
        parentOwner = "mom",
        isRecurring = true,
        recurrencePattern = "weekly",
        createdAt = LocalDateTime(2026, 10, 1, 8, 0, 5, NANOS),
        updatedAt = AT,
        updatedAtMillis = MILLIS,
        createdByFirebaseUid = ALICE,
        sharedWithJson = "[\"$ALICE\",\"$BOB\"]",
        recurrenceEndDate = LocalDate(2026, 12, 31),
        pickupConfirmedAt = LocalDateTime(2026, 11, 2, 9, 0, 0, 1),
        reminderMinutes = 30,
        acceptance = "PENDING",
        acceptedAt = LocalDateTime(999, 1, 2, 3, 4),
        isImportant = true,
        forMembersJson = "[\"child:c1\"]",
        familyId = FAMILY
    )

    private val USER = UserEntity(
        id = ALICE,
        email = "alice@example.com",
        name = "Alice",
        role = "mom",
        colorCode = "hue1|200",
        partnerId = BOB,
        partnerIdsJson = "[\"$BOB\"]",
        dateOfBirth = "1988-04-17",
        countryCode = "DE",
        regionCode = "BY",
        healthConsentVersion = 1,
        healthConsentAtMillis = MILLIS
    )

    private val SCHEDULE = CustodyScheduleEntity(id = "schedule-1", parentOwner = "dad", dayOfWeek = 3)

    private val CUSTODY_MODEL = CustodyModelEntity(
        id = "model-1",
        modelType = "WEEK_ON_WEEK_OFF",
        patternDays = 14,
        momDaysPattern = "0,1,2,3,4,5,6",
        startDate = "2026-11-02",
        createdAt = "2026-11-02T09:15:00",
        lastModifiedAtMillis = MILLIS,
        contactWindowsJson = "[\"9|15:00|19:00|dad\"]",
        familyId = FAMILY
    )

    private val CHILD = ChildInfoEntity(
        id = "child-1",
        childName = "Emma",
        dateOfBirth = LocalDateTime(2018, 3, 14, 0, 0),
        medicationsJson = "[]",
        activitiesJson = "[]",
        allergiesJson = "[\"peanuts\"]",
        medicalNotes = null,
        emergencyContactsJson = "[]",
        schoolInfoJson = null,
        createdAt = AT,
        updatedAt = AT,
        updatedAtMillis = MILLIS,
        createdByFirebaseUid = ALICE,
        lastModifiedBy = ALICE,
        syncedToFirestore = false,
        familyId = FAMILY
    )

    private val PET = PetEntity(
        id = "pet-1",
        name = "Max",
        species = "DOG",
        breed = null,
        dateOfBirth = null,
        medicationsJson = "[]",
        vaccinationsJson = "[]",
        specialNeeds = null,
        feedingNotes = "Twice a day",
        vetName = null,
        vetPhone = null,
        createdAt = AT,
        updatedAt = AT,
        updatedAtMillis = MILLIS,
        createdByFirebaseUid = ALICE,
        lastModifiedBy = null,
        syncedToFirestore = true,
        deletedAtMillis = MILLIS
    )

    private val CONVERSATION = ConversationEntity(
        id = "$ALICE-$BOB",
        participantsJson = "[\"$ALICE\",\"$BOB\"]",
        title = "Bob",
        lastReadAtJson = "{\"$ALICE\":$MILLIS}",
        lastMessageAtMillis = MILLIS,
        createdAt = AT
    )

    private val MESSAGE = MessageEntity(
        id = "message-1",
        conversationId = CONVERSATION.id,
        senderId = BOB,
        senderName = "Bob",
        content = "Čas na večeři? Ж ї 👍",
        sentAtMillis = MILLIS,
        messageType = "TEXT",
        status = "DELIVERED"
    )

    private val EXPENSE = ExpenseEntity(
        id = "expense-1",
        forMembersJson = "[\"pet:p1\"]",
        familyId = FAMILY,
        title = "Vet",
        amount = 1234.5,
        currency = "CZK",
        category = "MEDICAL",
        paidBy = "mom",
        splitBetweenJson = "[\"$ALICE\",\"$BOB\"]",
        date = LocalDate(2026, 2, 28),
        createdAt = AT,
        createdByFirebaseUid = ALICE,
        splitBasisPoints = 6000
    )

    private val BUDGET = BudgetEntity(id = "budget-1", category = "FOOD", monthlyLimit = 500.0, createdAt = AT)

    private val CHANGE_REQUEST = ChangeRequestEntity(
        id = "request-1",
        eventId = EVENT.id,
        eventTitle = EVENT.title,
        requestedBy = BOB,
        requestedTo = ALICE,
        currentStartDateTime = AT,
        proposedStartDateTime = LocalDateTime(2026, 11, 3, 9, 15),
        proposedEndDateTime = LocalDateTime(2026, 11, 3, 10, 0),
        note = "Could we move it?",
        status = "PENDING",
        createdAt = AT,
        respondedAt = null,
        familyId = FAMILY
    )

    private val PLAN = ParentingPlanEntryEntity(
        familyId = FAMILY,
        authorUid = ALICE,
        catalogueVersion = 1,
        answersJson = "{\"care_weekday\":\"Alternating weeks\"}",
        agreedToJson = "{}",
        updatedAtMillis = MILLIS,
        syncedToFirestore = false
    )

    private val REVISION = EventVersionOutboxEntity(
        id = "revision-1",
        eventId = EVENT.id,
        kind = "updated",
        editorUid = ALICE,
        deviceTimeMillis = MILLIS,
        snapshotJson = "{\"title\":\"Dentist\"}",
        audienceJson = "[\"$ALICE\",\"$BOB\"]",
        familyId = FAMILY,
        attempts = 2
    )

    private val NOTE = JournalEntryEntity(
        id = "journal-1",
        createdByFirebaseUid = ALICE,
        familyId = null,
        entryDate = LocalDate(2024, 2, 29),
        text = "Handover was late again.",
        createdAtMillis = MILLIS,
        updatedAtMillis = MILLIS
    )

    private val DOCUMENT = FamilyDocumentCacheEntity(
        id = "document-1",
        familyId = FAMILY,
        createdByFirebaseUid = BOB,
        title = "Court order",
        category = "LEGAL",
        fileName = "order.pdf",
        storagePath = "family_documents/$FAMILY/document-1/order.pdf",
        contentType = "application/pdf",
        sizeBytes = 123_456L,
        sha256 = "0".repeat(SHA256_HEX_LENGTH),
        createdAtMillis = MILLIS,
        deletedAtMillis = null
    )

    private val KNOWN_FAMILY = KnownFamilyEntity(
        familyId = FAMILY,
        coParentUid = BOB,
        coParentName = "Bob",
        mySlot = "mom",
        coParentSlot = "dad",
        updatedAtMillis = MILLIS
    )
}
