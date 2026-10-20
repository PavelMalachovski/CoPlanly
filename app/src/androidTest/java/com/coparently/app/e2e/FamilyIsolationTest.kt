package com.coparently.app.e2e

import android.database.Cursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.friends.FriendCalendarRead
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.professionals.ProfessionalRole
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.gson.Gson
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Nothing of one family reaches the other (isolation audit, October 2026: the owner asked for
 * families isolated one hundred per cent).
 *
 * Alice co-parents with Bob (family A) and, later, with Carol (family B). Family B also admits
 * every kind of outsider a family can: a family friend, a professional both its parents consented
 * to, and a guest of one of its children. Each family is filled with records carrying a canary —
 * a word made for this run — in a field a reader would see: Alice's event, expense, child, pet,
 * chat message, parenting-plan answer and change request in A, and Bob's own event and expense;
 * then, with family B on screen, Alice edits every family A record she can, syncs, and writes
 * family B's own records under a second canary.
 *
 * The sweep does not depend on knowing where a leak would land:
 * - **every document in the emulator** that carries a family's canary names that family and its
 *   two parents only, wherever it sits — collection, subcollection, push queue, revision;
 * - **every table of the other side's phones** (Carol's after a full sync, Bob's likewise) holds
 *   neither the canary nor the uid of the parent they share no family with;
 * - **each reader of family B** — Carol, the friend, the professional, the guest — is refused each
 *   family A document by id, and is queued no push about family A;
 * - and each of them can still read what family B gave them, so the refusals are not vacuous.
 *
 * What it cannot see is what a screen draws: `FamilyView` decides that, held by its unit tests.
 */
@RunWith(AndroidJUnit4::class)
class FamilyIsolationTest : TwoParentTest() {

    private val run = UUID.randomUUID().toString().take(RUN_ID_LENGTH)
    private val canaryA = "canaryA$run"
    private val canaryB = "canaryB$run"

    @Test
    fun nothingOfOneFamilyReachesTheOther() = runBlocking<Unit> {
        val familyA = FamilyKey.of(alice.uid, bob.uid)
        val inA = phase("family A: Alice and Bob write records with the canary") { fillFamilyA() }

        val carol = phase("Carol joins and her family goes on screen") { joinCarolAndShowHerFamily() }
        val familyB = FamilyKey.of(alice.uid, carol.uid)
        val outsiders = phase("family B: a friend, a professional and a child's guest") {
            admitOutsiders(carol, familyB)
        }

        phase("Alice edits family A's records with family B on screen, and syncs") {
            editFamilyAWhileShowingB(inA)
        }
        val inB = phase("family B: Alice and Carol write records with the second canary") {
            fillFamilyB(carol).also { alice.syncService.performFullSync().getOrThrow() }
        }

        phase("the sweep: every document") {
            assertCanaryStaysInItsFamily(canaryA, familyA, setOf(alice.uid, bob.uid))
            assertCanaryStaysInItsFamily(canaryB, familyB, setOf(alice.uid, carol.uid))
        }
        phase("the sweep: Carol's and Bob's phones") {
            assertPhoneHoldsNothingOf(carol, canaryA, bob.uid)
            assertPhoneHoldsNothingOf(bob, canaryB, carol.uid)
        }
        val readersOfB = listOf(carol) + outsiders.all
        phase("the sweep: every reader refused the other family's records") {
            readersOfB.forEach { reader -> inA.paths.forEach { path -> assertRefused(reader, path) } }
            inB.paths.forEach { path -> assertRefused(bob, path) }
        }
        phase("the sweep: pushes") {
            readersOfB.forEach { assertNoPushAbout(it, canaryA, familyA, bob.uid) }
            assertNoPushAbout(bob, canaryB, familyB, carol.uid)
        }
        phase("the control: family B's outsiders still read it") {
            assertOutsidersStillReadFamilyB(outsiders, familyB, inB)
        }
    }

    /**
     * Runs one stage of the scenario under [name], and names it in any failure: CI shows the
     * failure's message and not the log, and a bare `PERMISSION_DENIED` says nothing of which of
     * twenty writes it was.
     */
    private suspend fun <T> phase(name: String, block: suspend () -> T): T {
        EmulatorEnvironment.step(name)
        return try {
            block()
        } catch (failure: AssertionError) {
            throw AssertionError("$name: ${failure.message}", failure)
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            throw AssertionError("$name: $failure", failure)
        }
    }

    // ---- Family A ------------------------------------------------------------------------------

    /** The documents family A wrote, by path, and the records Alice edits later. */
    private class FamilyRecords(val paths: List<String>, val events: List<Event>, val child: ChildInfo?, val pet: Pet?)

    private suspend fun fillFamilyA(): FamilyRecords {
        val familyA = FamilyKey.of(alice.uid, bob.uid)
        val event = insertEvent(alice, "$canaryA parents' evening")
        val expense = newExpense(alice, "$canaryA school trip", listOf(alice.uid, bob.uid))
        alice.expenseRepository.addExpense(expense)
        val child = newChild(alice, "$canaryA Mia")
        alice.childInfoRepository.upsertChildInfo(child)
        val pet = newPet(alice, "$canaryA Rex")
        alice.petRepository.upsertPet(pet)
        val message = sendMessage(alice, bob, "$canaryA pickup moved to five")
        alice.parentingPlanRepository.save(
            familyA,
            alice.uid,
            ParentingPlanEntry(
                answers = mapOf(PLAN_QUESTION to "$canaryA alternate weeks"),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        alice.custodyRepository.createWeekOnWeekOff(LocalDate.now())
        val request = requestChange(alice, bob, event, "$canaryA could we swap")
        val bobsEvent = insertEvent(bob, "$canaryA Bob's dentist")
        bob.expenseRepository.addExpense(newExpense(bob, "$canaryA Bob's shoes", listOf(alice.uid, bob.uid)))
        alice.syncService.performFullSync().getOrThrow()
        awaitOnServer("events/${event.id}", "events/${bobsEvent.id}", "expenses/${expense.id}")
        return FamilyRecords(
            paths = listOf(
                "events/${event.id}",
                "events/${bobsEvent.id}",
                "expenses/${expense.id}",
                "child_info/${child.id}",
                "pets/${pet.id}",
                "messages/${message.id}",
                "conversations/${ConversationKey.of(alice.uid, bob.uid)}",
                "parenting_plans/$familyA",
                "custody_models/$familyA",
                "change_requests/${request.id}"
            ),
            events = listOf(event, bobsEvent),
            child = child,
            pet = pet
        )
    }

    /**
     * With family B on screen, Alice edits her own family A event, Bob's family A event (which her
     * phone downloaded), the child and the pet, and syncs — every writer that once named the
     * family on screen instead of the record's own.
     */
    private suspend fun editFamilyAWhileShowingB(inA: FamilyRecords) {
        inA.events.forEach { event ->
            val stored = checkNotNull(alice.eventRepository.getEventById(event.id)) {
                "${event.title} is not on Alice's phone"
            }
            alice.eventRepository.updateEvent(stored.copy(title = "${stored.title} (moved)", updatedAt = now()))
        }
        inA.child?.let { child ->
            val stored = checkNotNull(alice.childInfoRepository.getChildInfoById(child.id))
            alice.childInfoRepository.upsertChildInfo(
                stored.copy(medicalNotes = "$canaryA inhaler", syncedToFirestore = false, updatedAt = now())
            )
        }
        inA.pet?.let { pet ->
            val stored = checkNotNull(alice.petRepository.getPetById(pet.id))
            alice.petRepository.upsertPet(
                stored.copy(feedingNotes = "$canaryA once a day", syncedToFirestore = false, updatedAt = now())
            )
        }
        alice.syncService.performFullSync().getOrThrow()
        alice.syncService.performFullSync().getOrThrow()
    }

    // ---- Family B ------------------------------------------------------------------------------

    /** Carol accepts Alice's second invitation, Alice's phone learns of both families, B goes on screen. */
    private suspend fun joinCarolAndShowHerFamily(): EmulatorParent {
        val carol = newParent("Carol")
        val invite = alice.pairingRepository.createOrReuseInviteCode().getOrThrow()
        carol.pairingRepository.redeem(invite.code).getOrThrow()
        carol.awaitPairedWith(alice.uid)
        // The step of `SyncService.syncUserData` that brings a second co-parent down, as in
        // `MultiFamilyRecordsTest`; keep the three in step.
        val remote = checkNotNull(alice.userRepository.getRemoteUserProfile(alice.uid))
        val local = checkNotNull(alice.database.userDao().getUserById(alice.uid))
        alice.database.userDao().updateUser(local.copy(partnerIdsJson = Gson().toJson(remote.partnerIds)))
        alice.selectedFamilySource.reconcile()
        checkNotNull(alice.selectedFamilySource.select(FamilyKey.of(alice.uid, carol.uid)))
        assertEquals(carol.uid, alice.parentsSource.coParentUid())
        return carol
    }

    /** Family B's outsiders and the child the guest was invited to. */
    private class Outsiders(
        val friend: EmulatorParent,
        val professional: EmulatorParent,
        val professionalGrantId: String,
        val guest: EmulatorParent,
        val guestChildId: String
    ) {
        val all: List<EmulatorParent> get() = listOf(friend, professional, guest)
    }

    private suspend fun admitOutsiders(carol: EmulatorParent, familyB: String): Outsiders {
        val friend = newParent("Gran")
        val friendInvite = carol.friendRepository.inviteFriend(System.currentTimeMillis() + MONTH_MS).getOrThrow()
        friend.friendRepository.acceptFriendInvite(friendInvite.code).getOrThrow()
        assertEquals(listOf(familyB), friend.friendRepository.myGrants().map { it.familyId })

        // Alice invites, with family B on screen: the invitation must name B, not A.
        val professional = newParent("Mediator")
        val proInvite = alice.professionalRepository
            .invite(ProfessionalRole.MEDIATOR, System.currentTimeMillis() + MONTH_MS)
            .getOrThrow()
        val accepted = professional.professionalRepository.acceptInvite(proInvite.code).getOrThrow()
        assertEquals("${familyB}__${professional.uid}", accepted.grantId)
        carol.professionalRepository.consent(accepted.grantId).getOrThrow()

        // Carol's first sync since pairing, which the app runs on the Paired transition: its
        // backfill re-uploads her own records from Room, and a guest granted on the server before
        // it would be dropped by that re-upload (Room does not hold the grant until a pull).
        carol.syncService.performFullSync().getOrThrow()
        val guestChild = newChild(carol, "Ben")
        carol.childInfoRepository.upsertChildInfo(guestChild)
        awaitOnServer("child_info/${guestChild.id}")
        val guest = newParent("Nina")
        val guestInvite = carol.guestRepository
            .inviteGuest(guestChild.id, System.currentTimeMillis() + MONTH_MS)
            .getOrThrow()
        guest.guestRepository.acceptGuestInvite(guestInvite.code).getOrThrow()
        return Outsiders(friend, professional, accepted.grantId, guest, guestChild.id)
    }

    private suspend fun fillFamilyB(carol: EmulatorParent): FamilyRecords {
        val event = insertEvent(alice, "$canaryB swimming")
        val expense = newExpense(alice, "$canaryB swimsuit", listOf(alice.uid, carol.uid))
        alice.expenseRepository.addExpense(expense)
        val child = newChild(alice, "$canaryB Leo")
        alice.childInfoRepository.upsertChildInfo(child)
        alice.messageRepository.ensureConversation(alice.uid, carol.uid, carol.name)
        val message = sendMessage(alice, carol, "$canaryB see you at the pool")
        val carolsEvent = insertEvent(carol, "$canaryB Carol's concert")
        awaitOnServer("events/${event.id}", "events/${carolsEvent.id}", "expenses/${expense.id}")
        return FamilyRecords(
            paths = listOf(
                "events/${event.id}",
                "events/${carolsEvent.id}",
                "expenses/${expense.id}",
                "child_info/${child.id}",
                "messages/${message.id}",
                "conversations/${ConversationKey.of(alice.uid, carol.uid)}"
            ),
            events = listOf(event, carolsEvent),
            child = child,
            pet = null
        )
    }

    // ---- The sweep -----------------------------------------------------------------------------

    /**
     * Every document that carries [canary] names [family] and nobody outside [parents]: in its
     * `familyId`, its audience lists, the uids it is addressed to or written by, its thread — at any
     * depth, so a revision's embedded event and a push's payload are read too. A family-keyed
     * document (a schedule, a plan) must sit under the family's own id.
     */
    private fun assertCanaryStaysInItsFamily(canary: String, family: String, parents: Set<String>) {
        val carrying = EmulatorEnvironment.everyDocumentAsAdmin().filter { (_, doc) ->
            strings(doc).any { it.contains(canary) }
        }
        assertTrue("the canary $canary reached no document: the sweep proves nothing", carrying.isNotEmpty())
        val problems = carrying.flatMap { (path, doc) ->
            val keyedBy = path.split('/').getOrNull(1)
            val misfiled = path.substringBefore('/') in FAMILY_KEYED && keyedBy != family
            listOfNotNull("$path is filed under another family".takeIf { misfiled }) +
                namesOutside(doc, family, parents, ConversationKey.of(parents.first(), parents.last()))
                    .map { "$path: $it" }
        }
        assertTrue("a document of one family names the other:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    /** What [value] names that is not [family], one of [parents] or their [thread], at any depth. */
    private fun namesOutside(value: Any?, family: String, parents: Set<String>, thread: String): List<String> =
        when (value) {
            is Map<*, *> -> value.entries.flatMap { (key, inner) ->
                val stray = when (val name = key as? String) {
                    "familyId" -> (inner as? String)?.takeIf { it.isNotBlank() && it != family }
                    "conversationId" -> (inner as? String)?.takeIf { it != thread }
                    in AUDIENCE_LISTS -> (inner as? List<*>)?.filterIsInstance<String>()
                        ?.filterNot { it in parents }?.takeIf { it.isNotEmpty() }?.joinToString()
                    in PERSON_FIELDS -> (inner as? String)?.takeIf { it.isNotBlank() && it !in parents }
                    else -> null
                }
                listOfNotNull(stray?.let { "$key names $it" }) + namesOutside(inner, family, parents, thread)
            }
            is List<*> -> value.flatMap { namesOutside(it, family, parents, thread) }
            else -> emptyList()
        }

    /**
     * After a full sync and a pull of each listener-only collection, no table of [parent]'s phone
     * holds [canary] or the uid of [stranger], the parent they share no family with.
     */
    private suspend fun assertPhoneHoldsNothingOf(parent: EmulatorParent, canary: String, stranger: String) {
        parent.syncService.performFullSync().getOrThrow()
        parent.childInfoRepository.pullOnce()
        parent.petRepository.pullOnce()
        coroutineScope {
            val listener = launch { parent.expenseRepository.observeRemote() }
            delay(LISTENER_SETTLE_MS)
            listener.cancel()
        }
        val found = rowsContaining(parent, canary) + rowsContaining(parent, stranger)
        assertTrue("${parent.name}'s phone holds the other family's data: $found", found.isEmpty())
    }

    /** `table.column` for every string cell of [parent]'s database that contains [needle]. */
    private fun rowsContaining(parent: EmulatorParent, needle: String): List<String> {
        val db = parent.database.openHelper.readableDatabase
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table')"
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        return tables.flatMap { table ->
            db.query("SELECT * FROM `$table`").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        for (column in 0 until cursor.columnCount) {
                            val text = cursor.takeIf { it.getType(column) == Cursor.FIELD_TYPE_STRING }
                                ?.getString(column)
                            if (text?.contains(needle) == true) add("$table.${cursor.getColumnName(column)}")
                        }
                    }
                }
            }
        }
    }

    /** No push queued for [reader] carries [canary], [family] or [stranger]'s uid. */
    private fun assertNoPushAbout(reader: EmulatorParent, canary: String, family: String, stranger: String) {
        val leaks = EmulatorEnvironment.queryAsAdmin("notification_queue", "targetUserId", reader.uid)
            .filter { push -> strings(push).any { it.contains(canary) || it == family || it == stranger } }
        assertTrue("${reader.name} was pushed the other family's news: $leaks", leaks.isEmpty())
    }

    /** The positive control: each outsider of family B still reads what B gave them. */
    private suspend fun assertOutsidersStillReadFamilyB(outsiders: Outsiders, familyB: String, inB: FamilyRecords) {
        val from = LocalDate.now().minusDays(1)
        val to = LocalDate.now().plusDays(WINDOW_DAYS)
        val bEventIds = inB.events.map { it.id }.toSet()
        withTimeout(EmulatorParent.WAIT_MS) {
            outsiders.friend.friendRepository.observeFamilyEvents(familyB, from, to).first { read ->
                read is FriendCalendarRead.Events && read.events.map { it.id }.containsAll(bEventIds)
            }
        }
        val grant = withTimeout(EmulatorParent.WAIT_MS) {
            outsiders.professional.professionalRepository.observeMyGrants()
                .first { list -> list.any { it.id == outsiders.professionalGrantId } }
        }.single { it.id == outsiders.professionalGrantId }
        withTimeout(EmulatorParent.WAIT_MS) {
            outsiders.professional.professionalRepository.observeFamilyEvents(grant, from, to)
                .first { list -> list.map { it.id }.containsAll(bEventIds) }
        }
        val record = outsiders.guest.firestore.collection("child_info").document(outsiders.guestChildId).get().await()
        assertTrue("the guest lost the child she was invited to", record.exists())
    }

    /** Fails unless [reader] is refused `documents/[path]`, which exists. */
    private suspend fun assertRefused(reader: EmulatorParent, path: String) {
        assertTrue("$path does not exist: the refusal would prove nothing", EmulatorEnvironment.documentExists(path))
        val failure = runCatching { reader.firestore.document(path).get().await() }.exceptionOrNull()
        assertTrue(
            "${reader.name} read $path of the other family (got $failure)",
            failure is FirebaseFirestoreException &&
                failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
    }

    // ---- Writers -------------------------------------------------------------------------------

    /** Saves an event the way the editor does, in the family on [parent]'s screen. */
    private suspend fun insertEvent(parent: EmulatorParent, title: String): Event {
        val event = Event(
            id = UUID.randomUUID().toString(),
            title = title,
            startDateTime = now().plusDays(2),
            eventType = "school",
            parentOwner = parent.database.userDao().getUserById(parent.uid)?.role ?: "mom",
            createdAt = now(),
            updatedAt = now()
        )
        parent.eventRepository.insertEvent(event)
        return event
    }

    private fun newExpense(parent: EmulatorParent, title: String, between: List<String>) = Expense(
        id = UUID.randomUUID().toString(),
        title = title,
        amount = AMOUNT,
        currency = "EUR",
        category = ExpenseCategory.EDUCATION,
        paidBy = parent.uid,
        splitBetween = between,
        splitBasisPoints = HALF_BASIS_POINTS
    )

    private fun newChild(parent: EmulatorParent, name: String) = ChildInfo(
        id = UUID.randomUUID().toString(),
        childName = name,
        dateOfBirth = null,
        medicalNotes = "$name's notes",
        createdAt = now(),
        updatedAt = now(),
        createdByFirebaseUid = parent.uid,
        lastModifiedBy = parent.uid
    )

    private fun newPet(parent: EmulatorParent, name: String) = Pet(
        id = UUID.randomUUID().toString(),
        name = name,
        species = PetSpecies.DOG,
        createdAt = now(),
        updatedAt = now(),
        createdByFirebaseUid = parent.uid,
        lastModifiedBy = parent.uid
    )

    private suspend fun sendMessage(from: EmulatorParent, to: EmulatorParent, text: String): Message {
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = ConversationKey.of(from.uid, to.uid),
            senderId = from.uid,
            senderName = from.name,
            content = text
        )
        from.messageRepository.sendMessage(message)
        awaitOnServer("messages/${message.id}")
        return message
    }

    private suspend fun requestChange(
        from: EmulatorParent,
        to: EmulatorParent,
        event: Event,
        note: String
    ): ChangeRequest {
        val request = ChangeRequest(
            id = UUID.randomUUID().toString(),
            eventId = event.id,
            eventTitle = event.title,
            requestedBy = from.uid,
            requestedTo = to.uid,
            currentStartDateTime = event.startDateTime,
            proposedStartDateTime = event.startDateTime.plusDays(1),
            note = note,
            createdAt = now()
        )
        from.changeRequestRepository.createChangeRequest(request, answerBy = null)
        awaitOnServer("change_requests/${request.id}")
        return request
    }

    private suspend fun awaitOnServer(vararg paths: String) {
        withTimeout(EmulatorParent.WAIT_MS) {
            paths.forEach { path ->
                while (!EmulatorEnvironment.documentExists(path)) delay(POLL_MS)
            }
        }
    }

    /** Every string in [value], keys included, at any depth. */
    private fun strings(value: Any?): List<String> = when (value) {
        is String -> listOf(value)
        is Map<*, *> -> value.keys.flatMap(::strings) + value.values.flatMap(::strings)
        is List<*> -> value.flatMap(::strings)
        else -> emptyList()
    }

    /** Whole seconds: the wire carries `updatedAt` as an instant and drops what is finer. */
    private fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

    private companion object {
        const val RUN_ID_LENGTH = 8
        const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
        const val AMOUNT = 120.0
        const val HALF_BASIS_POINTS = 5_000
        const val PLAN_QUESTION = "care_weekday"
        const val WINDOW_DAYS = 14L
        const val POLL_MS = 250L
        const val LISTENER_SETTLE_MS = 3_000L

        /** Collections whose document id is the family's: a schedule, a plan, a split, a family. */
        val FAMILY_KEYED = setOf("custody_models", "parenting_plans", "family_settings", "families")

        /** Fields that list who may read a document or whom it divides between. */
        val AUDIENCE_LISTS = setOf("sharedWith", "participants", "familyParents", "splitBetween")

        /** Fields that name one person: the addressee, the author, the payer, the editor. */
        val PERSON_FIELDS = setOf(
            "targetUserId",
            "requestedTo",
            "requestedBy",
            "createdByFirebaseUid",
            "lastModifiedBy",
            "paidBy",
            "senderId",
            "editorUid"
        )
    }
}
