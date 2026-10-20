package com.coparently.app.data.family

import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.CustodyModelDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.ChildInfoEntity
import com.coparently.app.data.local.entity.CustodyModelEntity
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.local.entity.ExpenseEntity
import com.coparently.app.data.local.entity.PetEntity
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.repository.ExpenseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Records without a family" (owner decision, October 2026): which of a parent's records are a
 * question, and what each answer writes.
 *
 * The DAO queries narrow by SQL, which needs a real database; the class applies the same rule
 * again, and that is what these tests hold — so the DAOs here hand back rows the queries would
 * never return, to prove they are dropped.
 */
class UnassignedRecordsTest {

    private val userDao = mockk<UserDao>(relaxed = true)
    private val eventDao = mockk<EventDao>(relaxed = true)
    private val expenseDao = mockk<ExpenseDao>(relaxed = true)
    private val childInfoDao = mockk<ChildInfoDao>(relaxed = true)
    private val petDao = mockk<PetDao>(relaxed = true)
    private val custodyModelDao = mockk<CustodyModelDao>(relaxed = true)
    private val expenseRepository = mockk<ExpenseRepository>(relaxed = true)
    private val custodyModelRepository = mockk<CustodyModelRepository>()
    private val syncRequester = mockk<SyncRequester>(relaxed = true)
    private val preferences = mockk<EncryptedPreferences>()
    private val stored = mutableMapOf<String, String>()
    private lateinit var records: UnassignedRecords

    @Before
    fun setUp() {
        every { preferences.getString(any()) } answers { stored[firstArg()] }
        every { preferences.putString(any(), any()) } answers {
            stored[firstArg()] = secondArg()
            Unit
        }
        every { eventDao.observeOwnWithoutFamily(ALICE) } returns flowOf(emptyList())
        every { expenseDao.observeOwnWithoutFamily(ALICE) } returns flowOf(emptyList())
        every { childInfoDao.observeOwnWithoutFamily(ALICE) } returns flowOf(emptyList())
        every { petDao.observeOwnWithoutFamily(ALICE) } returns flowOf(emptyList())
        every { custodyModelDao.observeActiveModels() } returns flowOf(emptyList())
        familiesAre(listOf(BOB, CAROL), onScreen = BOB)
        records = UnassignedRecords(
            userDao,
            eventDao,
            expenseDao,
            childInfoDao,
            petDao,
            custodyModelDao,
            expenseRepository,
            custodyModelRepository,
            syncRequester,
            preferences
        )
    }

    // ---- what is asked about -------------------------------------------------------------

    @Test
    fun `every kind of own record of no family is listed, children and pets first`() = runTest {
        every { eventDao.observeOwnWithoutFamily(ALICE) } returns flowOf(listOf(event("dentist")))
        every { expenseDao.observeOwnWithoutFamily(ALICE) } returns flowOf(listOf(expense("shoes")))
        every { childInfoDao.observeOwnWithoutFamily(ALICE) } returns flowOf(listOf(child("emma")))
        every { petDao.observeOwnWithoutFamily(ALICE) } returns flowOf(listOf(pet("max")))
        every { custodyModelDao.observeActiveModels() } returns flowOf(listOf(schedule("mine")))

        val snapshot = records.observe(ALICE).first()

        assertEquals(
            listOf("child:emma", "pet:max", "event:dentist", "expense:shoes", "custody:mine"),
            snapshot.records.map { it.key }
        )
        assertEquals(listOf(BOB_FAMILY, CAROL_FAMILY), snapshot.families.map { it.familyId })
        assertEquals(BOB_FAMILY, snapshot.onScreenFamilyId)
        val custody = snapshot.records.last()
        assertEquals(CustodyModelType.WEEK_ON_WEEK_OFF, custody.custodyType)
        assertEquals(LocalDate.of(2026, 8, 3), custody.date)
    }

    @Test
    fun `a co-parent's record, a stamped one, a private event and a pending deletion are not asked about`() =
        runTest {
            every { eventDao.observeOwnWithoutFamily(ALICE) } returns flowOf(
                listOf(
                    event("mine"),
                    event("blank-family", familyId = ""),
                    event("bobs", createdBy = BOB),
                    event("stamped", familyId = BOB_FAMILY),
                    event("private", isPrivate = true),
                    event("deleted", deletedAtMillis = 1L)
                )
            )
            every { expenseDao.observeOwnWithoutFamily(ALICE) } returns flowOf(
                listOf(expense("carols", createdBy = CAROL), expense("deleted", deletedAtMillis = 1L))
            )
            every { childInfoDao.observeOwnWithoutFamily(ALICE) } returns flowOf(
                listOf(child("stamped", familyId = CAROL_FAMILY), child("nobodys", createdBy = null))
            )
            every { custodyModelDao.observeActiveModels() } returns flowOf(
                listOf(schedule("bob-family", familyId = BOB_FAMILY), schedule("old", isActive = false))
            )

            val snapshot = records.observe(ALICE).first()

            assertEquals(listOf("event:mine", "event:blank-family"), snapshot.records.map { it.key })
        }

    @Test
    fun `nothing is a question while the parent is in no family`() = runTest {
        familiesAre(emptyList(), onScreen = null)
        every { eventDao.observeOwnWithoutFamily(ALICE) } returns flowOf(listOf(event("dentist")))

        val snapshot = records.observe(ALICE).first()

        assertEquals(emptyList(), snapshot.records)
        assertFalse(snapshot.askOnHome)
    }

    @Test
    fun `with one family its records are listed, but its schedule is its own and not asked about`() = runTest {
        // A schedule of no family answers for the family while there is exactly one
        // (`ActiveCustodyRow.belongsTo`); a record of no family is still "only mine" — an earlier
        // family on the phone is what kept the backfill from stamping it.
        familiesAre(listOf(BOB), onScreen = BOB)
        every { eventDao.observeOwnWithoutFamily(ALICE) } returns flowOf(listOf(event("dentist")))
        every { custodyModelDao.observeActiveModels() } returns flowOf(listOf(schedule("mine")))

        val snapshot = records.observe(ALICE).first()

        assertEquals(listOf("event:dentist"), snapshot.records.map { it.key })
        assertFalse(snapshot.askOnHome)
    }

    @Test
    fun `a record kept only mine is not asked about again`() = runTest {
        every { eventDao.observeOwnWithoutFamily(ALICE) } returns flowOf(listOf(event("dentist"), event("school")))

        records.keep(ALICE, records.observe(ALICE).first().records.first())

        assertEquals(listOf("event:school"), records.observe(ALICE).first().records.map { it.key })
        assertEquals("event:dentist", stored[KEPT_KEY])
    }

    @Test
    fun `Home asks once per set of co-parents`() = runTest {
        every { eventDao.observeOwnWithoutFamily(ALICE) } returns flowOf(listOf(event("dentist")))

        assertTrue(records.observe(ALICE).first().askOnHome)

        records.markAsked(ALICE)
        assertFalse(records.observe(ALICE).first().askOnHome)
        // Sorted, so the same two co-parents read the same whatever order the row lists them in.
        assertEquals("$BOB,$CAROL", stored[ASKED_KEY])

        // A third family is a new reason to wonder where an old record belongs.
        familiesAre(listOf(BOB, CAROL, DAVE), onScreen = BOB)
        assertTrue(records.observe(ALICE).first().askOnHome)
    }

    @Test
    fun `Home does not ask when everything has been answered`() = runTest {
        every { eventDao.observeOwnWithoutFamily(ALICE) } returns flowOf(listOf(event("dentist")))
        records.keep(ALICE, records.observe(ALICE).first().records.single())

        assertFalse(records.observe(ALICE).first().askOnHome)
    }

    // ---- what an answer writes -----------------------------------------------------------

    @Test
    fun `an event, a child and a pet are stamped and a sync carries them to the family`() = runTest {
        coEvery { eventDao.assignFamily("dentist", CAROL_FAMILY, ALICE) } returns 1
        coEvery { childInfoDao.assignFamily("emma", CAROL_FAMILY, ALICE) } returns 1
        coEvery { petDao.assignFamily("max", CAROL_FAMILY, ALICE) } returns 1

        val outcomes = listOf(
            records.assign(ALICE, record(UnassignedKind.EVENT, "dentist"), CAROL_FAMILY),
            records.assign(ALICE, record(UnassignedKind.CHILD, "emma"), CAROL_FAMILY),
            records.assign(ALICE, record(UnassignedKind.PET, "max"), CAROL_FAMILY)
        )

        assertEquals(List(3) { AssignOutcome.ASSIGNED }, outcomes)

        verify(exactly = 3) { syncRequester.requestSyncNow() }
    }

    @Test
    fun `a record that already took a family is left as it is`() = runTest {
        coEvery { eventDao.assignFamily("dentist", CAROL_FAMILY, ALICE) } returns 0

        val outcome = records.assign(ALICE, record(UnassignedKind.EVENT, "dentist"), CAROL_FAMILY)

        assertEquals(AssignOutcome.GONE, outcome)
        verify(exactly = 0) { syncRequester.requestSyncNow() }
    }

    @Test
    fun `a family the parent is not in is refused, and nothing is written`() = runTest {
        val outcome = records.assign(ALICE, record(UnassignedKind.EVENT, "dentist"), "alice-uid__eve-uid")

        assertEquals(AssignOutcome.NOT_SENT, outcome)
        coVerify(exactly = 0) { eventDao.assignFamily(any(), any(), any()) }
    }

    @Test
    fun `an expense goes back through the repository with only its family changed`() = runTest {
        val original = Expense(
            id = "shoes",
            title = "Shoes",
            amount = 40.0,
            currency = "CZK",
            category = ExpenseCategory.CLOTHING,
            paidBy = ALICE,
            splitBetween = listOf(ALICE),
            createdByFirebaseUid = ALICE,
            syncedToFirestore = true,
            splitBasisPoints = 5_000
        )
        coEvery { expenseRepository.getExpenseById("shoes") } returns original
        val written = slot<Expense>()
        coEvery { expenseRepository.updateExpense(capture(written)) } returns Unit

        val outcome = records.assign(ALICE, record(UnassignedKind.EXPENSE, "shoes"), BOB_FAMILY)

        assertEquals(AssignOutcome.ASSIGNED, outcome)
        // The split is a snapshot of the moment it was recorded and is never re-priced.
        assertEquals(original.copy(familyId = BOB_FAMILY, syncedToFirestore = false), written.captured)
    }

    @Test
    fun `an expense that already names a family is not moved`() = runTest {
        coEvery { expenseRepository.getExpenseById("shoes") } returns Expense(
            id = "shoes",
            title = "Shoes",
            amount = 40.0,
            category = ExpenseCategory.CLOTHING,
            paidBy = ALICE,
            createdByFirebaseUid = ALICE,
            familyId = CAROL_FAMILY
        )

        val outcome = records.assign(ALICE, record(UnassignedKind.EXPENSE, "shoes"), BOB_FAMILY)

        assertEquals(AssignOutcome.GONE, outcome)
        coVerify(exactly = 0) { expenseRepository.updateExpense(any()) }
    }

    @Test
    fun `a schedule goes to the family on screen, and a proposal is not asked about again`() = runTest {
        coEvery { custodyModelDao.getModelById("mine") } returns schedule("mine")
        coEvery { custodyModelRepository.submitPattern(any(), any(), any()) } returns PatternSubmission.PROPOSED

        val outcome = records.assign(ALICE, record(UnassignedKind.CUSTODY, "mine"), BOB_FAMILY)

        assertEquals(AssignOutcome.PROPOSED, outcome)
        coVerify { custodyModelRepository.submitPattern(match { it.id == "mine" }, any(), any()) }
        assertEquals("custody:mine", stored[KEPT_KEY])
    }

    @Test
    fun `a schedule never goes to a family that is not on screen`() = runTest {
        coEvery { custodyModelDao.getModelById("mine") } returns schedule("mine")

        val outcome = records.assign(ALICE, record(UnassignedKind.CUSTODY, "mine"), CAROL_FAMILY)

        assertEquals(AssignOutcome.NOT_SENT, outcome)
        coVerify(exactly = 0) { custodyModelRepository.submitPattern(any(), any(), any()) }
    }

    @Test
    fun `a schedule refused for the co-parent's waiting proposal is still asked about`() = runTest {
        coEvery { custodyModelDao.getModelById("mine") } returns schedule("mine")
        coEvery { custodyModelRepository.submitPattern(any(), any(), any()) } returns
            PatternSubmission.COPARENT_PROPOSAL_WAITING

        val outcome = records.assign(ALICE, record(UnassignedKind.CUSTODY, "mine"), BOB_FAMILY)

        assertEquals(AssignOutcome.PROPOSAL_WAITING, outcome)
        assertEquals(null, stored[KEPT_KEY])
    }

    // ---- fixtures ------------------------------------------------------------------------

    private fun familiesAre(partners: List<String>, onScreen: String?) {
        val row = UserEntity(
            id = ALICE,
            email = "alice@example.com",
            name = "Alice",
            role = "mom",
            colorCode = "#FF4081",
            partnerId = onScreen,
            partnerIdsJson = partners.joinToString(",", "[", "]") { "\"$it\"" }
        )
        every { userDao.observeUserById(ALICE) } returns flowOf(row)
        coEvery { userDao.getUserById(ALICE) } returns row
    }

    private fun record(kind: UnassignedKind, id: String) = UnassignedRecord(kind, id, title = id)

    private fun event(
        id: String,
        createdBy: String? = ALICE,
        familyId: String? = null,
        isPrivate: Boolean = false,
        deletedAtMillis: Long? = null
    ) = EventEntity(
        id = id,
        title = id,
        startDateTime = NOW,
        eventType = "other",
        parentOwner = "mom",
        createdAt = NOW,
        updatedAt = NOW,
        updatedAtMillis = 0L,
        createdByFirebaseUid = createdBy,
        isPrivate = isPrivate,
        familyId = familyId,
        deletedAtMillis = deletedAtMillis
    )

    private fun expense(id: String, createdBy: String? = ALICE, deletedAtMillis: Long? = null) = ExpenseEntity(
        id = id,
        title = id,
        amount = 40.0,
        category = ExpenseCategory.CLOTHING.name,
        paidBy = ALICE,
        date = NOW.toLocalDate(),
        createdAt = NOW,
        createdByFirebaseUid = createdBy,
        deletedAtMillis = deletedAtMillis
    )

    private fun child(id: String, createdBy: String? = ALICE, familyId: String? = null) = ChildInfoEntity(
        id = id,
        childName = id,
        dateOfBirth = null,
        medicationsJson = "[]",
        activitiesJson = "[]",
        allergiesJson = "[]",
        medicalNotes = null,
        emergencyContactsJson = "[]",
        schoolInfoJson = null,
        createdAt = NOW,
        updatedAt = NOW,
        updatedAtMillis = 0L,
        createdByFirebaseUid = createdBy,
        lastModifiedBy = createdBy,
        syncedToFirestore = true,
        familyId = familyId
    )

    private fun pet(id: String) = PetEntity(
        id = id,
        name = id,
        species = "DOG",
        breed = null,
        dateOfBirth = null,
        medicationsJson = "[]",
        vaccinationsJson = "[]",
        specialNeeds = null,
        feedingNotes = null,
        vetName = null,
        vetPhone = null,
        createdAt = NOW,
        updatedAt = NOW,
        updatedAtMillis = 0L,
        createdByFirebaseUid = ALICE,
        lastModifiedBy = ALICE,
        syncedToFirestore = true
    )

    private fun schedule(id: String, familyId: String? = null, isActive: Boolean = true) = CustodyModelEntity(
        id = id,
        modelType = "week_on_week_off",
        patternDays = 14,
        momDaysPattern = "[0,1,2,3,4,5,6]",
        startDate = "2026-08-03",
        isActive = isActive,
        createdAt = "2026-08-01T10:00:00",
        familyId = familyId
    )

    private companion object {
        const val ALICE = "alice-uid"
        const val BOB = "bob-uid"
        const val CAROL = "carol-uid"
        const val DAVE = "dave-uid"

        // Spelled out rather than built with `FamilyKey.of`: the id names stored documents.
        const val BOB_FAMILY = "alice-uid__bob-uid"
        const val CAROL_FAMILY = "alice-uid__carol-uid"

        const val KEPT_KEY = "${PreferenceKeys.UNASSIGNED_KEPT_PREFIX}$ALICE"
        const val ASKED_KEY = "${PreferenceKeys.UNASSIGNED_ASKED_PREFIX}$ALICE"

        val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 20, 9, 0)
    }
}
