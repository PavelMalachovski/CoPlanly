package com.coparently.app.upgrade

/**
 * The rows the seed writes into the base build's database and the verify reads back after the
 * upgrade: one per table a family's history lives in.
 *
 * Values are in the form the production code writes them — `LocalDateTime` as
 * `ISO_LOCAL_DATE_TIME` text, `LocalDate` as `ISO_LOCAL_DATE`, lists as Gson JSON, enums by name
 * — so the verify can read them through the *new* build's DAOs and type converters rather than
 * only count them. A pull request that changes a stored form has to migrate these rows too, which
 * is the point.
 *
 * A **new table joins the fixture one pull request late**: the seed writes into the base build's
 * database, which does not have a table the pull request is adding, so add its row once that
 * pull request has merged.
 *
 * @property table The Room table.
 * @property id The row's primary key.
 * @property probeColumn A column whose text the verify compares after the upgrade.
 * @property values Column → value, written by [insertAdaptively].
 */
internal data class UpgradeRow(
    val table: String,
    val id: String,
    val probeColumn: String,
    val values: Map<String, Any?>
) {
    /** The value the verify expects in [probeColumn]. */
    val probe: String get() = values.getValue(probeColumn).toString()
}

/** The fixture rows, and the facts about them the verify asserts. */
internal object UpgradeRows {

    const val UID = "upgrade-check-uid-alice"
    const val PARTNER_UID = "upgrade-check-uid-bob"
    const val CONVERSATION_ID = "$PARTNER_UID|$UID"

    const val USER_ID = "upgrade-user"
    const val USER_NAME = "Alice (upgrade check)"
    const val EVENT_ID = "upgrade-event"
    const val EVENT_TITLE = "Dentist — written by the base build"
    const val EVENT_START = "2026-05-14T09:30:00"
    const val PRIVATE_EVENT_ID = "upgrade-event-private"
    const val EXPENSE_ID = "upgrade-expense"
    const val EXPENSE_AMOUNT = 42.5
    const val EXPENSE_DATE = "2026-05-10"
    const val CHILD_ID = "upgrade-child"
    const val CHILD_NAME = "Mia"
    const val PET_ID = "upgrade-pet"
    const val MESSAGE_ID = "upgrade-message"
    const val MESSAGE_TEXT = "Pickup at five? — written by the base build"
    const val CUSTODY_ID = "upgrade-custody"
    const val CUSTODY_CYCLE = 14
    const val JOURNAL_ID = "upgrade-journal"

    /** The Google refresh token the seed stores, and the preference values beside it. */
    const val REFRESH_TOKEN = "1//upgrade-check-refresh-token"
    const val DEFAULT_CURRENCY = "CZK"

    private const val CREATED = "2026-05-01T08:00:00"
    private const val UPDATED_MILLIS = 1_777_622_400_000L

    val ALL: List<UpgradeRow> = listOf(
        UpgradeRow(
            "users",
            USER_ID,
            "name",
            mapOf(
                "id" to USER_ID,
                "email" to "alice@upgrade.invalid",
                "name" to USER_NAME,
                "role" to "mom",
                "colorCode" to "#FF4081",
                "googleCalendarSyncEnabled" to 0,
                "partnerId" to PARTNER_UID,
                "partnerIdsJson" to "[\"$PARTNER_UID\"]",
                "allergiesJson" to "[]",
                "medicalProfileJson" to "{}",
                "countryCode" to "DE",
                "regionCode" to "BY"
            )
        ),
        event(EVENT_ID, EVENT_TITLE, isPrivate = false),
        event(PRIVATE_EVENT_ID, "Private note — never synced", isPrivate = true),
        UpgradeRow(
            "expenses",
            EXPENSE_ID,
            "title",
            mapOf(
                "id" to EXPENSE_ID,
                "forMembersJson" to "[\"child:$CHILD_ID\"]",
                "title" to "School shoes",
                "amount" to EXPENSE_AMOUNT,
                "currency" to DEFAULT_CURRENCY,
                "category" to "CLOTHING",
                "paidBy" to UID,
                "splitBetweenJson" to "[\"$UID\",\"$PARTNER_UID\"]",
                "date" to EXPENSE_DATE,
                "createdAt" to CREATED,
                "syncedToFirestore" to 0,
                "createdByFirebaseUid" to UID,
                "splitBasisPoints" to 6000
            )
        ),
        UpgradeRow(
            "child_info",
            CHILD_ID,
            "childName",
            mapOf(
                "id" to CHILD_ID,
                "childName" to CHILD_NAME,
                "dateOfBirth" to "2019-03-02T00:00:00",
                "medicationsJson" to "[]",
                "activitiesJson" to "[]",
                "allergiesJson" to "[\"peanuts\"]",
                "medicalNotes" to "Inhaler in the school bag",
                "emergencyContactsJson" to "[]",
                "medicalProfileJson" to "{}",
                "medicalPhotosJson" to "[]",
                "guestsJson" to "{}",
                "createdAt" to CREATED,
                "updatedAt" to CREATED,
                "updatedAtMillis" to UPDATED_MILLIS,
                "createdByFirebaseUid" to UID,
                "lastModifiedBy" to UID,
                "syncedToFirestore" to 1
            )
        ),
        UpgradeRow(
            "pets",
            PET_ID,
            "name",
            mapOf(
                "id" to PET_ID,
                "name" to "Rex",
                "species" to "dog",
                "medicationsJson" to "[]",
                "vaccinationsJson" to "[]",
                "photosJson" to "[]",
                "createdAt" to CREATED,
                "updatedAt" to CREATED,
                "updatedAtMillis" to UPDATED_MILLIS,
                "createdByFirebaseUid" to UID,
                "syncedToFirestore" to 1
            )
        ),
        UpgradeRow(
            "messages",
            MESSAGE_ID,
            "content",
            mapOf(
                "id" to MESSAGE_ID,
                "conversationId" to CONVERSATION_ID,
                "senderId" to PARTNER_UID,
                "senderName" to "Bob",
                "content" to MESSAGE_TEXT,
                "sentAtMillis" to UPDATED_MILLIS,
                "messageType" to "TEXT",
                "attachmentsJson" to "[]",
                "isRead" to 0,
                "syncedToFirestore" to 1,
                "status" to "SENT"
            )
        ),
        UpgradeRow(
            "custody_models",
            CUSTODY_ID,
            "modelType",
            mapOf(
                "id" to CUSTODY_ID,
                "modelType" to "week_on_week_off",
                "patternDays" to CUSTODY_CYCLE,
                "momDaysPattern" to "[0,1,2,3,4,5,6]",
                "startDate" to "2026-01-05",
                "isActive" to 1,
                "repeatYearly" to 1,
                "createdAt" to CREATED,
                "lastModifiedAt" to "2026-05-01T06:00:00",
                "lastModifiedAtMillis" to UPDATED_MILLIS
            )
        ),
        UpgradeRow(
            "journal_entries",
            JOURNAL_ID,
            "text",
            mapOf(
                "id" to JOURNAL_ID,
                "createdByFirebaseUid" to UID,
                "entryDate" to "2026-05-12",
                "text" to "Handover late again — my own note",
                "createdAtMillis" to UPDATED_MILLIS,
                "updatedAtMillis" to UPDATED_MILLIS
            )
        )
    )

    private fun event(id: String, title: String, isPrivate: Boolean) = UpgradeRow(
        "events",
        id,
        "title",
        mapOf(
            "id" to id,
            "title" to title,
            "description" to "Seeded before the upgrade",
            "startDateTime" to EVENT_START,
            "endDateTime" to "2026-05-14T10:30:00",
            "eventType" to "general",
            "parentOwner" to "mom",
            "isRecurring" to 0,
            "createdAt" to CREATED,
            "updatedAt" to CREATED,
            "updatedAtMillis" to UPDATED_MILLIS,
            "syncedToFirestore" to if (isPrivate) 0 else 1,
            "createdByFirebaseUid" to UID,
            "sharedWithJson" to if (isPrivate) "[\"$UID\"]" else "[\"$UID\",\"$PARTNER_UID\"]",
            "permissions" to "read_write",
            "isPrivate" to if (isPrivate) 1 else 0,
            "acceptance" to "NOT_REQUIRED",
            "isImportant" to 0,
            "forMembersJson" to "[\"child:$CHILD_ID\"]"
        )
    )
}
