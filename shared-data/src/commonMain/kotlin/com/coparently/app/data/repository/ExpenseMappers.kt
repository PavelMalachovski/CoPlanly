package com.coparently.app.data.repository

import com.coparently.app.data.local.entity.ExpenseEntity
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.domain.time.IsoText

// The expense's four mappers, entity and domain and document in both directions. Top-level rather
// than members of `ExpenseRepositoryImpl` since MON-24 phase 2 (Task 8f), so the expenses wire
// contract reaches them without building a repository, as the pets' did in Task 8d; they read no
// repository state, and their bodies are the members' as they were. Public behind
// `InternalSharedApi` for that contract alone: it sits in commonTest's `wire/`, which the app's unit
// tests compile too, and `internal` would stop at this module.

/**
 * Builds the Firestore document map for an expense, stamping [ownerUid] as
 * `createdByFirebaseUid`.
 *
 * `createdByFirebaseUid` mirrors the events schema so the Firestore rules can gate
 * ownership consistently; `paidBy` stays for split/summary logic. Callers decide
 * [ownerUid]: `addExpense` passes the current user, `updateExpense` passes the document's
 * existing owner so ownership stays immutable across edits, as `firestore.rules` requires.
 */
@InternalSharedApi
fun expenseToFirestoreMap(expense: Expense, ownerUid: String): Map<String, Any> = mapOf(
    "id" to expense.id,
    // Replaces the single `childId`, which is no longer written. Nothing is lost by
    // dropping it: no client ever set it, so every document in production carries "".
    "forMembers" to FamilyMemberRef.store(expense.forMembers),
    // Empty rather than an absent key: the read side treats "" as "no family yet", and a
    // document whose fields are iterated should not have a hole where a schema field is.
    "familyId" to (expense.familyId ?: ""),
    "title" to expense.title,
    "amount" to expense.amount,
    "currency" to expense.currency,
    "category" to expense.category.name,
    "createdByFirebaseUid" to ownerUid,
    "paidBy" to expense.paidBy,
    "splitBetween" to expense.splitBetween,
    "date" to IsoText.date(expense.date),
    "receiptUrl" to (expense.receiptUrl ?: ""),
    "notes" to (expense.notes ?: ""),
    "createdAt" to IsoText.dateTime(expense.createdAt),
    // The share this expense was priced at, so both phones agree on a settled month even
    // after the family renegotiates. -1 rather than an omitted key: the read below narrows
    // through `Number` and a negative value is not a share, which is how "recorded before
    // the agreement existed" crosses the wire.
    "splitBasisPoints" to (expense.splitBasisPoints ?: -1)
)

/**
 * An `expenses` document as a domain expense, as `ExpenseRepositoryImpl.observeRemote` downloads
 * it. Throws on a document it cannot read; the caller skips that one document.
 */
@InternalSharedApi
fun expenseFromDocument(data: Map<String, Any?>): Expense = Expense(
    id = data["id"] as String,
    // A co-parent on a build that predates the reference type still writes
    // `childId`, so it is read as a fallback. In practice it converts
    // nothing — the field was never populated by any client.
    familyId = (data["familyId"] as? String)?.takeIf { it.isNotEmpty() },
    forMembers = FamilyMemberRef.parse(data["forMembers"])
        .ifEmpty { FamilyMemberRef.fromLegacyChildId(data["childId"] as? String) },
    title = data["title"] as String,
    amount = (data["amount"] as Number).toDouble(),
    currency = data["currency"] as String,
    category = ExpenseCategory.valueOf(data["category"] as String),
    paidBy = data["paidBy"] as String,
    splitBetween = (data["splitBetween"] as? List<*>)
        ?.filterIsInstance<String>() ?: emptyList(),
    date = IsoText.parseDate(data["date"] as String),
    receiptUrl = (data["receiptUrl"] as? String)?.takeIf { it.isNotEmpty() },
    notes = (data["notes"] as? String)?.takeIf { it.isNotEmpty() },
    createdAt = IsoText.parseDateTime(data["createdAt"] as String),
    syncedToFirestore = true,
    createdByFirebaseUid =
    (data["createdByFirebaseUid"] as? String)?.takeIf { it.isNotEmpty() },
    splitBasisPoints = (data["splitBasisPoints"] as? Number)?.toInt()
        ?.takeIf { it >= 0 }
)

/** A Room row as a domain expense. */
@InternalSharedApi
fun ExpenseEntity.toDomain(): Expense {
    val splitBetween = StoredJson.decodeOrNull(StoredJson.strings, splitBetweenJson).orEmpty()
    val forMembers = StoredJson.decodeOrNull(StoredJson.strings, forMembersJson).orEmpty()

    return Expense(
        id = id,
        familyId = familyId,
        forMembers = FamilyMemberRef.parse(forMembers),
        title = title,
        amount = amount,
        currency = currency,
        category = ExpenseCategory.valueOf(category),
        paidBy = paidBy,
        splitBetween = splitBetween,
        date = date,
        receiptUrl = receiptUrl,
        notes = notes,
        createdAt = createdAt,
        syncedToFirestore = syncedToFirestore,
        createdByFirebaseUid = createdByFirebaseUid,
        splitBasisPoints = splitBasisPoints
    )
}

/** A domain expense as a Room row. */
@InternalSharedApi
fun Expense.toEntity(): ExpenseEntity {
    return ExpenseEntity(
        id = id,
        familyId = familyId,
        // `childId` is a dead column and is left null; see ExpenseEntity.
        forMembersJson = StoredJson.encode(StoredJson.strings, FamilyMemberRef.store(forMembers)),
        title = title,
        amount = amount,
        currency = currency,
        category = category.name,
        paidBy = paidBy,
        splitBetweenJson = StoredJson.encode(StoredJson.strings, splitBetween),
        date = date,
        receiptUrl = receiptUrl,
        notes = notes,
        createdAt = createdAt,
        syncedToFirestore = syncedToFirestore,
        createdByFirebaseUid = createdByFirebaseUid,
        splitBasisPoints = splitBasisPoints
    )
}
