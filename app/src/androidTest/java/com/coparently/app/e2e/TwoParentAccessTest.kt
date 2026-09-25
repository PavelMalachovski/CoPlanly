package com.coparently.app.e2e

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.chat.DepartedThreadSource
import com.coparently.app.data.remote.firebase.FirebaseImageStorage
import com.coparently.app.data.remote.firebase.FirestoreChildInfoDataSource
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.data.session.AccountDeletionService
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.chat.DepartedThread
import com.coparently.app.domain.custody.SharedCustodyRead
import com.coparently.app.domain.export.ExportFingerprint
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.RecordId
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.friends.FriendProfile
import com.coparently.app.domain.friends.FriendRole
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalGrantPolicy
import com.coparently.app.domain.professionals.ProfessionalGrantStatus
import com.coparently.app.domain.professionals.ProfessionalRole
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Everyone who is not one of the two parents, and the links that end: a calendar friend, a guest,
 * a professional, a calendar-feed URL and an export receipt — each through the production
 * repository and the real callable on the Functions emulator, against the real `firestore.rules`
 * and `storage.rules` — plus the two ways a pairing ends (unpair, account deletion) and the
 * receipt photo an expense carries.
 *
 * What the rules suites prove offline is each rule alone. What this proves is that the grant a
 * callable writes is the one the rule reads: a friend's family-scoped query succeeds while the
 * grant stands and is refused once a parent revokes it (CLAUDE.md, "A calendar friend sits beside
 * the two slots"); a professional reads nothing on one parent's consent, the calendar, plan and
 * schedule on both, and never the chat (item 29); a feed serves a shared event but not a private
 * or deleted one and answers 404 once revoked (item 27); an export hash is create-once and
 * verifies without an account (item 26, MON-16).
 *
 * What it cannot do: deliver a push (FCM has no emulator — the queued document is asserted), open
 * the feed in a calendar app, or render any of the screens. The friend's event query is the shape
 * `firestore-tests/rules/friend-calendar.test.js` names as the one a friend client must run: no
 * screen in the app issues it yet, so it is run here directly on the friend's Firestore client.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentAccessTest : TwoParentTest() {

    @Test
    fun aCalendarFriendReadsTheFamilysEventsUntilAParentRevokesTheGrant() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)
        val event = insertEvent(alice, "Swimming lesson")

        EmulatorEnvironment.step("Alice invites a calendar friend; Grandma redeems the code")
        val invite = alice.friendRepository.inviteFriend(System.currentTimeMillis() + MONTH_MS).getOrThrow()
        val grandma = newParent("Grandma")
        val accepted = grandma.friendRepository.acceptFriendInvite(invite.code).getOrThrow()
        assertEquals(listOf(alice.uid, bob.uid).sorted(), accepted.familyParents.sorted())
        assertEquals(invite.grantExpiresAtMillis, accepted.expiresAtMillis)

        // The grant names the one family it was issued for (M-6), and the friend can read it.
        val grant = checkNotNull(grandma.friendRepository.myGrant()) { "Grandma cannot read her own grant" }
        val stored = grandma.firestore.collection("calendar_friends").document(grandma.uid).get().await()
        assertEquals(familyId, stored.getString("familyId"))
        // Bob, who did not send the invitation, sees the friend in his list too.
        withTimeout(EmulatorParent.WAIT_MS) {
            bob.friendRepository.observeFamilyFriends().first { list -> list.any { it.friendUid == grandma.uid } }
        }

        // The friend authors her own profile; a parent reads it and cannot write it.
        grandma.friendRepository.saveMyProfile(
            FriendProfile(
                uid = grandma.uid,
                name = grandma.name,
                role = FriendRole.GRANDPARENT,
                familyParents = grant.familyParents
            )
        ).getOrThrow()
        val profile = withTimeout(EmulatorParent.WAIT_MS) {
            alice.friendRepository.observeFriendProfile(grandma.uid).first { it != null }
        }
        assertEquals(FriendRole.GRANDPARENT, profile?.role)
        assertRefused("a parent writing the friend's profile") {
            alice.firestore.collection("friend_profiles").document(grandma.uid).update("name", "Someone").await()
        }

        EmulatorEnvironment.step("Grandma reads the family's events, and cannot extend her own grant")
        assertTrue(event.id in friendsEventIds(grandma, familyId, grant.familyParents))
        assertRefused("a friend extending her own grant") {
            grandma.firestore.collection("calendar_friends").document(grandma.uid)
                .update("expiresAtMillis", Long.MAX_VALUE).await()
        }

        EmulatorEnvironment.step("Bob revokes the grant")
        bob.friendRepository.revokeFriend(grandma.uid).getOrThrow()
        assertNull(grandma.friendRepository.myGrant())
        assertRefused("a revoked friend's event query") { friendsEventIds(grandma, familyId, grant.familyParents) }
    }

    @Test
    fun aGuestReadsTheOneChildRecordSheWasInvitedToAndNothingElse() = runBlocking<Unit> {
        val now = LocalDateTime.now()
        val child = ChildInfo(
            id = UUID.randomUUID().toString(),
            childName = "Ema",
            dateOfBirth = null,
            createdAt = now,
            updatedAt = now,
            createdByFirebaseUid = alice.uid,
            lastModifiedBy = alice.uid
        )
        alice.childInfoRepository.upsertChildInfo(child)
        assertTrue(EmulatorEnvironment.documentExists("child_info/${child.id}"))
        val event = insertEvent(alice, "Parents' evening")

        EmulatorEnvironment.step("Alice invites a guest to Ema's record; Nina redeems the code")
        val invite = alice.guestRepository.inviteGuest(child.id, System.currentTimeMillis() + MONTH_MS).getOrThrow()
        val nina = newParent("Nina")
        val accepted = nina.guestRepository.acceptGuestInvite(invite.code).getOrThrow()
        assertEquals(child.id, accepted.childInfoId)
        assertEquals(invite.grantExpiresAtMillis, accepted.expiresAtMillis)

        // The record, read by id through the production data source on the guest's phone.
        val record = checkNotNull(FirestoreChildInfoDataSource(nina.firestore).getChildInfoById(child.id)) {
            "The guest cannot read the record she was invited to"
        }
        assertEquals("Ema", record["childName"])
        assertTrue(nina.uid in (record["sharedWith"] as List<*>))
        // Alice's own read shows the grant, so she can see who is reading and revoke it.
        val asAliceSeesIt = checkNotNull(FirestoreChildInfoDataSource(alice.firestore).getChildInfoById(child.id))
        assertTrue(nina.uid in (asAliceSeesIt["guests"] as Map<*, *>).keys)

        EmulatorEnvironment.step("A guest reads; she does not write, and sees no calendar")
        assertRefused("a guest editing the child record") {
            nina.firestore.collection("child_info").document(child.id).update("childName", "Someone").await()
        }
        assertRefused("a guest reading the family's event") {
            nina.firestore.collection("events").document(event.id).get().await()
        }
        // A second redemption of the same code grants nothing.
        assertTrue(bob.guestRepository.acceptGuestInvite(invite.code).isFailure)
    }

    @Test
    fun aProfessionalReadsNothingUntilBothParentsConsentAndNeverTheChat() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)
        val event = seedFamilyRecords(familyId)

        EmulatorEnvironment.step("Alice invites a mediator; the mediator redeems the code")
        val invite = alice.professionalRepository
            .invite(ProfessionalRole.MEDIATOR, System.currentTimeMillis() + MONTH_MS)
            .getOrThrow()
        val mediator = newParent("Mediator")
        val accepted = mediator.professionalRepository.acceptInvite(invite.code).getOrThrow()
        assertEquals("${familyId}__${mediator.uid}", accepted.grantId)

        // The server asks the other parent for consent — a type, never a sentence (item 15).
        val push = EmulatorEnvironment.awaitQueuedPush(bob.uid, PROFESSIONAL_ACCESS_REQUESTED)
        assertEquals(familyId, push["familyId"])
        assertEquals(mediator.name, push["actorName"])

        val grant = withTimeout(EmulatorParent.WAIT_MS) {
            mediator.professionalRepository.observeMyGrants().first { list -> list.any { it.id == accepted.grantId } }
        }.single { it.id == accepted.grantId }
        assertEquals(setOf(alice.uid), grant.consents.keys)
        val asBobSeesIt = withTimeout(EmulatorParent.WAIT_MS) {
            bob.professionalRepository.observeFamilyGrants().first { list -> list.any { it.id == grant.id } }
        }.single { it.id == grant.id }
        assertEquals(
            ProfessionalGrantStatus.WAITING_FOR_YOU,
            ProfessionalGrantPolicy.statusFor(asBobSeesIt, bob.uid, System.currentTimeMillis())
        )

        EmulatorEnvironment.step("One consent opens nothing")
        assertMediatorReadsNothing(mediator, grant, event)

        EmulatorEnvironment.step("Bob consents: the calendar, the plan and the schedule open")
        bob.professionalRepository.consent(grant.id).getOrThrow()
        assertMediatorReadsTheFamily(mediator, grant, event)

        EmulatorEnvironment.step("Never the chat, even with both consents")
        val conversationId = ConversationKey.of(alice.uid, bob.uid)
        assertRefused("a professional reading the conversation") {
            mediator.firestore.collection("conversations").document(conversationId).get().await()
        }
        assertRefused("a professional querying the messages") {
            mediator.firestore.collection("messages").whereEqualTo("conversationId", conversationId).get().await()
        }

        EmulatorEnvironment.step("Alice revokes alone")
        alice.professionalRepository.revoke(grant.id).getOrThrow()
        assertFalse(EmulatorEnvironment.documentExists("professional_grants/${grant.id}"))
        assertRefused("a revoked professional reading an event") {
            mediator.firestore.collection("events").document(event.id).get().await()
        }
    }

    @Test
    fun unpairingEndsTheLinkOnBothPhonesTellsBobAndNarrowsTheAudience() = runBlocking<Unit> {
        val event = insertEvent(alice, "Before the unpair")
        assertTrue(bob.firestore.collection("events").document(event.id).get().await().exists())

        EmulatorEnvironment.step("Alice unpairs")
        alice.pairingRepository.unpair().getOrThrow()
        awaitNotPaired(alice)
        awaitNotPaired(bob)

        // Queued by `unpairCoParent` itself: a client may not produce this type (item 15).
        val push = EmulatorEnvironment.awaitQueuedPush(bob.uid, PAIRING_REMOVED)
        assertEquals(alice.name, push["actorName"])
        // The callable narrowed the audience of what Alice shared before it returned.
        assertRefused("Bob reading Alice's event after the unpair") {
            bob.firestore.collection("events").document(event.id).get().await()
        }
    }

    @Test
    fun aCalendarFeedServesTheSharedEventsOnlyAndStopsWhenRevoked() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)
        val shared = insertEvent(alice, "Feed parents evening")
        val privateEvent = insertEvent(alice, "Feed private note", isPrivate = true)
        val deleted = insertEvent(alice, "Feed cancelled trip")
        alice.eventRepository.deleteEvent(deleted)
        eventually("the deletion of ${deleted.id} to reach the server") {
            EmulatorEnvironment.queryAsAdmin("events", "id", deleted.id).singleOrNull()?.get("deletedAtMillis")
        }

        EmulatorEnvironment.step("Alice creates a feed and a calendar app fetches it")
        val created = alice.calendarFeedRepository.create(familyId, "en").getOrThrow()
        assertTrue(created.webcalUrl.startsWith("webcal:"))
        // The URL names the deployed host; the path's last segment is `<token>.ics`, which is all
        // the function reads, so the same segment is fetched from the emulator.
        val tokenSegment = created.url.substringAfterLast('/')
        assertTrue(tokenSegment.endsWith(".ics"))
        val feedUrl = "${functionUrl("calendarFeed")}/$tokenSegment"
        val (status, body) = httpGet(feedUrl)
        assertEquals(HTTP_OK, status)
        assertTrue(body.startsWith("BEGIN:VCALENDAR"))
        assertTrue("the shared event is missing from the feed", body.contains("SUMMARY:${shared.title}"))
        assertFalse("a private event was served", body.contains(privateEvent.title))
        assertFalse("a deleted event was served", body.contains(deleted.id))

        // Listed for its owner, and for its owner only.
        assertTrue(alice.calendarFeedRepository.list().getOrThrow().any { it.feedId == created.feedId })
        assertTrue(alice.calendarFeedRepository.list().getOrThrow().all { it.familyId == familyId })
        assertTrue(bob.calendarFeedRepository.list().getOrThrow().none { it.feedId == created.feedId })

        EmulatorEnvironment.step("Alice revokes the feed")
        alice.calendarFeedRepository.revoke(created.feedId).getOrThrow()
        assertEquals(HTTP_NOT_FOUND, httpGet(feedUrl).first)
        assertTrue(alice.calendarFeedRepository.list().getOrThrow().none { it.feedId == created.feedId })
    }

    @Test
    fun anExportHashIsRegisteredOnceAndVerifiesWithoutAnAccount() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)
        val from = LocalDate.now().minusDays(PERIOD_DAYS)
        val recordId = checkNotNull(alice.exportReceipts.reserve(familyId, from, LocalDate.now(), ExportFormat.PDF)) {
            "reserveExportRecordId returned no id"
        }
        val bytes = "%PDF-1.4 e2e ${UUID.randomUUID()}".toByteArray()
        val sha256 = ExportFingerprint.sha256Hex(bytes)
        val otherBytes = "%PDF-1.4 a different file".toByteArray()

        EmulatorEnvironment.step("Only the parent who reserved the id registers, and only once")
        assertFalse("Bob registered under Alice's id", bob.exportReceipts.register(recordId, sha256, bytes.size))
        assertTrue(alice.exportReceipts.register(recordId, sha256, bytes.size))
        // A retry of the same hash is the original registration, not a failure.
        assertTrue(alice.exportReceipts.register(recordId, sha256, bytes.size))
        assertFalse(
            "a second hash was accepted under a registered id",
            alice.exportReceipts.register(recordId, ExportFingerprint.sha256Hex(otherBytes), otherBytes.size)
        )

        EmulatorEnvironment.step("A verifier with no account looks the file up")
        val byHash = verifyExport(JSONObject().put("sha256", sha256))
        assertTrue(byHash.getBoolean("found"))
        assertEquals(recordId, byHash.getString("recordId"))
        assertEquals(ExportFormat.PDF.wireName, byHash.getString("format"))
        assertEquals(bytes.size, byHash.getInt("byteLength"))
        // The receipt names nobody.
        assertFalse(byHash.has("familyId"))
        assertFalse(byHash.toString().contains(alice.uid))
        val byId = verifyExport(JSONObject().put("recordId", RecordId.display(recordId)))
        assertEquals(byHash.getLong("recordedAtMillis"), byId.getLong("recordedAtMillis"))
        val unknown = verifyExport(JSONObject().put("sha256", ExportFingerprint.sha256Hex(otherBytes)))
        assertFalse(unknown.getBoolean("found"))
    }

    @Test
    fun aReceiptPhotoAliceAttachesOpensForBobAndIsRefusedToASignedOutClient() = runBlocking<Unit> {
        val expenseId = UUID.randomUUID().toString()
        val receiptUrl = FirebaseImageStorage(context, alice.storage)
            .uploadReceipt(expenseId, Uri.fromFile(receiptPhoto()).toString())
        alice.expenseRepository.addExpense(
            Expense(
                id = expenseId,
                title = "Pharmacy",
                amount = RECEIPT_AMOUNT,
                currency = "EUR",
                category = ExpenseCategory.MEDICAL,
                paidBy = alice.uid,
                splitBetween = listOf(alice.uid, bob.uid),
                receiptUrl = receiptUrl
            )
        )

        EmulatorEnvironment.step("Bob's phone downloads the expense and opens its receipt")
        val downloaded = downloadExpenseOnBobsPhone(expenseId)
        assertEquals(receiptUrl, downloaded.receiptUrl)
        val path = "receipts/$expenseId.jpg"
        val photo = bob.storage.reference.child(path).getBytes(MAX_RECEIPT_BYTES).await()
        assertTrue("Bob's download is not a JPEG", photo.size > 2 && photo[0] == JPEG_0 && photo[1] == JPEG_1)

        EmulatorEnvironment.step("A client with no account is refused")
        val signedOut = EmulatorEnvironment.startFirebaseApp(context, "e2e-signed-out-${UUID.randomUUID()}")
        try {
            val failure = runCatching {
                FirebaseStorage.getInstance(signedOut).reference.child(path).getBytes(MAX_RECEIPT_BYTES).await()
            }.exceptionOrNull()
            assertTrue("a signed-out client read a receipt: $failure", failure is StorageException)
        } finally {
            signedOut.delete()
        }
    }

    @Test
    fun deletingBobsAccountErasesHisRecordsAndUnpairsAlice() = runBlocking<Unit> {
        val bobUid = bob.uid
        val event = insertEvent(bob, "Bob's football")

        EmulatorEnvironment.step("Bob deletes his account")
        AccountDeletionService(
            functions = FirebaseFunctions.getInstance(bob.app),
            database = bob.database,
            encryptedPreferences = bob.encryptedPreferences,
            fcmService = bob.fcmService
        ).deleteAccount().getOrThrow()

        assertFalse("Bob's profile survived", EmulatorEnvironment.documentExists("users/$bobUid"))
        assertFalse("Bob's event survived", EmulatorEnvironment.documentExists("events/${event.id}"))
        assertNull("Bob's phone kept his profile row", bob.database.userDao().getUserById(bobUid))
        awaitNotPaired(alice)
    }

    /**
     * GDPR review, September 2026: the thread is Alice's record too. Bob's deletion keeps it for
     * thirty days — readable, exportable, closed to new messages — and tells Alice once, with the
     * deadline, instead of the generic `pairing_removed`.
     */
    @Test
    fun deletingBobsAccountKeepsTheThreadForAliceToExportAndTellsHerOnce() = runBlocking<Unit> {
        val bobUid = bob.uid
        val conversationId = ConversationKey.of(alice.uid, bobUid)
        bob.messageRepository.ensureConversation(bobUid, alice.uid, alice.name)
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = bobUid,
            senderName = bob.name,
            content = "The school trip is on Friday"
        )
        bob.messageRepository.sendMessage(message)
        eventually("Bob's message on the server") {
            alice.firestore.collection("messages").document(message.id).get().await().takeIf { it.exists() }
        }

        EmulatorEnvironment.step("Bob deletes his account")
        val deletedAt = System.currentTimeMillis()
        AccountDeletionService(
            functions = FirebaseFunctions.getInstance(bob.app),
            database = bob.database,
            encryptedPreferences = bob.encryptedPreferences,
            fcmService = bob.fcmService
        ).deleteAccount().getOrThrow()
        awaitNotPaired(alice)

        // The thread, marked by the server, still readable by Alice with Bob's words in it.
        val thread = alice.firestore.collection("conversations").document(conversationId).get().await()
        assertTrue("the thread went with Bob's account", thread.exists())
        val retainedUntil = requireNotNull(thread.getLong(DepartedThread.RETAINED_UNTIL)) { "no deadline" }
        assertTrue("the deadline is not about thirty days out", retainedUntil >= deletedAt + RETENTION_MS - SLACK_MS)
        assertTrue("the deadline is past thirty days", retainedUntil <= System.currentTimeMillis() + RETENTION_MS)
        assertEquals(bobUid, thread.getString(DepartedThread.DEPARTED_UID))
        assertEquals(bob.name, thread.getString(DepartedThread.DEPARTED_NAME))
        val kept = alice.firestore.collection("messages")
            .whereEqualTo("conversationId", conversationId).get().await()
        assertTrue("Bob's message went with his account", kept.documents.any { it.id == message.id })

        // Found the way the chat tab and the export find it, with no pairing left to derive it from.
        val found = DepartedThreadSource(alice.userRepository, FirestoreMessageDataSource(alice.firestore))
            .current(alice.uid)
        assertEquals(listOf(DepartedThread(conversationId, bobUid, bob.name, retainedUntil)), found)

        // Closed to new messages.
        assertRefused("Alice writing into Bob's kept thread") {
            alice.firestore.collection("messages").document(UUID.randomUUID().toString()).set(
                mapOf(
                    "conversationId" to conversationId,
                    "senderId" to alice.uid,
                    "senderName" to alice.name,
                    "content" to "Are you there?",
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
        }

        // One push, the specific one, read back from the queue addressed to Alice.
        val push = EmulatorEnvironment.awaitQueuedPush(alice.uid, COPARENT_ACCOUNT_DELETED)
        assertEquals(bob.name, push["actorName"])
        assertEquals(conversationId, push["conversationId"])
        assertEquals(retainedUntil.toString(), push["retainedUntilMillis"])
        assertEquals(
            Instant.ofEpochMilli(retainedUntil).atZone(ZoneOffset.UTC).toLocalDate().toString(),
            push["date"]
        )
        val toAlice = alice.queuedFor(alice.uid).mapNotNull { (it["data"] as? Map<*, *>)?.get("type") }
        assertFalse("Alice was also sent pairing_removed: $toAlice", PAIRING_REMOVED in toAlice)
    }

    // ---- The professional's reads, before and after the second consent ----------------------

    /** A shared event, Alice's half of the plan and the pair's schedule — what a grant opens. */
    private suspend fun seedFamilyRecords(familyId: String): Event {
        val event = insertEvent(alice, "Handover at school")
        alice.parentingPlanRepository.save(
            familyId,
            alice.uid,
            ParentingPlanEntry(
                answers = mapOf(PLAN_QUESTION to PLAN_ANSWER),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        val onServer = alice.parentingPlanRepository.serverHalves(familyId)[alice.uid]
        assertEquals("Alice's half did not reach the server", PLAN_ANSWER, onServer?.answerTo(PLAN_QUESTION))
        alice.custodyRepository.createWeekOnWeekOff(LocalDate.now())
        assertTrue(
            "the schedule did not reach the server",
            alice.custodyRepository.readShared() is SharedCustodyRead.Found
        )
        return event
    }

    private suspend fun assertMediatorReadsNothing(mediator: EmulatorParent, grant: ProfessionalGrant, event: Event) {
        val (from, to) = window()
        // The repository degrades a refusal to an empty answer, as the screen must...
        assertTrue(mediator.professionalRepository.observeFamilyEvents(grant, from, to).first().isEmpty())
        assertTrue(mediator.professionalRepository.observePlan(grant).first().isEmpty())
        assertNull(mediator.professionalRepository.observeCustody(grant).first())
        // ...so the refusal itself is checked on the documents, each of which exists.
        assertRefused("an unconsented professional reading an event") {
            mediator.firestore.collection("events").document(event.id).get().await()
        }
        assertRefused("an unconsented professional reading the plan") {
            mediator.firestore.collection("parenting_plans").document(grant.familyId).get().await()
        }
        assertRefused("an unconsented professional reading the schedule") {
            mediator.firestore.collection("custody_models").document(grant.familyId).get().await()
        }
    }

    private suspend fun assertMediatorReadsTheFamily(mediator: EmulatorParent, grant: ProfessionalGrant, event: Event) {
        val (from, to) = window()
        withTimeout(EmulatorParent.WAIT_MS) {
            mediator.professionalRepository.observeFamilyEvents(grant, from, to)
                .first { list -> list.any { it.id == event.id } }
        }
        val plan = withTimeout(EmulatorParent.WAIT_MS) {
            mediator.professionalRepository.observePlan(grant).first { it.isNotEmpty() }
        }
        assertEquals(PLAN_ANSWER, plan[alice.uid]?.answerTo(PLAN_QUESTION))
        val custody = withTimeout(EmulatorParent.WAIT_MS) {
            mediator.professionalRepository.observeCustody(grant).first { it != null }
        }
        assertNotNull(custody)
    }

    private fun window(): Pair<LocalDate, LocalDate> =
        LocalDate.now().minusDays(1) to LocalDate.now().plusDays(PERIOD_DAYS)

    // ---- Shared helpers -----------------------------------------------------------------------

    /** Saves an event on [parent]'s phone the way the editor does, and waits for it to upload. */
    private suspend fun insertEvent(parent: EmulatorParent, title: String, isPrivate: Boolean = false): Event {
        val now = LocalDateTime.now()
        val event = Event(
            id = UUID.randomUUID().toString(),
            title = title,
            startDateTime = now.plusDays(2),
            eventType = "school",
            parentOwner = parent.database.userDao().getUserById(parent.uid)?.role ?: "mom",
            createdAt = now,
            updatedAt = now,
            isPrivate = isPrivate
        )
        parent.eventRepository.insertEvent(event)
        if (!isPrivate) {
            eventually("event ${event.id} to reach the server") {
                EmulatorEnvironment.documentExists("events/${event.id}").takeIf { it }
            }
        }
        return event
    }

    /**
     * The family-scoped query a calendar friend's client runs: `familyId` is what the grant is
     * keyed on, and the creator filter is what lets Firestore prove the M-6 check from the query.
     */
    private suspend fun friendsEventIds(friend: EmulatorParent, familyId: String, parents: List<String>): List<String> =
        friend.firestore.collection("events")
            .whereEqualTo("familyId", familyId)
            .whereIn("createdByFirebaseUid", parents)
            .get()
            .await()
            .documents
            .map { it.id }

    private suspend fun awaitNotPaired(parent: EmulatorParent) {
        withTimeout(EmulatorParent.WAIT_MS) {
            parent.pairingRepository.observePairingState().filterIsInstance<PairingState.NotPaired>().first()
        }
    }

    /** Runs Bob's expense listener until [expenseId] is in his Room, then stops it. */
    private suspend fun downloadExpenseOnBobsPhone(expenseId: String): Expense = coroutineScope {
        val listener = launch { bob.expenseRepository.observeRemote() }
        try {
            withTimeout(EmulatorParent.WAIT_MS) {
                bob.expenseRepository.getAllExpenses()
                    .first { list -> list.any { it.id == expenseId } }
                    .single { it.id == expenseId }
            }
        } finally {
            listener.cancel()
        }
    }

    /** A small JPEG in the app's cache, as the camera or the picker would hand over. */
    private fun receiptPhoto(): File {
        val file = File(context.cacheDir, "e2e-receipt-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(PHOTO_PX, PHOTO_PX, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        return file
    }

    private fun functionUrl(name: String): String =
        "http://${EmulatorEnvironment.requireHost()}:${EmulatorEnvironment.FUNCTIONS_PORT}" +
            "/${EmulatorEnvironment.PROJECT_ID}/$REGION/$name"

    /** A GET with no credentials, as a calendar app subscribing to a feed makes it. */
    private suspend fun httpGet(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            val status = connection.responseCode
            val stream = if (status < HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream
            status to (stream?.bufferedReader()?.readText() ?: "")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * `verifyExport` over the callable protocol with no ID token — the verification page's call.
     * The client has no wrapper for it: no signed-in phone ever needs to ask.
     */
    private suspend fun verifyExport(query: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(functionUrl("verifyExport")).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(JSONObject().put("data", query).toString().toByteArray()) }
            check(connection.responseCode == HTTP_OK) { "verifyExport answered ${connection.responseCode}" }
            JSONObject(connection.inputStream.bufferedReader().readText()).getJSONObject("result")
        } finally {
            connection.disconnect()
        }
    }

    /** Polls [probe] until it answers non-null, failing after [EmulatorParent.WAIT_MS]. */
    private suspend fun <T : Any> eventually(what: String, probe: suspend () -> T?): T {
        val deadline = System.currentTimeMillis() + EmulatorParent.WAIT_MS
        while (true) {
            probe()?.let { return it }
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            delay(POLL_MS)
        }
    }

    /** Fails unless [block] is refused by the security rules. */
    private suspend fun assertRefused(what: String, block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue(
            "$what was not refused (got $failure)",
            failure is FirebaseFirestoreException &&
                failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
    }

    private companion object {
        const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
        const val PERIOD_DAYS = 30L
        const val POLL_MS = 250L

        const val PROFESSIONAL_ACCESS_REQUESTED = "professional_access_requested"
        const val PAIRING_REMOVED = "pairing_removed"
        const val COPARENT_ACCOUNT_DELETED = "coparent_account_deleted"

        /** `DEPARTED_CHAT_RETENTION_DAYS` in `functions/index.js`. */
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

        /** Clock skew between this device and the Functions emulator the deadline check allows. */
        const val SLACK_MS = 60_000L

        /** A schedule question (`PlanScheduleLink.SCHEDULE_QUESTIONS`), so the plan has a real id. */
        const val PLAN_QUESTION = "care_weekday"
        const val PLAN_ANSWER = "Alternate weeks, handover on Monday at school"

        /** The region `onCall`/`onRequest` default to, and so the emulator's URL path. */
        const val REGION = "us-central1"
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TIMEOUT_MS = 10_000

        const val RECEIPT_AMOUNT = 12.5
        const val MAX_RECEIPT_BYTES = 5L * 1024 * 1024
        const val PHOTO_PX = 64
        const val JPEG_QUALITY = 90
        val JPEG_0: Byte = 0xFF.toByte()
        val JPEG_1: Byte = 0xD8.toByte()
    }
}
