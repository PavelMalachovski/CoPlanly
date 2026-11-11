@file:OptIn(ExperimentalUuidApi::class, InternalSharedApi::class)

package com.coparently.app.gate

import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.remote.firebase.AuthEmulator
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.data.remote.firebase.runOnMainRunLoop
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.data.repository.SwapOfferer
import com.coparently.app.data.sync.EventDocument
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.custody.CustodyDecisionOutcome
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideTransition
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.SchoolInfo
import com.coparently.app.domain.model.Vaccination
import com.coparently.app.domain.time.IsoText
import com.coparently.app.domain.time.wallClockNow
import com.coparently.app.domain.versions.EventVersionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The iPhone's write gate (MON-24 phase 3, Task 7): until it is green the iPhone writes nothing to
 * the real project.
 *
 * Two parents, each on an iPhone data layer of their own ([GatePhone]: a named GitLive app on the
 * emulators, its own Room database, the production repositories), pair through the real
 * `acceptPairingInvitation` callable. Alice's production repositories then write, in the pair's
 * family, a shared event, an important event (and its `event_important` push), an expense, a child
 * with a medical profile, a pet, a chat message, a change request, a custody schedule with a day
 * swap answered on Bob's phone and two proposals (one declined), and the swap's request deadline.
 * After each write Bob's production reader reads it back, and the test holds the domain value Bob
 * reads to the one Alice's phone holds — timestamps aside where the wire keeps only seconds.
 *
 * Every write either phone's production code makes passes through [RecordingFirestoreClient]: a
 * write the real `firestore.rules` refuse fails the test, and every document written is listed in
 * the file `COPLANLY_GATE_OUTPUT` names (`build/ios-gate/written.txt` in CI), which
 * `tools/ios/check-ios-writes.js` then compares, key for key and type for type, with what Android
 * writes. The two halves together are the gate.
 *
 * It runs where [AuthEmulator]'s tests run, under the same rule: without an emulator host it skips
 * itself, and fails instead under `COPLANLY_REQUIRE_EMULATORS=1`, where the output file is required
 * too.
 */
class IosWriteGateTest {

    @Test
    fun `every record the iPhone writes reaches the co-parent in the shape Android writes`() {
        val host = AuthEmulator.host
        if (host == null) {
            if (AuthEmulator.required) {
                fail("COPLANLY_REQUIRE_EMULATORS=1 but no COPLANLY_EMULATOR_HOST reached the test")
            }
            println("${AuthEmulator.SKIPPED} write gate")
            return
        }
        val output = AuthEmulator.variable(OUTPUT_VARIABLE)
        if (output == null && AuthEmulator.required) fail("COPLANLY_REQUIRE_EMULATORS=1 but no $OUTPUT_VARIABLE")
        val written = WrittenDocuments()
        val problems = mutableListOf<String>()
        try {
            runOnMainRunLoop(GATE_TIMEOUT) {
                val alice = GatePhone.create("Alice", "gate_alice", host, written)
                val bob = GatePhone.create("Bob", "gate_bob", host, written)
                try {
                    GateScenario(alice, bob, written, problems).play()
                } finally {
                    alice.close()
                    bob.close()
                }
            }
        } finally {
            output?.let(written::writeTo)
            println("Write gate: ${written.paths.size} documents written, listed in $output")
        }
        written.refused.forEach { problems += "the server refused a write to ${it.path}: ${it.failure}" }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n\n", prefix = "The write gate failed:\n\n"))
        println("${AuthEmulator.RAN} write gate")
    }

    private companion object {
        /** Names the file the written documents are listed in. */
        const val OUTPUT_VARIABLE = "COPLANLY_GATE_OUTPUT"

        /** The whole scenario: two sign-ups, a pairing and a dozen writes, each read back. */
        val GATE_TIMEOUT = 10.minutes
    }
}

/**
 * What the gate does, step by step, on [alice]'s and [bob]'s phones; each mismatch Bob reads goes
 * into [problems], so one run reports every step rather than the first that failed.
 */
private class GateScenario(
    private val alice: GatePhone,
    private val bob: GatePhone,
    private val written: WrittenDocuments,
    private val problems: MutableList<String>
) {
    /** The child every record is tagged with, saved by [child]. */
    private val childId = Uuid.random().toString()

    /** The shared event, which the change request is about. */
    private var sharedEvent: Event? = null

    /** Pairs the two phones, then writes and reads back each kind of record. */
    suspend fun play() {
        pair()
        step("events") { events() }
        step("expense") { expense() }
        step("child") { child() }
        step("pet") { pet() }
        step("message") { message() }
        step("change request") { changeRequest() }
        step("custody") { custody() }
    }

    /** Alice's code redeemed on Bob's phone; both phones see the pairing. Nothing runs without it. */
    private suspend fun pair() {
        val invite = alice.pairingRepository.createOrReuseInviteCode().getOrThrow()
        bob.pairingRepository.redeem(invite.code).getOrThrow()
        bob.awaitPairedWith(alice.uid)
        alice.awaitPairedWith(bob.uid)
    }

    private suspend fun events() {
        val shared = newEvent("Dentist", important = false)
        alice.eventRepository.insertEvent(shared)
        compareEvent(shared.id)
        sharedEvent = shared

        val important = newEvent("School play", important = true)
        alice.eventRepository.insertEvent(important)
        compareEvent(important.id)
        revisions(listOf(shared.id, important.id))
        val toBob = written.pushes.filter { it.data["targetUserId"] == bob.uid }
            .map { (it.data["data"] as? Map<*, *>)?.get(PushPayload.TYPE) }
        expectThat(PushPayload.EVENT_IMPORTANT in toBob) { "no event_important push was queued for Bob: $toBob" }
    }

    /** Bob's read of event [id], through `EventDocument.toEntity`, against Alice's row. */
    private suspend fun compareEvent(id: String) {
        val document = checkNotNull(bob.eventDataSource.getEventById(id)) { "Bob cannot read event $id" }
        same("event $id", alicesEvent(id), EventDocument.toEntity(document).withoutClock())
    }

    /**
     * Each event's revision, uploaded as a sync would (a save uploads it in the background), then
     * read by Bob as the export reads revisions; its event is the document the save wrote.
     */
    private suspend fun revisions(eventIds: List<String>) {
        alice.eventVersionRecorder.flush(alice.uid)
        val undelivered = alice.eventVersionRecorder.undelivered(alice.uid)
        expectThat(undelivered.isEmpty()) { "revisions stayed in Alice's outbox: $undelivered" }
        val bobs = bob.eventVersionDataSource.readableBy(bob.uid).filter { !it.recordedByServer }
        for (id in eventIds) {
            val revision = bobs.singleOrNull { it.eventId == id }
            if (revision == null) {
                problems += "Bob reads no single revision of event $id: $bobs"
                continue
            }
            expectThat(revision.editorUid == alice.uid && revision.kind == EventVersionKind.CREATED) {
                "Bob read $revision"
            }
            same("revision of event $id", alicesEvent(id), EventDocument.toEntity(revision.snapshot).withoutClock())
        }
    }

    /** Alice's row of event [id], timestamps aside. */
    private suspend fun alicesEvent(id: String): EventEntity =
        checkNotNull(alice.database.eventDao().getEventById(id)) { "Alice has no event $id" }.withoutClock()

    /** The row with its update time aside: the wire keeps it to the second, as UTC text. */
    private fun EventEntity.withoutClock() = copy(updatedAt = NO_TIME, updatedAtMillis = 0)

    private suspend fun newEvent(title: String, important: Boolean): Event {
        val now = nowToTheSecond()
        val start = now.date.plus(1, DateTimeUnit.DAY).atTime(EVENT_HOUR, 0)
        return Event(
            id = Uuid.random().toString(),
            title = title,
            description = "Bring the card",
            startDateTime = start,
            endDateTime = start.date.atTime(EVENT_HOUR + 1, 0),
            eventType = "medical",
            parentOwner = alice.slotOf(),
            createdAt = now,
            updatedAt = now,
            lastModifiedBy = alice.uid,
            reminderMinutes = REMINDER_MINUTES,
            isImportant = important,
            forMembers = listOf(FamilyMemberRef.Child(childId))
        )
    }

    private suspend fun expense() {
        val expense = Expense(
            id = Uuid.random().toString(),
            forMembers = listOf(FamilyMemberRef.Child(childId)),
            title = "Swimming course",
            // Whole, so a double the iPhone encoded as an integer would show.
            amount = 1250.0,
            currency = "CZK",
            category = ExpenseCategory.ACTIVITIES,
            paidBy = alice.uid,
            splitBetween = listOf(alice.uid, bob.uid),
            date = nowToTheSecond().date,
            notes = "Spring term",
            createdAt = nowToTheSecond()
        )
        alice.expenseRepository.addExpense(expense)
        val alices = alice.expenseRepository.getAllExpenses().first().single { it.id == expense.id }
        val bobs = coroutineScope {
            val listener = launch { bob.expenseRepository.observeRemote() }
            try {
                withTimeout(GatePhone.WAIT_MS) {
                    bob.expenseRepository.getAllExpenses().first { list -> list.any { it.id == expense.id } }
                }.single { it.id == expense.id }
            } finally {
                listener.cancel()
            }
        }
        same("expense", alices.copy(syncedToFirestore = false), bobs.copy(syncedToFirestore = false))
    }

    private suspend fun child() {
        val now = nowToTheSecond()
        val child = ChildInfo(
            id = childId,
            childName = "Anya",
            dateOfBirth = LocalDateTime(BIRTH_YEAR, 4, 2, 0, 0),
            medications = listOf(Medication(name = "Ventolin", dosage = "2 puffs", frequency = "as needed")),
            allergies = listOf("peanuts"),
            medicalNotes = "Asthma",
            schoolInfo = SchoolInfo(name = "ZS Vinohrady", grade = "2"),
            medicalProfile = MedicalProfile(intolerances = listOf("lactose")),
            createdAt = now,
            updatedAt = now,
            createdByFirebaseUid = alice.uid,
            lastModifiedBy = alice.uid
        )
        alice.childInfoRepository.upsertChildInfo(child)
        bob.childInfoRepository.pullOnce()
        val bobs = checkNotNull(bob.childInfoRepository.getChildInfoById(childId)) { "Bob's pull brought no child" }
        val alices = checkNotNull(alice.childInfoRepository.getChildInfoById(childId))
        same("child", alices.copy(syncedToFirestore = false), bobs.copy(syncedToFirestore = false))
    }

    private suspend fun pet() {
        val now = nowToTheSecond()
        val pet = Pet(
            id = Uuid.random().toString(),
            name = "Rex",
            species = PetSpecies.DOG,
            breed = "Beagle",
            dateOfBirth = LocalDateTime(PET_BIRTH_YEAR, 3, 14, 0, 0),
            medications = listOf(Medication(name = "Wormer", dosage = "1 tablet", frequency = "quarterly")),
            vaccinations = listOf(Vaccination(name = "Rabies", date = LocalDate(VACCINATION_YEAR, 2, 1))),
            vetName = "Dr. Novak",
            createdAt = now,
            updatedAt = now,
            createdByFirebaseUid = alice.uid,
            lastModifiedBy = alice.uid
        )
        alice.petRepository.upsertPet(pet)
        bob.petRepository.pullOnce()
        val bobs = checkNotNull(bob.petRepository.getPetById(pet.id)) { "Bob's pull brought no pet" }
        val alices = checkNotNull(alice.petRepository.getPetById(pet.id))
        same("pet", alices.copy(syncedToFirestore = false), bobs.copy(syncedToFirestore = false))
    }

    private suspend fun message() {
        val conversationId = ConversationKey.of(alice.uid, bob.uid)
        val message = Message(
            id = Uuid.random().toString(),
            conversationId = conversationId,
            senderId = alice.uid,
            senderName = alice.name,
            content = "Pick-up moved to 17:30"
        )
        alice.messageRepository.sendMessage(message)
        val bobs = withTimeout(GatePhone.WAIT_MS) {
            bob.messageRepository.observeMessages(conversationId).first { list -> list.any { it.id == message.id } }
        }.single { it.id == message.id }
        val alices = alice.messageRepository.observeMessages(conversationId).first().single { it.id == message.id }
        same("message", alices.asRead(), bobs.asRead())
        bob.messageRepository.markDelivered(conversationId, bob.uid)
        bob.messageRepository.markRead(conversationId, bob.uid)
    }

    private fun Message.asRead() = copy(syncedToFirestore = false, status = MessageSendStatus.SENT)

    private suspend fun changeRequest() {
        val event = checkNotNull(sharedEvent) { "no shared event to ask about" }
        val request = ChangeRequest(
            id = Uuid.random().toString(),
            eventId = event.id,
            eventTitle = event.title,
            requestedBy = alice.uid,
            requestedTo = bob.uid,
            currentStartDateTime = event.startDateTime,
            currentEndDateTime = event.endDateTime,
            proposedStartDateTime = event.startDateTime.date.plus(1, DateTimeUnit.DAY).atTime(EVENT_HOUR, 0),
            proposedEndDateTime = event.startDateTime.date.plus(1, DateTimeUnit.DAY).atTime(EVENT_HOUR + 1, 0),
            note = "Can we move it a day?",
            createdAt = nowToTheSecond()
        )
        alice.changeRequestRepository.createChangeRequest(request)
        val bobs = checkNotNull(bob.changeRequestRepository.fetchFromServer(request.id)) { "Bob cannot read it" }
        val alices = checkNotNull(alice.changeRequestRepository.getChangeRequestById(request.id))
        same("change request", alices.copy(syncedToFirestore = false), bobs.copy(syncedToFirestore = false))
    }

    private suspend fun custody() {
        val today = nowToTheSecond().date
        val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        val first = alice.custodyRepository.createWeekOnWeekOff(monday, momFirst = true)
        expectThat(first == PatternSubmission.ACTIVATED) { "the first schedule was $first, not activated" }
        val model = checkNotNull(alice.custodyRepository.getActiveModelSync()) { "Alice has no schedule" }
        same("custody schedule", model, awaitShared(bob) { it.model.id == model.id }.model)

        // Well beyond the default three-day deadline, which the day caps (as on Android's e2e).
        val swapDay = today.plus(SWAP_DAYS_AHEAD, DateTimeUnit.DAY)
        daySwap(model.getCustodyFor(swapDay), swapDay)

        val proposed = alice.custodyRepository.createTwoTwoThree(monday, momStartsFirst = true)
        expectThat(proposed == PatternSubmission.PROPOSED) { "the first proposal was $proposed, not sent" }
        awaitShared(bob) { it.proposal?.proposedBy == alice.uid }
        bob.custodyRepository.declineProposal(note = "Not in June").getOrThrow()
        awaitShared(alice) { it.proposal == null && it.lastDecision?.outcome == CustodyDecisionOutcome.DECLINED }
        val again = alice.custodyRepository.createWeekOnWeekOff(monday, momFirst = false)
        expectThat(again == PatternSubmission.PROPOSED) { "the second proposal was $again, not sent" }
        val bobs = awaitShared(bob) { it.proposal?.proposedBy == alice.uid && it.lastDecision != null }
        val alices = awaitShared(alice) { it.proposal != null && it.lastDecision != null }
        same("custody document", alices, bobs)
        expectThat(bobs.proposal?.model?.modelType == CustodyModelType.WEEK_ON_WEEK_OFF) { "Bob read ${bobs.proposal}" }
    }

    /** Alice offers [date], held by [holder], with the default deadline; Bob accepts it. */
    private suspend fun daySwap(holder: String, date: LocalDate) {
        val day = date.toString()
        val toSlot = if (holder == "mom") "dad" else "mom"
        val groupId = Uuid.random().toString()
        val offeredAt = IsoText.dateTimeShortest(wallClockNow())
        alice.custodyRepository.applyDayOverrides(
            date = day,
            transform = { DayOverrideTransition.offer(it, day, toSlot, alice.uid, offeredAt, groupId = groupId) },
            answerBy = AnswerByPick.DEFAULT,
            offerer = SwapOfferer(alice.slotOf(), alice.name)
        ).getOrThrow()
        awaitOverride(bob, day) { it.isPending }
        val answeredAt = IsoText.dateTimeShortest(wallClockNow())
        bob.custodyRepository.applyDayOverridesForDates(
            dates = listOf(day),
            transform = { current, at ->
                DayOverrideTransition.decideGroup(current, listOf(at), bob.uid, answeredAt, accept = true)
            }
        ).getOrThrow()
        val bobs = awaitOverride(bob, day) { it.isAccepted }
        same("day swap", awaitOverride(alice, day) { it.isAccepted }, bobs)
        expectThat(bobs.requestedBy == alice.uid && bobs.decidedBy == bob.uid && bobs.groupId == groupId) {
            "Bob read $bobs"
        }

        val clockId = RequestDeadlineId.of(RequestKind.SWAP, alice.uid, groupId, FamilyKey.of(alice.uid, bob.uid))
        val bobsClock = checkNotNull(bob.requestDeadlineRepository.get(clockId)) { "Bob cannot read the clock" }
        same("request deadline", alice.requestDeadlineRepository.get(clockId), bobsClock)
        expectThat(bobsClock.responderUid == bob.uid && bobsClock.firstDate == date) { "Bob read $bobsClock" }
    }

    private suspend fun awaitShared(phone: GatePhone, condition: (SharedCustody) -> Boolean): SharedCustody =
        withTimeout(GatePhone.WAIT_MS) {
            checkNotNull(phone.custodyRepository.observeShared().first { it != null && condition(it) })
        }

    private suspend fun awaitOverride(phone: GatePhone, day: String, condition: (DayOverride) -> Boolean): DayOverride =
        withTimeout(GatePhone.WAIT_MS) {
            phone.custodyRepository.observeDayOverrides().first { it[day]?.let(condition) == true }.getValue(day)
        }

    /** This parent's slot, from the pairing's Room row or, failing that, the server's profile. */
    private suspend fun GatePhone.slotOf(): String = database.userDao().getUserById(uid)?.role
        ?: checkNotNull(userRepository.getRemoteUserProfile(uid)?.role) { "$name has no slot" }

    /** Runs [block], keeping what it threw as a problem so the next step still runs. */
    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            problems += "$name: timed out: $e"
        } catch (e: Exception) {
            problems += "$name: ${e.stackTraceToString()}"
        }
    }

    private fun same(what: String, expected: Any?, actual: Any?) {
        if (expected != actual) problems += "$what: Bob read\n  $actual\nwhere Alice's phone holds\n  $expected"
    }

    private fun expectThat(condition: Boolean, problem: () -> String) {
        if (!condition) problems += problem()
    }

    private fun nowToTheSecond(): LocalDateTime = wallClockNow().let {
        LocalDateTime(it.date, LocalTime(it.hour, it.minute, it.second))
    }

    private companion object {
        val NO_TIME = LocalDateTime(2000, 1, 1, 0, 0)
        const val EVENT_HOUR = 10
        const val SWAP_DAYS_AHEAD = 10
        const val REMINDER_MINUTES = 30
        const val BIRTH_YEAR = 2019
        const val PET_BIRTH_YEAR = 2021
        const val VACCINATION_YEAR = 2026
    }
}
