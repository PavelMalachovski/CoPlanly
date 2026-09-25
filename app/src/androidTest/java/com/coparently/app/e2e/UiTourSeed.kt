package com.coparently.app.e2e

import android.net.Uri
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.DayOverrideTransition
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Activity
import com.coparently.app.domain.model.BloodType
import com.coparently.app.domain.model.Budget
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.EmergencyContact
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.SchoolInfo
import com.coparently.app.domain.model.Vaccination
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.repository.BudgetRepository
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.e2e.EmulatorEnvironment.step
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject

/**
 * The family the UI tour photographs: Alice and Bob, their children Emma and Leo, the dog Max —
 * and everything a parent would have after a few weeks of using the app, written through the same
 * repositories the screens save through.
 *
 * Alice's records go through **her app's own** repositories (this class is built by Hilt, from the
 * graph `MainActivity` runs on), so they are in her Room the moment they are saved. Bob's go through
 * his `EmulatorParent` and reach Alice the way they would reach a phone: the custody document through
 * her mirror, his events through `SyncService.performFullSync()`, his messages through `ChatMirror`,
 * his offers and requests through their listeners.
 *
 * Every step is independent and best effort ([attempt]): one that the emulators refuse is logged and
 * the rest still seed, because a tour with one card missing is worth far more than no tour.
 */
@Suppress("LongParameterList") // one repository per kind of record the tour shows
class UiTourSeed @Inject constructor(
    private val events: EventRepository,
    private val expenses: ExpenseRepository,
    private val budgets: BudgetRepository,
    private val children: ChildInfoRepository,
    private val pets: PetRepository,
    private val messages: MessageRepository,
    private val custody: CustodyModelRepository,
    private val splits: FamilySettingsRepository,
    private val plans: ParentingPlanRepository
) {

    /** Who is who, for the whole seed: the two uids, their slots, the family id, today, and the words. */
    private class Family(
        val alice: String,
        val bob: EmulatorParent,
        val aliceSlot: String,
        val bobSlot: String,
        val content: UiTourContent
    ) {
        val id: String = FamilyKey.of(alice, bob.uid)
        val today: LocalDate = LocalDate.now()
    }

    /**
     * Seeds everything that is already settled — custody, children, pet, events, money, the plan,
     * the chat and a document. What waits on Alice (Bob's swap and change request) is [seedPending],
     * so the tour can photograph Home before and after it arrives if it wants to. What the family
     * wrote is [content], in the variant's language ([UiTourContent.forLanguage]).
     */
    suspend fun seedSettled(aliceUid: String, bob: EmulatorParent, content: UiTourContent) {
        val family = family(aliceUid, bob, content)
        attempt("custody") { seedCustody(family) }
        attempt("children and pet") { seedChildrenAndPet(family) }
        attempt("events") { seedEvents(family) }
        attempt("money") { seedMoney(family) }
        attempt("parenting plan") { seedPlan(family) }
        attempt("chat") { seedChat(family) }
        attempt("document") { seedDocument(family) }
    }

    /** Bob's day-swap offer and his change request on Alice's dentist appointment. */
    suspend fun seedPending(aliceUid: String, bob: EmulatorParent, content: UiTourContent) {
        val family = family(aliceUid, bob, content)
        attempt("swap") {
            val agreed = checkNotNull(bob.custodyRepository.getActiveModelSync()) { "Bob has no pattern" }
            val date = family.today.plusDays(SWAP_OFFSET).toString()
            val toSlot = if (agreed.getCustodyFor(LocalDate.parse(date)) == family.aliceSlot) {
                family.bobSlot
            } else {
                family.aliceSlot
            }
            bob.custodyRepository.applyDayOverrides(date) { current ->
                DayOverrideTransition.offer(current, date, toSlot, bob.uid, LocalDateTime.now().toString())
            }.getOrThrow()
        }
        attempt("change request") {
            val dentist = events.getAllEvents().first().first { it.title == content.events.dentist }
            bob.changeRequestRepository.createChangeRequest(
                ChangeRequest(
                    id = UUID.randomUUID().toString(),
                    eventId = dentist.id,
                    eventTitle = dentist.title,
                    requestedBy = bob.uid,
                    requestedTo = aliceUid,
                    currentStartDateTime = dentist.startDateTime,
                    currentEndDateTime = dentist.endDateTime,
                    proposedStartDateTime = dentist.startDateTime.plusDays(1),
                    proposedEndDateTime = dentist.endDateTime?.plusDays(1),
                    note = content.changeRequestNote,
                    createdAt = LocalDateTime.now(),
                    familyId = family.id
                )
            )
        }
    }

    // ---- custody --------------------------------------------------------------------------------

    /**
     * Week on, week off from this Monday, Alice first, with an afternoon each way and an autumn
     * break with Bob — set on Bob's phone as the pair's first pattern (nothing to propose against),
     * and waited for in Alice's Room, where her calendar reads it.
     */
    private suspend fun seedCustody(family: Family) {
        val monday = family.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val base = CustodyModel.weekOnWeekOff(
            id = UUID.randomUUID().toString(),
            startDate = monday,
            momFirst = family.aliceSlot == ContactWindow.SLOT_ONE
        )
        val autumn = family.today.plusDays(LAYER_START_OFFSET)
        val model = base.copy(
            contactWindows = listOf(
                ContactWindow(WEDNESDAY, LocalTime.of(15, 0), LocalTime.of(19, 0), family.bobSlot),
                ContactWindow(WEDNESDAY + WEEK, LocalTime.of(16, 0), LocalTime.of(18, 30), family.aliceSlot)
            ),
            seasonalLayers = listOf(
                SeasonalLayer.allWith(
                    LAYER_ID,
                    family.content.layerName,
                    autumn..autumn.plusDays(LAYER_DAYS),
                    family.bobSlot
                )
            )
        )
        family.bob.custodyRepository.submitPattern(model)
        withTimeout(WAIT_MS) {
            custody.getActiveModel().first { it != null && it.startDate == monday }
        }
    }

    // ---- children and pet -----------------------------------------------------------------------

    private suspend fun seedChildrenAndPet(family: Family) {
        val emma = family.content.emma
        children.upsertChildInfo(
            child(family, EMMA, LocalDateTime.of(2016, 4, 12, 0, 0)).copy(
                allergies = emma.allergies,
                medications = listOf(Medication(emma.medication, emma.dose, emma.medicationWhen)),
                activities = listOf(
                    Activity(emma.activity, emma.activitySchedule, "ZUŠ Vinohrady", emma.activityTeacher)
                ),
                schoolInfo = SchoolInfo("ZŠ Náměstí Míru", teacherName = "Mgr. Nováková", grade = "4"),
                medicalNotes = emma.medicalNotes,
                medicalProfile = MedicalProfile(
                    bloodType = BloodType.A_POSITIVE,
                    intolerances = listOf(emma.intolerance),
                    vaccinations = listOf(Vaccination("MMR", LocalDate.of(2017, 5, 3)))
                ),
                emergencyContacts = listOf(EmergencyContact("Jana Svobodová", emma.grandmother, "+420 602 123 456"))
            )
        )
        val leo = family.content.leo
        children.upsertChildInfo(
            child(family, LEO, LocalDateTime.of(2020, 9, 30, 0, 0)).copy(
                activities = listOf(Activity(leo.activity, leo.activitySchedule, leo.activityPlace)),
                schoolInfo = SchoolInfo("MŠ Korunní", teacherName = leo.teacher, grade = leo.grade)
            )
        )
        val pet = family.content.pet
        val now = LocalDateTime.now()
        pets.upsertPet(
            Pet(
                id = UUID.randomUUID().toString(),
                name = MAX,
                species = PetSpecies.DOG,
                breed = pet.breed,
                vaccinations = listOf(Vaccination(pet.vaccination, family.today.minusMonths(PET_VACCINE_MONTHS))),
                feedingNotes = pet.feedingNotes,
                vetName = "Veterinární klinika Vinohrady",
                vetPhone = "+420 222 555 111",
                createdAt = now,
                updatedAt = now,
                createdByFirebaseUid = family.alice,
                lastModifiedBy = family.alice,
                familyId = family.id
            )
        )
    }

    private fun child(family: Family, name: String, born: LocalDateTime): ChildInfo {
        val now = LocalDateTime.now()
        return ChildInfo(
            id = UUID.randomUUID().toString(),
            childName = name,
            dateOfBirth = born,
            createdAt = now,
            updatedAt = now,
            createdByFirebaseUid = family.alice,
            lastModifiedBy = family.alice,
            familyId = family.id
        )
    }

    // ---- events ---------------------------------------------------------------------------------

    private suspend fun seedEvents(family: Family) {
        val emma = memberNamed(EMMA)
        val leo = memberNamed(LEO)
        val titles = family.content.events
        fun day(offset: Long, hour: Int, minute: Int = 0) = family.today.plusDays(offset).atTime(hour, minute)
        val alices = listOf(
            event(family, titles.schoolPickup, day(0, 15, 30), minutes = 30, type = "school", members = emma),
            event(family, titles.dentist, day(1, 10), minutes = 45, type = "medical", members = leo)
                .copy(description = titles.dentistNote, isImportant = true),
            event(family, titles.swimming, day(2, 17), minutes = 60, type = "sports", members = emma).copy(
                isRecurring = true,
                recurrencePattern = "weekly",
                recurrenceEndDate = family.today.plusDays(RECURRENCE_DAYS)
            ),
            event(family, titles.therapy, day(4, 19), minutes = 50, type = "general").copy(isPrivate = true),
            event(family, titles.grandmaBirthday, day(5, 0), minutes = ALL_DAY_MINUTES, type = "birthday"),
            event(family, titles.schoolTrip, day(8, 8), TRIP_MINUTES, type = "school", members = emma),
            event(family, titles.vaccination, day(12, 9, 30), minutes = 30, type = "medical", members = leo),
            event(family, titles.pianoRecital, day(15, 18), minutes = 90, type = "school", members = emma),
            event(family, titles.parentsEvening, day(-2, 18), minutes = 60, type = "school")
        )
        alices.forEach { events.insertEvent(it) }
        val bobs = listOf(
            event(family, titles.parentTeacherMeeting, day(3, 17, 30), minutes = 45, type = "school", byBob = true),
            event(family, titles.footballTraining, day(6, 17), 90, type = "sports", members = leo, byBob = true)
                .copy(isRecurring = true, recurrencePattern = "weekly"),
            event(family, titles.handover, day(7, 15), minutes = 15, type = "general", byBob = true),
            event(family, titles.birthdayParty, day(25, 14), minutes = 180, type = "birthday", byBob = true)
        )
        bobs.forEach { family.bob.eventRepository.insertEvent(it) }
    }

    @Suppress("LongParameterList") // a small builder; named arguments at every call site
    private fun event(
        family: Family,
        title: String,
        start: LocalDateTime,
        minutes: Long,
        type: String,
        members: List<FamilyMemberRef> = emptyList(),
        byBob: Boolean = false
    ): Event {
        val now = LocalDateTime.now()
        return Event(
            id = UUID.randomUUID().toString(),
            title = title,
            startDateTime = start,
            endDateTime = start.plusMinutes(minutes),
            eventType = type,
            parentOwner = if (byBob) family.bobSlot else family.aliceSlot,
            createdAt = now,
            updatedAt = now,
            createdByFirebaseUid = if (byBob) family.bob.uid else family.alice,
            forMembers = members,
            familyId = family.id
        )
    }

    private suspend fun memberNamed(name: String): List<FamilyMemberRef> =
        children.getAllChildInfo().first().filter { it.childName == name }.map { FamilyMemberRef.Child(it.id) }

    // ---- money ----------------------------------------------------------------------------------

    /** A 60/40 split agreed first, then a month of expenses in crowns and euros, and two budgets. */
    private suspend fun seedMoney(family: Family) {
        splits.submitRatio(SplitRatio(SPLIT_BASIS_POINTS)).getOrThrow()
        val both = listOf(family.alice, family.bob.uid)
        fun expense(title: String, amount: Double, currency: String, category: ExpenseCategory, daysAgo: Long) =
            Expense(
                id = UUID.randomUUID().toString(),
                title = title,
                amount = amount,
                currency = currency,
                category = category,
                paidBy = family.alice,
                splitBetween = both,
                date = family.today.minusDays(daysAgo),
                createdByFirebaseUid = family.alice,
                splitBasisPoints = SPLIT_BASIS_POINTS,
                familyId = family.id
            )
        val titles = family.content.expenses
        listOf(
            expense(titles.jacket, 1_890.0, "CZK", ExpenseCategory.CLOTHING, 1),
            expense(titles.piano, 1_200.0, "CZK", ExpenseCategory.ACTIVITIES, 3),
            expense(titles.dentist, 450.0, "CZK", ExpenseCategory.MEDICAL, 5),
            expense(titles.trip, 85.0, "EUR", ExpenseCategory.EDUCATION, 2),
            expense(titles.skis, 60.0, "EUR", ExpenseCategory.ACTIVITIES, 0)
        ).forEach { expenses.addExpense(it) }
        budgets.addBudget(budget(ExpenseCategory.ACTIVITIES, 3_000.0, "CZK"))
        budgets.addBudget(budget(ExpenseCategory.EDUCATION, 100.0, "EUR"))
    }

    private fun budget(category: ExpenseCategory, limit: Double, currency: String) = Budget(
        id = UUID.randomUUID().toString(),
        category = category,
        monthlyLimit = limit,
        currency = currency
    )

    // ---- the plan, the chat, a document ---------------------------------------------------------

    /** Both halves of two schedule questions, one agreed word for word, one still apart. */
    private suspend fun seedPlan(family: Family) {
        val now = System.currentTimeMillis()
        val plan = family.content.plan
        plans.save(
            family.id,
            family.alice,
            ParentingPlanEntry(
                answers = mapOf(CARE_WEEKDAY to plan.weekdayAnswer, HOLIDAYS_SCHOOL to plan.aliceHolidays),
                agreedTo = mapOf(CARE_WEEKDAY to plan.weekdayAnswer),
                updatedAtMillis = now
            )
        )
        family.bob.parentingPlanRepository.save(
            family.id,
            family.bob.uid,
            ParentingPlanEntry(
                answers = mapOf(CARE_WEEKDAY to plan.weekdayAnswer, HOLIDAYS_SCHOOL to plan.bobHolidays),
                agreedTo = mapOf(CARE_WEEKDAY to plan.weekdayAnswer),
                updatedAtMillis = now
            )
        )
    }

    /** Eight messages between the two, the last of Bob's carrying a PDF. */
    private suspend fun seedChat(family: Family) {
        val thread = ConversationKey.of(family.alice, family.bob.uid)
        val chat = family.content.chat
        chat.lines.forEach { (fromAlice, text) ->
            val message = Message(
                id = UUID.randomUUID().toString(),
                conversationId = thread,
                senderId = if (fromAlice) family.alice else family.bob.uid,
                senderName = if (fromAlice) ALICE else family.bob.name,
                content = text
            )
            if (fromAlice) messages.sendMessage(message) else family.bob.messageRepository.sendMessage(message)
        }
        val messageId = UUID.randomUUID().toString()
        val attachment = family.bob.attachmentOutbox.stage(
            thread,
            messageId,
            fileUri(chat.attachmentFileName, "Trip consent form")
        )
        family.bob.messageRepository.sendMessage(
            Message(
                id = messageId,
                conversationId = thread,
                senderId = family.bob.uid,
                senderName = family.bob.name,
                content = chat.attachmentCaption,
                attachments = listOf(ChatAttachmentCodec.encode(attachment))
            )
        )
    }

    private suspend fun seedDocument(family: Family) {
        val document = family.content.document
        val file = fileUri(document.fileName, "Order")
        family.bob.documentRepository
            .add(family.id, document.title, DocumentCategory.COURT_ORDER, file)
            .getOrThrow()
    }

    // ---- plumbing -------------------------------------------------------------------------------

    /** The two slots as the server assigned them at pairing — see `OnScreenAgreementsTest.slots`. */
    private suspend fun family(aliceUid: String, bob: EmulatorParent, content: UiTourContent): Family {
        val aliceSlot = checkNotNull(bob.userRepository.getRemoteUserProfile(aliceUid)).role
        val bobSlot = checkNotNull(bob.userRepository.getRemoteUserProfile(bob.uid)).role
        return Family(aliceUid, bob, aliceSlot, bobSlot, content)
    }

    /** A small, well-formed PDF in the app's cache, as a picker's `file://` URI. */
    private fun fileUri(fileName: String, text: String): String {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val dir = File(cache, "ui-tour-files/${UUID.randomUUID()}").apply { mkdirs() }
        val bytes = buildString {
            append("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n")
            append("2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\n% $text\n")
            append("trailer<</Root 1 0 R>>\n%%EOF\n")
        }.toByteArray()
        return Uri.fromFile(File(dir, fileName).apply { writeBytes(bytes) }).toString()
    }

    @Suppress("TooGenericExceptionCaught") // one refused step must not cost the tour its other screens
    private suspend fun attempt(what: String, block: suspend () -> Unit) {
        step("tour seed: $what")
        try {
            block()
        } catch (e: Exception) {
            Log.w("UiTour", "seeding $what failed", e)
        } catch (e: AssertionError) {
            Log.w("UiTour", "seeding $what failed", e)
        }
    }

    /** Names the tour's screens look for. */
    companion object {
        const val ALICE = "Alice"
        const val EMMA = "Emma"
        const val LEO = "Leo"
        const val MAX = "Max"

        private const val LAYER_ID = "autumn-break"
        private const val LAYER_START_OFFSET = 21L
        private const val LAYER_DAYS = 6L
        private const val SWAP_OFFSET = 9L
        private const val RECURRENCE_DAYS = 56L
        private const val WEDNESDAY = 2
        private const val WEEK = 7
        private const val WAIT_MS = 45_000L
        private const val PET_VACCINE_MONTHS = 4L
        private const val ALL_DAY_MINUTES = 23L * 60 + 59
        private const val TRIP_MINUTES = 2L * 24 * 60 + 10 * 60
        private const val SPLIT_BASIS_POINTS = 6_000
        private const val CARE_WEEKDAY = "care_weekday"
        private const val HOLIDAYS_SCHOOL = "holidays_school"
    }
}
