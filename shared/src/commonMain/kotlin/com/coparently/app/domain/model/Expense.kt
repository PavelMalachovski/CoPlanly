package com.coparently.app.domain.model

import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.time.wallClockNow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Domain model representing an expense for a child.
 * This is the clean architecture model used in the domain layer.
 *
 * @property id Unique identifier for the expense
 * @property forMembers The children and pets this expense is about. Empty means the whole
 *   family, which is what every record written before the reference type holds — see
 *   [com.coparently.app.domain.family.FamilyMemberRef]. It replaces a single `childId`, which
 *   had nowhere to put a vet's bill.
 * @property title Title/description of the expense
 * @property amount Amount spent
 * @property currency Currency code (default: USD)
 * @property category Category of the expense
 * @property paidBy Firebase UID of the parent who paid
 * @property splitBetween List of Firebase UIDs to split the expense between
 * @property date Date of the expense; by default today on the device's clock
 * @property receiptUrl `RecordPhotoCodec` reference to the receipt photo, or null (L-4). The
 *   name is older than the format; it is never a download URL.
 * @property notes Optional notes about the expense
 * @property createdAt Timestamp when the expense was created, on the device's wall clock
 * @property syncedToFirestore Whether the expense has been synced to Firestore
 * @property createdByFirebaseUid Who created this expense — the uid edits are gated on. Null on
 *   rows recorded before the field existed, which read as "editable by both", exactly what they
 *   were.
 * @property splitBasisPoints Slot 1's agreed share of this expense when it was recorded, in
 *   basis points; null for a row that predates the agreement.
 */
data class Expense(
    val id: String,
    val forMembers: List<FamilyMemberRef> = emptyList(),
    val title: String,
    val amount: Double,
    val currency: String = "USD",
    val category: ExpenseCategory,
    val paidBy: String,
    val splitBetween: List<String> = emptyList(),
    val date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val receiptUrl: String? = null,
    val notes: String? = null,
    val createdAt: LocalDateTime = wallClockNow(),
    val syncedToFirestore: Boolean = false,
    val createdByFirebaseUid: String? = null,
    /**
     * Slot 1's agreed share of this expense, in basis points, **as it stood when the expense was
     * recorded**.
     *
     * Snapshotted rather than read live from the family's current agreement, and that is the
     * whole design decision: a renegotiated split must not silently re-price a month the two
     * parents had already settled and argued about. Null means an expense recorded before the
     * agreement existed, which the balance math reads as an even split — what it was.
     */
    val splitBasisPoints: Int? = null,
    /**
     * The co-parenting relationship this record belongs to, or null while it belongs to nobody
     * but its creator. See [com.coparently.app.domain.model.Event.familyId].
     */
    val familyId: String? = null
)

/**
 * Categories for child-related expenses.
 */
enum class ExpenseCategory {
    EDUCATION,
    MEDICAL,
    CLOTHING,
    FOOD,
    ACTIVITIES,
    TRANSPORTATION,
    TOYS,
    HOUSEHOLD,
    OTHER;

    val displayName: String
        get() = when (this) {
            EDUCATION -> "Education"
            MEDICAL -> "Medical"
            CLOTHING -> "Clothing"
            FOOD -> "Food"
            ACTIVITIES -> "Activities"
            TRANSPORTATION -> "Transportation"
            TOYS -> "Toys"
            HOUSEHOLD -> "Household"
            OTHER -> "Other"
        }

    val icon: String
        get() = when (this) {
            EDUCATION -> "school"
            MEDICAL -> "medical_services"
            CLOTHING -> "checkroom"
            FOOD -> "restaurant"
            ACTIVITIES -> "sports_soccer"
            TRANSPORTATION -> "directions_car"
            TOYS -> "toys"
            HOUSEHOLD -> "home"
            OTHER -> "more_horiz"
        }
}
