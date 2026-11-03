@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.repository

import com.coparently.app.data.family.inFamilyOnScreen
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.FirestoreExpenseDataSource
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.data.remote.firebase.PushQueue
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.expenses.shareOf
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.family.FamilySlotsSource
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.money.DecimalText
import com.coparently.app.domain.money.JvmDoubleText
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.time.IsoText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

@Suppress("LongParameterList") // one more source: each family's own slots (25 October 2026)
class ExpenseRepositoryImpl(
    private val expenseDao: ExpenseDao,
    private val userDao: UserDao,
    private val firebaseAuthService: SignedInUser,
    private val firestoreExpenseDataSource: FirestoreExpenseDataSource,
    private val activityAnnouncer: ActivityAnnouncer,
    private val fcmService: PushQueue,
    private val familySlots: FamilySlotsSource = FamilySlotsSource.None
) : ExpenseRepository {

    /**
     * The family on screen's expenses. A parent in two families sees one family's money at a
     * time: the other live family's expenses stay out ([inFamilyOnScreen]) — balances settle per
     * pair, and two families' shoes in one list was the tester report this fixes.
     */
    override fun getAllExpenses(): Flow<List<Expense>> {
        return getAllExpensesInEveryFamily().inFamilyOnScreen(
            firebaseAuthService,
            userDao,
            creatorOf = { it.createdByFirebaseUid }
        ) { it.familyId }
    }

    override fun getAllExpensesInEveryFamily(): Flow<List<Expense>> =
        expenseDao.getAllExpenses().map { entities -> entities.map { it.toDomain() } }

    override fun getExpensesForPeriod(start: LocalDate, end: LocalDate): Flow<List<Expense>> {
        return expenseDao.getExpensesForPeriod(start, end).map { entities ->
            entities.map { it.toDomain() }
        }.inFamilyOnScreen(
            firebaseAuthService,
            userDao,
            creatorOf = { it.createdByFirebaseUid }
        ) { it.familyId }
    }

    override suspend fun getExpenseById(id: String): Expense? {
        // The DAO returns pending tombstones so the sync and delete paths can tell "deleted
        // here" from "never existed". To a caller asking on a user's behalf it is simply gone.
        return expenseDao.getExpenseById(id)?.takeIf { it.deletedAtMillis == null }?.toDomain()
    }

    override suspend fun addExpense(expense: Expense) {
        // A brand-new expense has no prior owner, so the current user is the owner — stamped on
        // the Room row too, so the client can enforce creator-only editing without a network
        // read. Null when signed out; the row then reads as "editable by both", which is all
        // this device can honestly say about it.
        val uid = expense.createdByFirebaseUid ?: firebaseAuthService.getCurrentUid()
        val owned = expense.copy(
            createdByFirebaseUid = uid,
            // Which relationship this belongs to, and therefore who may see it. A new expense from
            // the form arrives stamped by `ExpenseViewModel`, which reads the family once for the
            // whole save. Otherwise null while the account is unpaired: the expense is this
            // parent's alone until there is somebody to share it with, and inventing an id for a
            // pair of one would say otherwise. Pairing backfills, the same way the audience on
            // events is re-stamped.
            familyId = expense.familyId
                ?: FamilyKey.orNull(uid, userDao.getUserById(uid.orEmpty())?.partnerId)
        )

        // Room is the source of truth — persist locally first so the expense is never
        // lost, even if the Firestore push below fails.
        expenseDao.insertExpense(owned.toEntity())

        announce(owned, ActivityKind.EXPENSE_ADDED)?.let { coParent -> pushNewExpense(owned, coParent) }

        val signedInUid = firebaseAuthService.getCurrentUid() ?: return
        pushToFirestore(owned, ownerUid = owned.createdByFirebaseUid ?: signedInUid)
    }

    override suspend fun updateExpense(expense: Expense) {
        expenseDao.insertExpense(expense.toEntity())
        announce(expense, ActivityKind.EXPENSE_UPDATED)

        val signedInUid = firebaseAuthService.getCurrentUid() ?: return
        try {
            // Ownership is immutable in `firestore.rules` (update requires
            // request.resource.data.createdByFirebaseUid == resource.data.createdByFirebaseUid).
            // Read back the existing owner instead of re-stamping the caller's uid — the
            // stamping addExpense does is only correct for a document that does not exist yet.
            // Without this, a co-parent editing an expense they didn't create would flip the
            // owner field, Firestore would reject the write, and the edit would sit in Room
            // with syncedToFirestore = false forever, failing again on every retry.
            val existingOwnerUid = firestoreExpenseDataSource.getExpense(expense.id)
                ?.get("createdByFirebaseUid") as? String
            pushToFirestore(expense, ownerUid = existingOwnerUid ?: signedInUid)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.w("ExpenseRepo", "Expense owner lookup failed; kept locally", e)
        }
    }

    /**
     * Writes [expense] to Firestore stamped with [ownerUid] and, on success, marks the local
     * row as synced.
     *
     * A rejected or failed write must never crash the app — the expense is already saved
     * locally and will re-sync later. (A PERMISSION_DENIED here used to be fatal.)
     */
    private suspend fun pushToFirestore(expense: Expense, ownerUid: String) {
        try {
            firestoreExpenseDataSource.setExpense(expense.id, expenseToFirestoreMap(expense, ownerUid))
            expenseDao.insertExpense(expense.copy(syncedToFirestore = true).toEntity())
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.w("ExpenseRepo", "Expense Firestore sync failed; kept locally", e)
        }
    }

    /**
     * Deletes an expense, in a way the co-parent can actually find out about (CQ-3).
     *
     * The row is **tombstoned** rather than removed — hidden from every query at once, kept in
     * Room as an outbox entry until the deletion reaches Firestore. See
     * [com.coparently.app.data.repository.EventRepositoryImpl.deleteEvent]; the reasoning is
     * identical and so is the shape.
     *
     * The known gap this replaces was worse here than for events. The `expenses` delete rule
     * admits only the creator, so a co-parent's delete removed the local row, was refused
     * remotely, and the next sync pulled the expense back — a delete that undid itself in front
     * of the person who asked for it. Tombstoning does not widen that rule (`update` is
     * creator-only too, by an owner decision from the August 2026 walkthrough), so a co-parent's
     * delete is still refused. What changes is that it now *stays* refused visibly — the
     * tombstone is queued and retried rather than silently lost — and that the creator's own
     * delete finally reaches the other phone.
     */
    override suspend fun deleteExpense(expenseId: String) {
        // Read before marking: the announcement names the expense, and a tombstoned row is
        // invisible to every query that could name it afterwards.
        val deleted = expenseDao.getExpenseById(expenseId)?.toDomain()
        val deletedAtMillis = Clock.System.now().toEpochMilliseconds()
        expenseDao.markDeleted(expenseId, deletedAtMillis)
        deleted?.let { announce(it, ActivityKind.EXPENSE_DELETED) }

        val signedInUid = firebaseAuthService.getCurrentUid() ?: return
        val tombstoned = firestoreExpenseDataSource.tombstoneExpense(
            expenseId = expenseId,
            deletedAtMillis = deletedAtMillis,
            deletedBy = signedInUid
        )
        if (tombstoned.isSuccess) {
            expenseDao.deleteExpense(expenseId)
        } else {
            logger.w(
                "ExpenseRepo",
                "Expense tombstone not written; the deletion stays queued for the next sync",
                tombstoned.exceptionOrNull()
            )
        }
    }

    override suspend fun observeRemote() {
        val signedInUid = firebaseAuthService.getCurrentUid() ?: return
        val partnerId = userDao.getUserById(signedInUid)?.partnerId
        // The family, not the two authors. An unpaired account gets null and the data source
        // closes without querying, which is right: there is nothing shared to download, and an
        // unfiltered read would be denied.
        val familyId = FamilyKey.orNull(signedInUid, partnerId)

        retryPendingDeletions(signedInUid)

        firestoreExpenseDataSource.getAllExpenses(familyId)
            .catch { e -> logger.w("ExpenseRepo", "Expense sync failed", e) }
            .collect { expenses ->
                expenses.forEach { data ->
                    val id = data["id"] as String

                    // The co-parent deleted it. Applied before anything is parsed, and without
                    // consulting any timestamp — see `SyncService.syncEvents` for why a
                    // deletion wins outright rather than by comparison.
                    if (Tombstone.isDeleted(data)) {
                        expenseDao.deleteExpense(id)
                        return@forEach
                    }

                    // The mirror image: this device deleted it and the tombstone has not been
                    // written yet, so the document is still alive. Writing it back here would
                    // undo the parent's own delete between two attempts to deliver it.
                    if (expenseDao.getExpenseById(id)?.deletedAtMillis != null) {
                        return@forEach
                    }

                    // One document that does not parse must not take the whole stream down —
                    // `.catch` above covers the listener, not this lambda, and an unchecked
                    // cast here used to crash both parents' Expenses screens on every open
                    // until the document went away.
                    val expense = runCatching {
                        expenseFromDocument(data)
                    }.getOrElse { e ->
                        logger.w("ExpenseRepo", "Skipping an expense document that does not parse: $id", e)
                        return@forEach
                    }
                    expenseDao.insertExpense(expense.toEntity())
                }
            }
    }

    /**
     * Re-attempts every deletion this device has not managed to write yet.
     *
     * Expenses have no general upload pass — `getUnsyncedExpenses()` exists but has never had a
     * caller, because an add or an edit pushes to Firestore inline. Deletions cannot work that
     * way: the inline push is exactly the one that fails when the phone is offline or the rule
     * refuses, and a failure there used to be the end of it. This is the retry the delete path
     * did not have.
     *
     * Deliberately scoped to tombstones rather than to everything `getUnsyncedExpenses()`
     * returns. Uploading the rest would change what an unsynced expense means and could
     * re-publish rows that were left unsynced on purpose; that is a separate question from
     * whether a delete survives, and it is not answered here.
     */
    private suspend fun retryPendingDeletions(userId: String) {
        val pending = expenseDao.getUnsyncedExpenses().filter { it.deletedAtMillis != null }
        for (entity in pending) {
            val deletedAtMillis = entity.deletedAtMillis ?: continue
            val tombstoned = firestoreExpenseDataSource.tombstoneExpense(
                expenseId = entity.id,
                deletedAtMillis = deletedAtMillis,
                // Whoever is deleting — this device's signed-in parent — not the expense's
                // creator: the co-parent may be the one who deleted it, and `deleteExpense` names
                // the deleter too, so a retry of the same deletion must not name somebody else.
                deletedBy = userId
            )
            if (tombstoned.isSuccess) {
                expenseDao.deleteExpense(entity.id)
            } else {
                logger.w(
                    "ExpenseRepo",
                    "Expense tombstone for ${entity.id} not written; it stays queued",
                    tombstoned.exceptionOrNull()
                )
            }
        }
    }

    override suspend fun questionExpense(expense: Expense): Boolean {
        val myUid = firebaseAuthService.getCurrentUid() ?: return false
        val creator = expense.createdByFirebaseUid
        if (creator.isNullOrBlank() || creator == myUid) return false
        return announce(expense, ActivityKind.EXPENSE_QUESTIONED) != null
    }

    /**
     * Tells the co-parent about an expense.
     *
     * An expense is a shared fact by construction — there is no private expense — so the only
     * thing that silences it is having no family to tell: an ended family, or no family while the
     * parent has two (`FamilyAudience.partnerFor`).
     *
     * The amount is passed already formatted, with its currency beside it. The app never converts
     * between currencies (CLAUDE.md), so a reader must be able to group by currency without
     * parsing the formatted string back apart.
     *
     * Never throws — see `ActivityAnnouncer`. A parent's expense must not fail to save because
     * their co-parent's chat listener is down.
     */
    private suspend fun announce(expense: Expense, kind: ActivityKind): String? {
        val myUid = firebaseAuthService.getCurrentUid() ?: return null
        val me = userDao.getUserById(myUid)
        // The thread of the expense's own family, never the family on screen: an expense of an
        // ended family, or of no family while there are two, has nobody to tell (October 2026
        // isolation audit — such a card carried the title and the amount to the other family).
        val coParent = FamilyAudience.partnerFor(
            familyId = expense.familyId,
            myUid = myUid,
            livePartners = EventVersionDocument.decodeAudience(me?.partnerIdsJson ?: "[]"),
            selectedPartner = me?.partnerId
        )
        activityAnnouncer.announce(
            announcement = ActivityAnnouncement(
                kind = kind,
                entityType = ActivityEntityType.EXPENSE,
                entityId = expense.id,
                title = expense.title,
                whenIso = IsoText.date(expense.date),
                amount = "${JvmDoubleText.of(expense.amount)} ${expense.currency}",
                currency = expense.currency
            ),
            senderName = me?.name.orEmpty(),
            suppress = coParent == null,
            partnerUid = coParent
        )
        return coParent
    }

    /**
     * Tells [coParent] about a new expense with a push of its own, with what it costs them
     * (tester feedback, 21 October 2026: "when I add an expense to split, the other parent should
     * be notified"). The chat card [announce] posts is pushed as a message too; this one says the
     * figures and lands in the Money channel, and its tap opens Expenses.
     *
     * The figures are plain decimals and the currency's code; the receiving phone formats them in
     * its reader's language (SEC-3: a push carries facts, the reader's phone writes the sentence).
     * Their share is left out when the expense is not split with them. Never throws: the expense
     * is saved whether or not the push can be queued.
     */
    private suspend fun pushNewExpense(expense: Expense, coParent: String) {
        val myUid = firebaseAuthService.getCurrentUid() ?: return
        val me = userDao.getUserById(myUid)
        // This parent's slot in the expense's family, which may differ from the profile's
        // (per-family slots, 25 October 2026).
        val inFamily = familySlots.get(FamilyKey.of(myUid, coParent))?.of(myUid)
        val mySlot = (inFamily ?: me?.role)?.takeIf { it == ContactWindow.SLOT_ONE || it == ContactWindow.SLOT_TWO }
        val roleByUid = buildMap {
            if (mySlot != null) {
                put(myUid, mySlot)
                put(coParent, if (mySlot == ContactWindow.SLOT_ONE) ContactWindow.SLOT_TWO else ContactWindow.SLOT_ONE)
            }
        }
        val share = shareOf(expense, coParent, roleByUid).takeIf { coParent in expense.splitBetween }
        val data = buildMap {
            put(PushPayload.TYPE, PushPayload.EXPENSE_ADDED)
            put(PushPayload.ACTOR, me?.name.orEmpty().take(ACTOR_MAX))
            put(PushPayload.SUBJECT, expense.title.take(PushPayload.SUBJECT_MAX))
            put(PushPayload.AMOUNT, plainMoney(expense.amount))
            put(PushPayload.CURRENCY, expense.currency)
            share?.let { put(PushPayload.SHARE, plainMoney(expense.amount * it)) }
        }
        fcmService.queueNotificationForUser(coParent, data)
    }

    private companion object {
        /** The longest actor name `firestore.rules` accepts on a push. */
        const val ACTOR_MAX = 100

        /** Two decimals, as the push's `amount` and `share` carry them. */
        const val MONEY_SCALE = 2
    }

    /**
     * [amount] as a plain decimal with two places, as the push carries it ("1000.00"):
     * `BigDecimal.valueOf(amount).setScale(2, HALF_UP).toPlainString()`, which [DecimalText.halfUp]
     * is held to (`DecimalTextParityTest`).
     */
    private fun plainMoney(amount: Double): String = DecimalText.halfUp(amount, MONEY_SCALE)
}
