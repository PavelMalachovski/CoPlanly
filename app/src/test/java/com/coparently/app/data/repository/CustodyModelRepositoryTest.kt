package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.CustodyModelDao
import com.coparently.app.data.local.entity.CustodyModelEntity
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.custody.ChildOverrideCodec
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.CustodyTimestamp
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.custody.SharedCustodyRead
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [CustodyModelRepository]'s Firestore path, in both directions.
 *
 * The repository talked only to Room, so after pairing the second phone had no custody pattern
 * at all. What is asserted here is the shape of the fix rather than the fact that a write
 * happens: Room is written before Firestore and survives a refused remote write; an unpaired
 * user touches Firestore not at all; the mirror never discards a local model newer than the
 * document it just received; and the stream *keeps delivering after a failure* at every stage —
 * the defect `MessageRepositoryImpl` still ships, where a terminal `catch` completes the mirror
 * flow and leaves the feature running on Room alone for the rest of the process.
 *
 * The repository is built with a sharing scope on [dispatcher] and every test runs on the same
 * scheduler, so what is collected is [CustodyModelRepository.observeShared] itself — the real
 * shared flow, `shareIn` and all — with backoff delays elapsing on virtual time.
 */
// One class because every case needs the same paired-repository fixture on one scheduler;
// splitting it by theme would copy that fixture, not shrink it (as `SyncServiceTest` says).
@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class CustodyModelRepositoryTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    private lateinit var custodyModelDao: CustodyModelDao
    private lateinit var userRepository: UserRepository
    private lateinit var firestoreCustodyDataSource: FirestoreCustodyDataSource
    private lateinit var activityAnnouncer: ActivityAnnouncer
    private lateinit var repository: CustodyModelRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        custodyModelDao = mockk(relaxed = true)
        userRepository = mockk()
        firestoreCustodyDataSource = mockk()
        activityAnnouncer = mockk(relaxed = true)
        repository = CustodyModelRepository(
            custodyModelDao,
            userRepository,
            firestoreCustodyDataSource,
            activityAnnouncer,
            mockk(relaxed = true),
            CoroutineScope(dispatcher)
        )

        pairedWith(PARTNER_UID)
        coEvery { custodyModelDao.getModelById(any()) } returns null
        activeRows()
        coEvery { firestoreCustodyDataSource.getCustody(any()) } returns null
        coEvery { firestoreCustodyDataSource.setCustody(any(), any(), any()) } returns Unit
        every { firestoreCustodyDataSource.observeCustody(any()) } returns flowOf(null)
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    // ---- writing ----------------------------------------------------------

    @Test
    fun `saving writes Room before Firestore`() = runTest(dispatcher) {
        val custody = slot<SharedCustody>()
        coEvery {
            firestoreCustodyDataSource.setCustody(any(), any(), capture(custody))
        } returns Unit

        repository.saveAndActivate(localModel())

        // Room is the source of truth: the remote write is what happens *after* the local one
        // has already succeeded, never instead of it.
        val entity = slot<CustodyModelEntity>()
        coVerifyOrder {
            custodyModelDao.deactivateFamily(DOCUMENT_ID)
            custodyModelDao.insertModel(capture(entity))
            firestoreCustodyDataSource.setCustody(DOCUMENT_ID, PARTICIPANTS, any())
        }
        // Stamped with the family it was saved in (schema 45), so no other family reads it.
        assertEquals(DOCUMENT_ID, entity.captured.familyId)
        assertEquals(MY_UID, custody.captured.lastModifiedBy)
        assertEquals(LOCAL_MODEL_ID, custody.captured.model.id)
    }

    @Test
    fun `participants are written sorted even when the partner uid sorts first`() =
        runTest(dispatcher) {
            // firestore.rules requires the *stored* array to satisfy participants[0] <
            // participants[1] on create, and compares it order-sensitively on every update, so
            // an unsorted write would deny every later write to this pair's document forever.
            // The fixture's uids are deliberately in the wrong order for this to bite.
            pairedWith(EARLIER_PARTNER_UID)
            val participants = slot<List<String>>()
            coEvery {
                firestoreCustodyDataSource.setCustody(any(), capture(participants), any())
            } returns Unit

            repository.saveAndActivate(localModel())

            assertEquals(listOf(EARLIER_PARTNER_UID, MY_UID), participants.captured)
            // And the id is the canonical join of that same sorted pair — the rule binds the two
            // together, so a document whose id disagrees with its participants cannot be created.
            coVerify(exactly = 1) {
                firestoreCustodyDataSource.setCustody("uid0__uidA", any(), any())
            }
        }

    @Test
    fun `a Firestore failure on save leaves the local model in place`() = runTest(dispatcher) {
        coEvery {
            firestoreCustodyDataSource.setCustody(any(), any(), any())
        } throws permissionDenied()
        val entity = slot<CustodyModelEntity>()

        // Must not throw: three ViewModels call this from viewModelScope.launch, where an
        // uncaught PERMISSION_DENIED kills the process rather than failing the sync.
        repository.saveAndActivate(localModel())

        coVerify(exactly = 1) { custodyModelDao.insertModel(capture(entity)) }
        assertEquals(LOCAL_MODEL_ID, entity.captured.id)
        assertTrue(entity.captured.isActive)
    }

    @Test
    fun `an unpaired user saves to Room and writes no document`() = runTest(dispatcher) {
        pairedWith(partnerUid = null)
        val entity = slot<CustodyModelEntity>()

        repository.saveAndActivate(localModel())

        coVerify(exactly = 1) { custodyModelDao.insertModel(capture(entity)) }
        assertEquals(LOCAL_MODEL_ID, entity.captured.id)
        assertNull(entity.captured.familyId)
        coVerify(exactly = 1) { custodyModelDao.deactivateFamily(null) }
        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
    }

    @Test
    fun `an update preserves the createdAt already on the document`() = runTest(dispatcher) {
        coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } returns remoteCustody()
        val custody = slot<SharedCustody>()
        coEvery {
            firestoreCustodyDataSource.setCustody(any(), any(), capture(custody))
        } returns Unit

        repository.saveAndActivate(localModel())

        // Editing the pattern must not re-date the pair's arrangement.
        assertEquals(REMOTE_CREATED_AT, custody.captured.createdAt)
    }

    // ---- re-expressing a pattern, rather than changing one ------------------

    @Test
    fun `re-slotting keeps the stored row's dates and pushes nothing`() = runTest(dispatcher) {
        // The property the pairing reconciliation rests on, and the one `PairingViewModelTest`
        // structurally cannot see: it mocks this repository, so it pins that the *argument* is
        // complemented and would pass unchanged if this method re-dated the row.
        //
        // Re-dating would make this device win every later staleness comparison in
        // `mirrorIntoRoom`, turning a re-expression of the same arrangement — the accepter's
        // pattern rewritten for their new slot — into an unattributed overwrite of the
        // co-parent's schedule. Not pushing matters for the same reason from the other side: the
        // shared document may hold a pattern the user is at that moment being asked about.
        val stored = localEntity(lastModifiedAtMillis = LONG_AGO)
            .copy(createdAt = PRE_EXISTING_CREATED_AT, familyId = DOCUMENT_ID)
        coEvery { custodyModelDao.getModelById(LOCAL_MODEL_ID) } returns stored
        val entity = slot<CustodyModelEntity>()

        repository.saveReslotted(localModel().copy(momDayIndices = (7..13).toSet()))

        coVerify(exactly = 1) { custodyModelDao.insertModel(capture(entity)) }
        assertEquals(stored.createdAt, entity.captured.createdAt)
        assertEquals(stored.lastModifiedAtMillis, entity.captured.lastModifiedAtMillis)
        // The same family's schedule, re-expressed: it stays that family's (schema 45).
        assertEquals(DOCUMENT_ID, entity.captured.familyId)
        coVerify(exactly = 1) { custodyModelDao.deactivateFamily(DOCUMENT_ID) }
        // The complement itself did land, so this is not passing by writing nothing at all.
        assertEquals("[7,8,9,10,11,12,13]", entity.captured.momDaysPattern)
        assertTrue(entity.captured.isActive)
        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
    }

    @Test
    fun `archiving a rejected pattern keeps its dates under a fresh, inactive id`() =
        runTest(dispatcher) {
            // Room's insert REPLACEs on the primary key, so archiving under the original id would
            // delete the pattern that just won rather than keep the one that lost. The dates are
            // kept for the same reason as above: an inactive row is never pushed, but it can be
            // re-activated from `getAllModels()`, and it must not come back claiming to be newer.
            val stored = localEntity(lastModifiedAtMillis = LONG_AGO)
                .copy(createdAt = PRE_EXISTING_CREATED_AT, familyId = DOCUMENT_ID)
            coEvery { custodyModelDao.getModelById(LOCAL_MODEL_ID) } returns stored
            val entity = slot<CustodyModelEntity>()

            repository.archiveRejected(localModel())

            coVerify(exactly = 1) { custodyModelDao.insertModel(capture(entity)) }
            assertNotEquals(LOCAL_MODEL_ID, entity.captured.id)
            assertFalse(entity.captured.isActive)
            assertEquals(DOCUMENT_ID, entity.captured.familyId)
            assertEquals(stored.createdAt, entity.captured.createdAt)
            assertEquals(stored.lastModifiedAtMillis, entity.captured.lastModifiedAtMillis)
            coVerify(exactly = 0) { custodyModelDao.deactivateFamily(any()) }
            coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
        }

    // ---- a swallowed write must not become a silent revert -----------------

    @Test
    fun `a swallowed write is not undone by the mirror replaying the older document`() =
        runTest(dispatcher) {
            val saved = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(saved)) } returns Unit
            coEvery {
                firestoreCustodyDataSource.setCustody(any(), any(), any())
            } throws permissionDenied()

            repository.saveAndActivate(localModel())

            // Room now holds the user's choice — and only Room does, because the push was
            // swallowed. The document still holds the pattern that was mirrored before it.
            activeRows(saved.captured)
            every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
                flowOf(remoteCustody(lastModifiedAtMillis = LONG_AGO))

            repository.observeShared().first()

            // Exactly the one insert the save itself performed. `saveAndActivate` deactivated
            // every model, so the previously mirrored row is inactive and the equality guard
            // does not fire on the replay — without the staleness check the mirror would
            // reactivate the stale pattern and the user's setup would silently revert.
            coVerify(exactly = 1) { custodyModelDao.insertModel(any()) }
            coVerify(exactly = 1) { custodyModelDao.deactivateFamily(DOCUMENT_ID) }
            assertEquals(LOCAL_MODEL_ID, saved.captured.id)
        }

    @Test
    fun `a local model newer than the document is sent again rather than discarded`() =
        runTest(dispatcher) {
            activeRows(localEntity(lastModifiedAtMillis = RECENTLY))
            every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
                flowOf(remoteCustody(lastModifiedAtMillis = LONG_AGO))
            val custody = slot<SharedCustody>()
            coEvery {
                firestoreCustodyDataSource.setCustody(any(), any(), capture(custody))
            } returns Unit

            repository.observeShared().first()

            // Refusing to mirror alone would leave the pair permanently disagreeing, with the
            // co-parent reading this device's own lost write. The listener has just proved it
            // is alive, so the write that failed gets one more attempt.
            coVerify(exactly = 1) {
                firestoreCustodyDataSource.setCustody(DOCUMENT_ID, any(), any())
            }
            assertEquals(LOCAL_MODEL_ID, custody.captured.model.id)
            assertEquals(RECENTLY, custody.captured.lastModifiedAtMillis)
        }

    @Test
    fun `a document newer than the local model still wins`() = runTest(dispatcher) {
        activeRows(localEntity(lastModifiedAtMillis = LONG_AGO))
        every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
            flowOf(remoteCustody(lastModifiedAtMillis = RECENTLY))
        val entity = slot<CustodyModelEntity>()

        repository.observeShared().first()

        // The guard is "do not discard something newer", not "never mirror again": the
        // co-parent changing the schedule must still reach this device.
        coVerify(exactly = 1) { custodyModelDao.insertModel(capture(entity)) }
        assertEquals(REMOTE_MODEL_ID, entity.captured.id)
        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
    }

    // ---- reading ----------------------------------------------------------

    @Test
    fun `a pattern held before pairing is published once the pair has no document`() =
        runTest(dispatcher) {
            // The first parent sets the schedule, then pairs. The save pushed nothing (no pair),
            // the mirror re-publishes only over a document that exists, and the accepter's
            // reconciliation writes only when the accepter has a pattern — so the other phone's
            // calendar stayed blank until this pass existed.
            activeRows(mirroredEntity())
            coEvery { firestoreCustodyDataSource.getCustody(any()) } returns null

            repository.publishLocalIfMissing()

            coVerify(exactly = 1) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
        }

    @Test
    fun `nothing is published over a document the pair already has`() = runTest(dispatcher) {
        activeRows(mirroredEntity())
        coEvery { firestoreCustodyDataSource.getCustody(any()) } returns remoteCustody()

        repository.publishLocalIfMissing()

        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
    }

    @Test
    fun `nothing is published when the pair's document could not be read`() = runTest(dispatcher) {
        // Unavailable is not Absent: publishing on the strength of a failed read is how a
        // co-parent's schedule gets replaced by a device that merely could not see it.
        activeRows(mirroredEntity())
        coEvery { firestoreCustodyDataSource.getCustody(any()) } throws permissionDenied()

        repository.publishLocalIfMissing()

        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
    }

    @Test
    fun `an unpaired user cannot answer whether a document exists`() = runTest(dispatcher) {
        // Unavailable, not Absent. With no pair known locally there is nothing to look in, and
        // a caller told "there is no document" would go on to create one over whatever is there
        // once the pairing does land.
        pairedWith(partnerUid = null)

        assertEquals(SharedCustodyRead.Unavailable, repository.readShared())
        assertNull(repository.observeShared().first())

        coVerify(exactly = 0) { firestoreCustodyDataSource.getCustody(any()) }
        coVerify(exactly = 0) { firestoreCustodyDataSource.observeCustody(any()) }
    }

    @Test
    fun `the one-shot read reaches the document derived from the two uids`() =
        runTest(dispatcher) {
            coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } returns remoteCustody()

            val shared = (repository.readShared() as SharedCustodyRead.Found).custody

            assertEquals(REMOTE_MODEL_ID, shared.model.id)
            assertEquals(PARTNER_UID, shared.lastModifiedBy)
        }

    @Test
    fun `a failed one-shot read is Unavailable, never an absent document`() = runTest(dispatcher) {
        // The distinction the pairing conflict screen rests on: a denied read must not read as
        // "the co-parent has no schedule", or a device that merely could not look replaces one.
        coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } throws permissionDenied()

        assertEquals(SharedCustodyRead.Unavailable, repository.readShared())
    }

    @Test
    fun `a successful read of a pair with no document is Absent`() = runTest(dispatcher) {
        coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } returns null

        assertEquals(SharedCustodyRead.Absent, repository.readShared())
    }

    @Test
    fun `the observer keeps delivering after a failed listener`() = runTest(dispatcher) {
        val collections = AtomicInteger()
        every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns flow {
            if (collections.getAndIncrement() == 0) throw permissionDenied()
            emit(remoteCustody())
        }
        val startedAt = scheduler.currentTime

        // Fails — the exception escapes and the test errors out — without `retryWhen` on the
        // upstream. A terminal `catch` instead would make it fail differently and worse: the
        // flow would complete having emitted nothing, silently, forever.
        val delivered = repository.observeShared().first { it != null }

        assertEquals(REMOTE_MODEL_ID, delivered?.model?.id)
        assertEquals(2, collections.get())
        // Not just "it retried" but "it waited first": under `runTest` the virtual clock skips
        // any delay, so a backoff of zero would satisfy every other assertion here.
        assertEquals(FIRST_BACKOFF_MS, scheduler.currentTime - startedAt)
    }

    @Test
    fun `a failure in the Room mirror does not terminate the stream`() = runTest(dispatcher) {
        val writes = AtomicInteger()
        coEvery { custodyModelDao.insertModel(any()) } answers {
            if (writes.getAndIncrement() == 0) error("Room write failed") else Unit
        }
        every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
            flowOf(remoteCustody())

        // The mirror runs on the sharing coroutine, outside every guard the write path has. Its
        // failure used to reach that scope's root and kill the process, while subscribers saw
        // nothing at all — a shared flow never delivers an upstream failure downstream.
        val delivered = repository.observeShared().first { it != null }

        assertEquals(REMOTE_MODEL_ID, delivered?.model?.id)
        assertEquals(2, writes.get())
    }

    @Test
    fun `a remote model is mirrored into Room under the id it arrived with`() =
        runTest(dispatcher) {
            every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
                flowOf(remoteCustody())
            val entity = slot<CustodyModelEntity>()

            repository.observeShared().first()

            coVerifyOrder {
                custodyModelDao.deactivateFamily(DOCUMENT_ID)
                custodyModelDao.insertModel(capture(entity))
            }
            // The writer's id, not a freshly generated one: otherwise the two devices accumulate
            // a copy of the same schedule per sync instead of converging on one row.
            assertEquals(REMOTE_MODEL_ID, entity.captured.id)
            // And the family whose document it came from, never the family on screen's guess.
            assertEquals(DOCUMENT_ID, entity.captured.familyId)
            assertTrue(entity.captured.isActive)
            assertEquals("[0,1,2,3,4,5,6]", entity.captured.momDaysPattern)
            assertEquals(REMOTE_CREATED_AT, entity.captured.createdAt)
            assertEquals(REMOTE_MODIFIED_AT, entity.captured.lastModifiedAtMillis)
        }

    @Test
    fun `an unchanged remote document is not written to Room again`() = runTest(dispatcher) {
        every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
            flowOf(remoteCustody())
        coEvery { custodyModelDao.getModelById(REMOTE_MODEL_ID) } returns mirroredEntity().copy(familyId = DOCUMENT_ID)

        repository.observeShared().first()

        // Firestore echoes this device's own writes straight back; re-inserting an identical
        // row would tick Room's invalidation tracker and re-emit to every observer for nothing.
        coVerify(exactly = 0) { custodyModelDao.insertModel(any()) }
        coVerify(exactly = 0) { custodyModelDao.deactivateFamily(any()) }
    }

    // ---- the family on screen's own document (phase 2 review) ----------------

    @Test
    fun `another family's document is never this family's`() = runTest(dispatcher) {
        every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns flowOf(remoteCustody())

        // The device follows the family on screen, uidA and uidB. Asked about uidA and uidC's
        // family, it answers with nothing — never uidB's schedule and swaps under uidC's name.
        assertEquals(REMOTE_MODEL_ID, repository.observeSharedOf(DOCUMENT_ID).first { it != null }?.model?.id)
        assertNull(repository.observeSharedOf(OTHER_DOCUMENT_ID).first())
    }

    @Test
    fun `a switch never shows the previous family's document as the next one's`() = runTest(dispatcher) {
        val users = MutableStateFlow(listOf(signedInWith(PARTNER_UID)))
        every { userRepository.getAllUsers() } returns users
        every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns flowOf(remoteCustody())
        // The next family's listener has not answered yet.
        every { firestoreCustodyDataSource.observeCustody(OTHER_DOCUMENT_ID) } returns flow { awaitCancellation() }
        repository.observeShared().first { it != null }

        users.value = listOf(signedInWith(OTHER_PARTNER_UID))
        val seen = mutableListOf<SharedCustody?>()
        backgroundScope.launch { repository.observeSharedOf(OTHER_DOCUMENT_ID).toList(seen) }
        runCurrent()

        // The shared stream replays uidB's document until uidC's answers. Read for uidC's family,
        // that replay is "not known yet", never uidB's schedule.
        assertTrue("saw $seen", seen.all { it == null })
    }

    // ---- one parent in two families: a schedule per family (schema 45) ------------------------
    //
    // The phone used to keep one active row for every family. With the uidA–uidC family on screen,
    // the mirror, the sync's publish and the pattern editor all read the uidA–uidB family's row as
    // the family on screen's, and wrote it into uidA–uidC's document.

    @Test
    fun `another family's newer schedule is never sent into this family's document`() = runTest(dispatcher) {
        inTwoFamilies(onScreen = PARTNER_UID)
        activeRows(carolFamilyRow(lastModifiedAtMillis = RECENTLY))
        every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
            flowOf(remoteCustody(lastModifiedAtMillis = LONG_AGO))
        val entity = slot<CustodyModelEntity>()
        coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

        repository.observeShared().first()

        // uidC's schedule is newer than uidB's document, which is what made the mirror push it
        // over that document. It is not uidB's family's to compare with.
        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
        assertEquals(REMOTE_MODEL_ID, entity.captured.id)
        assertEquals(DOCUMENT_ID, entity.captured.familyId)
    }

    @Test
    fun `a republish goes to the row's own family even when the screen has just moved on`() =
        runTest(dispatcher) {
            // uidB's document arrives as the switch to uidC's family lands: the stream still
            // follows uidB's family, the one-shot read of the profile already says uidC's.
            val bobOnScreen = signedInWith(PARTNER_UID).copy(partnerIds = listOf(PARTNER_UID, OTHER_PARTNER_UID))
            every { userRepository.getAllUsers() } returns flowOf(listOf(bobOnScreen))
            coEvery { userRepository.getUserById(MY_UID) } returns bobOnScreen.copy(partnerId = OTHER_PARTNER_UID)
            activeRows(bobFamilyRow(lastModifiedAtMillis = RECENTLY))
            every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
                flowOf(remoteCustody(lastModifiedAtMillis = LONG_AGO))

            repository.observeShared().first()

            coVerify(exactly = 1) { firestoreCustodyDataSource.setCustody(DOCUMENT_ID, PARTICIPANTS, any()) }
            coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(OTHER_DOCUMENT_ID, any(), any()) }
        }

    @Test
    fun `a family with no document is never given another family's schedule`() = runTest(dispatcher) {
        inTwoFamilies(onScreen = OTHER_PARTNER_UID)
        activeRows(bobFamilyRow())
        coEvery { firestoreCustodyDataSource.getCustody(OTHER_DOCUMENT_ID) } returns null

        repository.publishLocalIfMissing()

        // Every sync runs this. uidC's family has no schedule yet, and uidB's is not a stand-in.
        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
    }

    @Test
    fun `a schedule set in one family carries none of another family's layers or children`() =
        runTest(dispatcher) {
            inTwoFamilies(onScreen = OTHER_PARTNER_UID)
            activeRows(
                bobFamilyRow().copy(
                    seasonalLayersJson = """["$SUMMER_WIRE"]""",
                    childOverridesJson = """["$BABY_WIRE"]"""
                )
            )
            val custody = slot<SharedCustody>()
            coEvery { firestoreCustodyDataSource.setCustody(any(), any(), capture(custody)) } returns Unit

            repository.createWeekOnWeekOff(START_DATE)

            coVerify(exactly = 1) {
                firestoreCustodyDataSource.setCustody(OTHER_DOCUMENT_ID, listOf(MY_UID, OTHER_PARTNER_UID), any())
            }
            assertEquals(emptyList<String>(), custody.captured.seasonalLayersWire)
            assertEquals(emptyList<String>(), custody.captured.childOverridesWire)
        }

    @Test
    fun `layers and a child's schedule are never laid over another family's pattern`() = runTest(dispatcher) {
        inTwoFamilies(onScreen = OTHER_PARTNER_UID)
        activeRows(bobFamilyRow())

        // uidC's family has no pattern of its own to layer on, so there is nothing to submit.
        assertNull(repository.submitSeasonalLayers(listOf(SUMMER)))
        assertNull(repository.submitChildOverride("baby-1", baby()))
        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
        coVerify(exactly = 0) { custodyModelDao.insertModel(any()) }
    }

    @Test
    fun `the calendar reads the schedule of the family on screen`() = runTest(dispatcher) {
        inTwoFamilies(onScreen = OTHER_PARTNER_UID)
        val swap = DayOverride(toParent = "dad", requestedBy = PARTNER_UID, requestedAt = "2026-08-01T09:00:00")
        activeRows(
            bobFamilyRow(lastModifiedAtMillis = RECENTLY)
                .copy(dayOverridesJson = DayOverrideJson.encode(mapOf("2026-08-10" to swap))),
            carolFamilyRow(lastModifiedAtMillis = LONG_AGO)
        )

        assertEquals(CAROL_MODEL_ID, repository.getActiveModelSync()?.id)
        assertEquals(CAROL_MODEL_ID, repository.getActiveModel().first()?.id)
        assertEquals(CAROL_MODEL_ID, repository.storedCustody().first?.id)
        // uidB's family's swap is not a day of uidC's family's.
        assertEquals(emptyMap<String, DayOverride>(), repository.observeDayOverrides().first())
        assertEquals(emptyMap<String, DayOverride>(), repository.storedCustody().second)
    }

    @Test
    fun `with two co-parents a schedule that names no family is nobody's`() = runTest(dispatcher) {
        inTwoFamilies(onScreen = PARTNER_UID)
        // Saved before rows named their family; nothing says which family it was.
        activeRows(mirroredEntity())
        coEvery { firestoreCustodyDataSource.getCustody(any()) } returns null

        assertNull(repository.getActiveModelSync())
        repository.publishLocalIfMissing()

        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
    }

    @Test
    fun `an unpaired parent still reads the schedule they last had`() = runTest(dispatcher) {
        // Characterises what unpairing always did: the calendar keeps the last schedule.
        pairedWith(partnerUid = null)
        activeRows(bobFamilyRow())

        assertEquals(REMOTE_MODEL_ID, repository.getActiveModelSync()?.id)
    }

    @Test
    fun `a save lands in the family it was decided for, even if the screen moves on mid-save`() =
        runTest(dispatcher) {
            // uidC's family is on screen when the save starts; a push tap switches to uidB's
            // before it finishes. Every step reads the family once, at the start: uidC's layers,
            // uidC's document, uidC's stamp — never uidC's pattern over uidB's document.
            val carolOnScreen = signedInWith(OTHER_PARTNER_UID)
                .copy(partnerIds = listOf(PARTNER_UID, OTHER_PARTNER_UID))
            coEvery { userRepository.getUserById(MY_UID) } returnsMany
                listOf(carolOnScreen, carolOnScreen.copy(partnerId = PARTNER_UID))
            val entity = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

            repository.createWeekOnWeekOff(START_DATE)

            coVerify(exactly = 1) {
                firestoreCustodyDataSource.setCustody(OTHER_DOCUMENT_ID, listOf(MY_UID, OTHER_PARTNER_UID), any())
            }
            coVerify(exactly = 0) { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) }
            coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(DOCUMENT_ID, any(), any()) }
            assertEquals(OTHER_DOCUMENT_ID, entity.captured.familyId)
        }

    @Test
    fun `an ended family's schedule is not the next family's`() = runTest(dispatcher) {
        // uidA unpaired from uidB and now has uidC alone. The uidA–uidB row never moves into the
        // new household (CLAUDE.md item 18): not read as its schedule, not published to it.
        pairedWith(OTHER_PARTNER_UID)
        activeRows(bobFamilyRow())
        coEvery { firestoreCustodyDataSource.getCustody(OTHER_DOCUMENT_ID) } returns null

        assertNull(repository.getActiveModelSync())
        repository.publishLocalIfMissing()

        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
    }

    @Test
    fun `a first pairing reconciles the schedule this parent holds on their own`() = runTest(dispatcher) {
        pairedWith(partnerUid = null)
        val mine = mirroredEntity().copy(id = LOCAL_MODEL_ID)
        activeRows(mine)
        assertEquals(LOCAL_MODEL_ID, repository.getUnsharedModelSync()?.id)

        // The same answer once the pairing reaches Room, so the accept path does not depend on
        // which of the two lands first.
        pairedWith(PARTNER_UID)
        activeRows(mine, bobFamilyRow(lastModifiedAtMillis = RECENTLY))
        assertEquals(LOCAL_MODEL_ID, repository.getUnsharedModelSync()?.id)
    }

    @Test
    fun `no family's schedule is ever the one a pairing reconciles`() = runTest(dispatcher) {
        // An ended family's row is what the calendar shows while unpaired, and it is still
        // never carried into the next family by the accept path.
        pairedWith(partnerUid = null)
        activeRows(bobFamilyRow())
        assertEquals(REMOTE_MODEL_ID, repository.getActiveModelSync()?.id)
        assertNull(repository.getUnsharedModelSync())

        pairedWith(OTHER_PARTNER_UID)
        assertNull(repository.getUnsharedModelSync())
    }

    @Test
    fun `with two co-parents no schedule is unshared`() = runTest(dispatcher) {
        inTwoFamilies(onScreen = PARTNER_UID)
        activeRows(mirroredEntity())

        assertNull(repository.getUnsharedModelSync())
    }

    @Test
    fun `a one-family phone's row from before schema 45 is stamped by the next echo`() = runTest(dispatcher) {
        // The same schedule the document holds, stored before rows named their family. The echo
        // is not "unchanged": it is what tells this row which family it is.
        coEvery { custodyModelDao.getModelById(REMOTE_MODEL_ID) } returns mirroredEntity()
        every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns flowOf(remoteCustody())
        val entity = slot<CustodyModelEntity>()
        coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

        repository.observeShared().first()

        assertEquals(mirroredEntity().copy(familyId = DOCUMENT_ID), entity.captured)
        coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
    }

    @Test
    fun `another family's row under the same model id lends this family nothing`() = runTest(dispatcher) {
        // The old republish could copy one family's model, id and all, into the other's
        // document, so two families' rows can share an id. A document an older build wrote
        // without `contactWindows` keeps the row's own windows — never the other family's.
        inTwoFamilies(onScreen = PARTNER_UID)
        coEvery { custodyModelDao.getModelById(REMOTE_MODEL_ID) } returns
            mirroredEntity().copy(familyId = OTHER_DOCUMENT_ID, contactWindowsJson = """["2|15:00|19:00|dad"]""")
        every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
            flowOf(remoteCustody(lastModifiedAtMillis = RECENTLY))
        val entity = slot<CustodyModelEntity>()
        coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

        repository.observeShared().first()

        assertEquals(DOCUMENT_ID, entity.captured.familyId)
        assertNull(entity.captured.contactWindowsJson)
    }

    // ---- contact windows (MON-6b) -------------------------------------------

    @Test
    fun `a pattern save writes its contact windows, and an empty list rather than no key`() =
        runTest(dispatcher) {
            val custody = slot<SharedCustody>()
            coEvery {
                firestoreCustodyDataSource.setCustody(any(), any(), capture(custody))
            } returns Unit

            repository.saveAndActivate(localModel().copy(contactWindows = listOf(WEDNESDAY_WINDOW)))
            assertEquals(listOf("2|15:00|19:00|dad"), custody.captured.contactWindowsWire)

            // "None" is an explicit empty list: a missing key is what an older build writes, and
            // the mirror reads that as "keep what you have" rather than as a removal.
            repository.saveAndActivate(localModel())
            assertEquals(emptyList<String>(), custody.captured.contactWindowsWire)
        }

    @Test
    fun `a document an older build wrote without windows keeps this device's windows`() =
        runTest(dispatcher) {
            // An older co-parent's ordinary save rewrites the whole document with no
            // `contactWindows` key. That is not a removal, and must not erase the afternoons.
            val stored = mirroredEntity().copy(contactWindowsJson = """["2|15:00|19:00|dad"]""")
            coEvery { custodyModelDao.getModelById(REMOTE_MODEL_ID) } returns stored
            every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
                flowOf(remoteCustody(lastModifiedAtMillis = RECENTLY))
            val entity = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

            repository.observeShared().first()

            assertEquals(stored.contactWindowsJson, entity.captured.contactWindowsJson)
        }

    @Test
    fun `an explicit list on the document replaces this device's windows, empty included`() =
        runTest(dispatcher) {
            val stored = mirroredEntity().copy(contactWindowsJson = """["2|15:00|19:00|dad"]""")
            coEvery { custodyModelDao.getModelById(REMOTE_MODEL_ID) } returns stored
            every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
                flowOf(remoteCustody().copy(contactWindowsWire = emptyList()))
            val entity = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

            repository.observeShared().first()

            assertNull(entity.captured.contactWindowsJson)
        }

    @Test
    fun `a stored row's windows come back on the model`() = runTest(dispatcher) {
        activeRows(mirroredEntity().copy(contactWindowsJson = """["9|15:00|19:00|dad","2|15:00|19:00|dad"]"""))

        val model = repository.getActiveModelSync()

        assertEquals(listOf(WEDNESDAY_WINDOW, WEDNESDAY_WINDOW.copy(dayIndex = 9)), model?.contactWindows)
    }

    // ---- seasonal layers (MON-14) -------------------------------------------

    @Test
    fun `a pattern save writes its seasonal layers, and an empty list rather than no key`() =
        runTest(dispatcher) {
            val custody = slot<SharedCustody>()
            coEvery {
                firestoreCustodyDataSource.setCustody(any(), any(), capture(custody))
            } returns Unit

            repository.saveAndActivate(
                localModel().copy(seasonalLayers = listOf(SUMMER), unreadableLayers = listOf(FUTURE))
            )
            assertEquals(listOf(SUMMER_WIRE, FUTURE).sorted(), custody.captured.seasonalLayersWire)

            repository.saveAndActivate(localModel())
            assertEquals(emptyList<String>(), custody.captured.seasonalLayersWire)
        }

    @Test
    fun `a document an older build wrote without layers keeps this device's layers`() =
        runTest(dispatcher) {
            val stored = mirroredEntity().copy(seasonalLayersJson = """["$SUMMER_WIRE"]""")
            coEvery { custodyModelDao.getModelById(REMOTE_MODEL_ID) } returns stored
            every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
                flowOf(remoteCustody(lastModifiedAtMillis = RECENTLY))
            val entity = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

            repository.observeShared().first()

            assertEquals(stored.seasonalLayersJson, entity.captured.seasonalLayersJson)
        }

    @Test
    fun `an explicit empty layer list on the document removes this device's layers`() =
        runTest(dispatcher) {
            val stored = mirroredEntity().copy(seasonalLayersJson = """["$SUMMER_WIRE"]""")
            coEvery { custodyModelDao.getModelById(REMOTE_MODEL_ID) } returns stored
            every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
                flowOf(remoteCustody().copy(seasonalLayersWire = emptyList()))
            val entity = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

            repository.observeShared().first()

            assertNull(entity.captured.seasonalLayersJson)
        }

    @Test
    fun `saving the base pattern carries the agreed layers, unreadable ones included`() =
        runTest(dispatcher) {
            activeRows(mirroredEntity().copy(seasonalLayersJson = """["$SUMMER_WIRE","$FUTURE"]"""))
            val entity = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

            repository.createWeekOnWeekOff(START_DATE)

            assertEquals("""["$SUMMER_WIRE","$FUTURE"]""", entity.captured.seasonalLayersJson)
        }

    @Test
    fun `submitting layers keeps the base pattern and refuses without one`() = runTest(dispatcher) {
        activeRows()
        assertNull(repository.submitSeasonalLayers(listOf(SUMMER)))

        activeRows(mirroredEntity())
        val entity = slot<CustodyModelEntity>()
        coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

        repository.submitSeasonalLayers(listOf(SUMMER))

        assertEquals("[0,1,2,3,4,5,6]", entity.captured.momDaysPattern)
        assertEquals("""["$SUMMER_WIRE"]""", entity.captured.seasonalLayersJson)
    }

    // ---- a child's own schedule (FAM-4) --------------------------------------

    @Test
    fun `a pattern save writes the children's overrides, and an empty list rather than no key`() =
        runTest(dispatcher) {
            val custody = slot<SharedCustody>()
            coEvery {
                firestoreCustodyDataSource.setCustody(any(), any(), capture(custody))
            } returns Unit

            repository.saveAndActivate(
                localModel().copy(childOverrides = listOf(baby()), unreadableChildOverrides = listOf(CHILD_FUTURE))
            )
            assertEquals(listOf(BABY_WIRE, CHILD_FUTURE).sorted(), custody.captured.childOverridesWire)

            repository.saveAndActivate(localModel())
            assertEquals(emptyList<String>(), custody.captured.childOverridesWire)
        }

    @Test
    fun `a document an older build wrote without overrides keeps this device's overrides`() =
        runTest(dispatcher) {
            val stored = mirroredEntity().copy(childOverridesJson = """["$BABY_WIRE"]""")
            coEvery { custodyModelDao.getModelById(REMOTE_MODEL_ID) } returns stored
            every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
                flowOf(remoteCustody(lastModifiedAtMillis = RECENTLY))
            val entity = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

            repository.observeShared().first()

            assertEquals(stored.childOverridesJson, entity.captured.childOverridesJson)
        }

    @Test
    fun `an explicit empty override list on the document sends every child back to the family schedule`() =
        runTest(dispatcher) {
            val stored = mirroredEntity().copy(childOverridesJson = """["$BABY_WIRE"]""")
            coEvery { custodyModelDao.getModelById(REMOTE_MODEL_ID) } returns stored
            every { firestoreCustodyDataSource.observeCustody(DOCUMENT_ID) } returns
                flowOf(remoteCustody().copy(childOverridesWire = emptyList()))
            val entity = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

            repository.observeShared().first()

            assertNull(entity.captured.childOverridesJson)
        }

    @Test
    fun `saving the base pattern carries the agreed overrides, unreadable ones included`() =
        runTest(dispatcher) {
            activeRows(mirroredEntity().copy(childOverridesJson = """["$BABY_WIRE","$CHILD_FUTURE"]"""))
            val entity = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

            repository.createWeekOnWeekOff(START_DATE)

            assertEquals("""["$BABY_WIRE","$CHILD_FUTURE"]""", entity.captured.childOverridesJson)
        }

    @Test
    fun `a child's override replaces only that child's and keeps the base pattern`() = runTest(dispatcher) {
        activeRows()
        assertNull(repository.submitChildOverride("baby-1", baby()))

        activeRows(mirroredEntity().copy(childOverridesJson = """["$TEEN_WIRE","$CHILD_FUTURE"]"""))
        val entity = slot<CustodyModelEntity>()
        coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

        repository.submitChildOverride("baby-1", baby())

        assertEquals("[0,1,2,3,4,5,6]", entity.captured.momDaysPattern)
        val stored = ChildOverrideJson.decode(entity.captured.childOverridesJson)
        assertEquals(listOf("baby-1", "teen-1"), stored.overrides.map { it.childId })
        assertEquals(listOf(CHILD_FUTURE), stored.unreadable)
    }

    @Test
    fun `sending a child back to the family schedule removes only that child's override`() =
        runTest(dispatcher) {
            activeRows(mirroredEntity().copy(childOverridesJson = """["$BABY_WIRE","$TEEN_WIRE"]"""))
            val entity = slot<CustodyModelEntity>()
            coEvery { custodyModelDao.insertModel(capture(entity)) } returns Unit

            repository.submitChildOverride("baby-1", null)

            assertEquals("""["$TEEN_WIRE"]""", entity.captured.childOverridesJson)
        }

    @Test
    fun `a paired family's override change is a proposal that leaves the agreed list verbatim`() =
        runTest(dispatcher) {
            activeRows(mirroredEntity())
            val agreed = remoteCustody().copy(childOverridesWire = listOf(CHILD_FUTURE))
            coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } returns agreed
            val custody = slot<SharedCustody>()
            coEvery { firestoreCustodyDataSource.setCustody(any(), any(), capture(custody)) } returns Unit

            val result = repository.submitChildOverride("baby-1", baby())

            assertEquals(PatternSubmission.PROPOSED, result)
            // The proposal states the new list; the agreed one rides along exactly as stored,
            // which is what `childOverridesKeptOrDropped` requires of a proposal-only write.
            assertEquals(listOf(BABY_WIRE), custody.captured.proposal?.childOverridesWire)
            assertEquals(listOf(CHILD_FUTURE), custody.captured.childOverridesWire)
            coVerify(exactly = 0) { custodyModelDao.insertModel(any()) }
        }

    private fun baby() = requireNotNull(ChildOverrideCodec.decode(BABY_WIRE))

    // ---- refusals: a paired schedule changes only by proposal --------------

    @Test
    fun `a change while the co-parent's proposal waits is refused, and nothing is written`() =
        runTest(dispatcher) {
            // The old fallback activated the pattern locally and pushed it over the document:
            // the agreed schedule and the co-parent's pending proposal were both gone.
            val theirs = CustodyProposal(
                model = localModel().copy(id = "their-proposal"),
                repeatYearly = true,
                proposedBy = PARTNER_UID,
                proposedAt = "2026-08-05T10:00:00"
            )
            coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } returns
                remoteCustody().copy(proposal = theirs)

            val result = repository.createWeekOnWeekOff(START_DATE)

            assertEquals(PatternSubmission.COPARENT_PROPOSAL_WAITING, result)
            coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
            coVerify(exactly = 0) { custodyModelDao.insertModel(any()) }
            coVerify(exactly = 0) { custodyModelDao.deactivateFamily(any()) }
        }

    @Test
    fun `a proposal that cannot be written is not sent, and nothing is activated instead`() =
        runTest(dispatcher) {
            coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } returns remoteCustody()
            coEvery { firestoreCustodyDataSource.setCustody(any(), any(), any()) } throws permissionDenied()

            val result = repository.createWeekOnWeekOff(START_DATE)

            assertEquals(PatternSubmission.NOT_SENT, result)
            // One write — the proposal — and no second one pushing the pattern over the document.
            coVerify(exactly = 1) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
            coVerify(exactly = 0) { custodyModelDao.insertModel(any()) }
        }

    @Test
    fun `a pair whose document cannot be read gets no proposal and no overwrite`() =
        runTest(dispatcher) {
            // Unguarded, this read threw out of the caller's coroutine; and a failed read is not
            // an absent document, so it must not be answered by activating the pattern.
            coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } throws permissionDenied()

            val result = repository.createWeekOnWeekOff(START_DATE)

            assertEquals(PatternSubmission.NOT_SENT, result)
            coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }
            coVerify(exactly = 0) { custodyModelDao.insertModel(any()) }
        }

    @Test
    fun `an unpaired parent's pattern still applies at once`() = runTest(dispatcher) {
        pairedWith(partnerUid = null)

        val result = repository.createWeekOnWeekOff(START_DATE)

        assertEquals(PatternSubmission.ACTIVATED, result)
        coVerify(exactly = 1) { custodyModelDao.insertModel(any()) }
    }

    @Test
    fun `a pair's first schedule, with no document yet, applies at once`() = runTest(dispatcher) {
        val result = repository.createWeekOnWeekOff(START_DATE)

        assertEquals(PatternSubmission.ACTIVATED, result)
        coVerify(exactly = 1) { custodyModelDao.insertModel(any()) }
        coVerify(exactly = 1) { firestoreCustodyDataSource.setCustody(DOCUMENT_ID, any(), any()) }
    }

    // ---- parenting-plan citation (MON-21) -----------------------------------

    @Test
    fun `a pattern built from the plan goes as a proposal that cites it, pattern untouched`() =
        runTest(dispatcher) {
            coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } returns remoteCustody()
            val custody = slot<SharedCustody>()
            coEvery { firestoreCustodyDataSource.setCustody(any(), any(), capture(custody)) } returns Unit

            val result = repository.createWeekOnWeekOff(START_DATE, planCitation = PLAN_CITATION)

            assertEquals(PatternSubmission.PROPOSED, result)
            assertEquals(PLAN_CITATION, custody.captured.proposal?.planCitationWire)
            // A proposal, never an overwrite: the agreed pattern on the document is unchanged.
            assertEquals(remoteCustody().model, custody.captured.model)
        }

    @Test
    fun `seasonal layers from the plan cite it too, and a plain save cites nothing`() =
        runTest(dispatcher) {
            activeRows(mirroredEntity())
            coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } returns remoteCustody()
            val custody = slot<SharedCustody>()
            coEvery { firestoreCustodyDataSource.setCustody(any(), any(), capture(custody)) } returns Unit

            repository.submitSeasonalLayers(listOf(SUMMER), PLAN_CITATION)
            assertEquals(PLAN_CITATION, custody.captured.proposal?.planCitationWire)

            repository.submitSeasonalLayers(listOf(SUMMER))
            assertNull(custody.captured.proposal?.planCitationWire)
        }

    @Test
    fun `the proposal's chat card carries the citation, so the export can still name it later`() =
        runTest(dispatcher) {
            coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } returns remoteCustody()

            repository.createWeekOnWeekOff(START_DATE, planCitation = PLAN_CITATION)

            coVerify(exactly = 1) {
                activityAnnouncer.announce(
                    match { it.kind == ActivityKind.CUSTODY_PROPOSED && it.planCitation == PLAN_CITATION },
                    any(),
                    any()
                )
            }
        }

    @Test
    fun `a proposal built without the plan announces no citation`() = runTest(dispatcher) {
        coEvery { firestoreCustodyDataSource.getCustody(DOCUMENT_ID) } returns remoteCustody()

        repository.createWeekOnWeekOff(START_DATE)

        coVerify(exactly = 1) {
            activityAnnouncer.announce(
                match { it.kind == ActivityKind.CUSTODY_PROPOSED && it.planCitation == null },
                any(),
                any()
            )
        }
    }

    // ---- fixtures ---------------------------------------------------------

    /** Points both the one-shot and the streaming uid lookups at [partnerUid]. */
    private fun signedInWith(partnerUid: String?) = User(
        id = MY_UID,
        email = "mom@example.com",
        name = "Mom",
        role = "mom",
        colorCode = "#FF4081",
        partnerId = partnerUid
    )

    private fun pairedWith(partnerUid: String?, partnerIds: List<String> = emptyList()) {
        val me = signedInWith(partnerUid).copy(partnerIds = partnerIds)
        coEvery { userRepository.getCurrentUserId() } returns MY_UID
        coEvery { userRepository.getUserById(MY_UID) } returns me
        every { userRepository.observeCurrentUserId() } returns flowOf(MY_UID)
        every { userRepository.getAllUsers() } returns flowOf(listOf(me))
    }

    /** uidA in two families, uidB's and uidC's, with [onScreen]'s family on screen (M-8). */
    private fun inTwoFamilies(onScreen: String) = pairedWith(onScreen, listOf(PARTNER_UID, OTHER_PARTNER_UID))

    /** The uidA–uidB family's schedule as this device stores it: stamped with the family. */
    private fun bobFamilyRow(lastModifiedAtMillis: Long = REMOTE_MODIFIED_AT) =
        mirroredEntity().copy(familyId = DOCUMENT_ID, lastModifiedAtMillis = lastModifiedAtMillis)

    /** The uidA–uidC family's schedule: another pattern, under another id. */
    private fun carolFamilyRow(lastModifiedAtMillis: Long = RECENTLY) = mirroredEntity().copy(
        id = CAROL_MODEL_ID,
        momDaysPattern = "[7,8,9,10,11,12,13]",
        familyId = OTHER_DOCUMENT_ID,
        lastModifiedAtMillis = lastModifiedAtMillis
    )

    /** Room's active rows, for both the one-shot and the streaming read. */
    private fun activeRows(vararg rows: CustodyModelEntity) {
        coEvery { custodyModelDao.getActiveModelsSync() } returns rows.toList()
        every { custodyModelDao.observeActiveModels() } returns flowOf(rows.toList())
    }

    private fun localModel() = CustodyModel(
        id = LOCAL_MODEL_ID,
        modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = START_DATE
    )

    private fun localEntity(lastModifiedAtMillis: Long) = mirroredEntity().copy(
        id = LOCAL_MODEL_ID,
        lastModifiedAtMillis = lastModifiedAtMillis
    )

    private fun remoteCustody(lastModifiedAtMillis: Long = REMOTE_MODIFIED_AT) = SharedCustody(
        model = localModel().copy(id = REMOTE_MODEL_ID),
        lastModifiedBy = PARTNER_UID,
        lastModifiedAtMillis = lastModifiedAtMillis,
        createdAt = REMOTE_CREATED_AT
    )

    /** The row [remoteCustody] mirrors to, for the echo-guard case. */
    private fun mirroredEntity() = CustodyModelEntity(
        id = REMOTE_MODEL_ID,
        modelType = "week_on_week_off",
        patternDays = 14,
        momDaysPattern = "[0,1,2,3,4,5,6]",
        startDate = START_DATE.toString(),
        isActive = true,
        repeatYearly = true,
        createdAt = REMOTE_CREATED_AT,
        lastModifiedAtMillis = REMOTE_MODIFIED_AT
    )

    private fun permissionDenied() = FirebaseFirestoreException(
        "Missing or insufficient permissions.",
        FirebaseFirestoreException.Code.PERMISSION_DENIED
    )

    private companion object {
        /** A parenting-plan citation as `PlanCitationCodec` writes it (MON-21). */
        const val PLAN_CITATION = "p1|care_weekday|0123456789abcdef"

        /** July with slot 1 (MON-14). */
        val SUMMER = SeasonalLayer.allWith(
            "summer-26",
            "Summer",
            LocalDate.of(2026, 7, 1)..LocalDate.of(2026, 7, 31),
            "mom"
        )
        const val SUMMER_WIRE = "L1;summer-26;0;2026-07-01;2026-07-31;2026-07-01;1;0;;Summer"

        /** A layer from a newer build, which this one must carry without reading. */
        const val FUTURE = "L2;written-by-a-newer-build"

        /** A child who is with slot 1 every day (FAM-4). */
        const val BABY_WIRE = "C1;child:baby-1;2026-09-07;1;0;"

        /** Alternate weeks for another child, starting with slot 2. */
        const val TEEN_WIRE = "C1;child:teen-1;2026-09-07;14;7,8,9,10,11,12,13;"

        /** An override entry from a newer build, carried verbatim. */
        const val CHILD_FUTURE = "C2;written-by-a-newer-build"

        const val MY_UID = "uidA"
        const val PARTNER_UID = "uidB"

        /** Sorts *before* [MY_UID], so an unsorted write is visible rather than coincidental. */
        const val EARLIER_PARTNER_UID = "uid0"

        /** What `CustodyKey.of("uidA", "uidB")` derives — pinned as a literal on purpose. */
        const val DOCUMENT_ID = "uidA__uidB"

        /** uidA's second co-parent, and their family's document. */
        const val OTHER_PARTNER_UID = "uidC"
        const val OTHER_DOCUMENT_ID = "uidA__uidC"

        /** The id of the uidA–uidC family's pattern. */
        const val CAROL_MODEL_ID = "carol-model-1"
        val PARTICIPANTS = listOf("uidA", "uidB")

        const val LOCAL_MODEL_ID = "local-model-1"
        const val REMOTE_MODEL_ID = "remote-model-1"
        const val REMOTE_CREATED_AT = "2026-07-01T09:00:00"
        val REMOTE_MODIFIED_AT = CustodyTimestamp.fromWire("2026-08-04T18:30:00")

        /** Distinct from [REMOTE_CREATED_AT], so "kept the row's dates" cannot pass by accident. */
        const val PRE_EXISTING_CREATED_AT = "2026-06-15T07:45:00"

        /**
         * Ordering fixtures for the staleness guard. Absolute rather than relative to `now()`, so
         * what is asserted is the comparison under test and not the machine's clock.
         */
        val LONG_AGO = CustodyTimestamp.fromWire("2020-01-01T00:00:00")
        val RECENTLY = CustodyTimestamp.fromWire("2099-01-01T00:00:00")

        /** `RETRY_BASE_MS shl 0`, the repository's first backoff step. */
        const val FIRST_BACKOFF_MS = 1_000L

        val START_DATE: LocalDate = LocalDate.of(2026, 8, 3)

        /** Wednesday of the first week (day 0 is Monday 3 August), 15:00-19:00 with slot 2. */
        val WEDNESDAY_WINDOW = ContactWindow(2, LocalTime.of(15, 0), LocalTime.of(19, 0), "dad")
    }
}
