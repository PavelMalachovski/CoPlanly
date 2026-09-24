package com.coparently.app.e2e

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.custody.CustodyDecisionOutcome
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DayOverrideTransition
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.e2e.EmulatorEnvironment.step
import com.coparently.app.presentation.navigation.BottomNavDestination
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * What Alice **sees** when Bob asks her something, and what her answer on screen does on his
 * phone: an event change request, a new custody pattern, a one-off day swap and a seasonal layer.
 *
 * `TwoParentRequestsAndEventPushesTest` and `TwoParentCustodyTest` prove the mechanism between two
 * data layers; this runs Alice's real app ([AliceOnScreenTest]) so the surfaces that draw it are
 * exercised too — the calendar's inline banner (`CalendarBanners.ChangeRequestBanner`), the
 * change-request inbox, Home's pop-ups (`AwaitingDialogs`) and the month grid's day cell. Every
 * answer is a tap, and Bob's repositories are what confirm it landed.
 *
 * What a phone still adds (`docs/DEVICE-CHECKLIST.md` §3.11, §5.3): two screens at once, the push
 * that tells Alice to look, motion, and the colours of the band — the day cell is asserted by its
 * accessibility description, which names the parent, not by its pixels.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnScreenAgreementsTest : AliceOnScreenTest() {

    @Inject
    lateinit var eventRepository: EventRepository

    @Inject
    lateinit var custodyRepository: CustodyModelRepository

    @Test
    fun bobsChangeRequestRaisesTheCalendarBannerAndAliceAcceptsItInTheInbox() {
        val title = "Swimming ${shortId()}"
        val event = runBlocking { aliceSavesAnEvent(title) }

        // On the calendar before it arrives, so the banner is what she sees — on Home the same
        // request would open the requests pop-up instead.
        step("request: open the calendar")
        openTab(BottomNavDestination.CALENDAR)
        val request = runBlocking { bobAsksToMove(event) }

        val banner = hasText(
            context.resources.getQuantityString(R.plurals.calendar_change_requests_banner, 1, 1)
        ) and hasClickAction()
        waitFor("the change-request banner over the grid", banner)
        tap(banner)

        // The inbox, opened from the banner: the request's card and its Accept.
        val accept = hasText(string(R.string.change_request_accept)) and hasClickAction() and
            hasAnyAncestor(hasText(title, substring = true))
        waitFor("Bob's request in the inbox", accept)
        tap(accept)

        step("request: Bob sees it accepted and the event moved")
        runBlocking {
            awaitRequestOnBobsPhone(request.id, ChangeRequestStatus.ACCEPTED)
            withTimeout(EmulatorParent.WAIT_MS) {
                while (bob.eventRepository.fetchRemoteEvent(event.id)?.startDateTime != request.proposedStartDateTime) {
                    delay(POLL_MS)
                }
            }
        }
    }

    @Test
    fun bobsCustodyProposalPopsUpOnAlicesHomeAndHerAnswersReachHim() {
        runBlocking { bobSetsTheFirstPattern(LocalDate.now(), aliceHasTheFirstWeek = true) }

        step("proposal: Bob proposes a 2-2-3")
        runBlocking {
            assertEquals(
                PatternSubmission.PROPOSED,
                bob.custodyRepository.createTwoTwoThree(LocalDate.now(), momStartsFirst = true)
            )
        }
        waitFor("Bob's proposal on Alice's Home, naming him", proposalDialogNaming(bob.name))
        tap(dialogButton(R.string.custody_proposal_accept))

        step("proposal: both phones hold the 2-2-3")
        runBlocking {
            awaitShared(bob) { it.proposal == null && it.lastDecision?.outcome == CustodyDecisionOutcome.ACCEPTED }
            awaitPattern(bob.custodyRepository) { it.modelType == CustodyModelType.TWO_TWO_THREE }
            awaitPattern(custodyRepository) { it.modelType == CustodyModelType.TWO_TWO_THREE }
        }

        step("proposal: Bob proposes again, Alice declines")
        runBlocking {
            assertEquals(
                PatternSubmission.PROPOSED,
                bob.custodyRepository.createWeekOnWeekOff(LocalDate.now(), momFirst = false)
            )
        }
        waitFor("Bob's second proposal on Alice's Home", proposalDialogNaming(bob.name))
        tap(dialogButton(R.string.custody_proposal_decline))
        runBlocking {
            awaitShared(bob) { it.proposal == null && it.lastDecision?.outcome == CustodyDecisionOutcome.DECLINED }
            // The pattern Alice accepted stays in force on both phones.
            awaitPattern(bob.custodyRepository) { it.modelType == CustodyModelType.TWO_TWO_THREE }
            awaitPattern(custodyRepository) { it.modelType == CustodyModelType.TWO_TWO_THREE }
        }
    }

    @Test
    fun bobsDaySwapPopsUpOnAlicesHomeAndHerAnswersReachHim() {
        val agreed = runBlocking { bobSetsTheFirstPattern(LocalDate.now(), aliceHasTheFirstWeek = true) }
        val firstDay = LocalDate.now().plusDays(FIRST_SWAP_OFFSET)
        val secondDay = LocalDate.now().plusDays(SECOND_SWAP_OFFSET)

        step("swap: Bob offers a day, Alice accepts on Home")
        runBlocking { bobOffers(agreed, firstDay) }
        waitFor("Bob's swap on Alice's Home", swapDialogFor(firstDay))
        tap(dialogButton(R.string.day_swap_accept))
        runBlocking {
            awaitOverride(firstDay) { it.isAccepted && it.decidedBy == aliceUid }
        }

        step("swap: Bob offers another, Alice declines on Home")
        runBlocking { bobOffers(agreed, secondDay) }
        waitFor("Bob's second swap on Alice's Home", swapDialogFor(secondDay))
        tap(dialogButton(R.string.day_swap_decline))
        runBlocking {
            awaitOverride(secondDay) { it.status == DayOverrideStatus.DECLINED && it.decidedBy == aliceUid }
            // The earlier agreement is untouched by the later refusal.
            awaitOverride(firstDay) { it.isAccepted }
        }
    }

    @Test
    fun aSeasonalLayerAliceAcceptsNamesBobOnTodaysCell() {
        val today = LocalDate.now()
        val (aliceSlot, bobSlot) = runBlocking { slots() }
        val base = runBlocking { bobSetsTheFirstPattern(today, aliceHasTheFirstWeek = true) }
        assertEquals("the base pattern gives today to Alice", aliceSlot, base.getCustodyFor(today))

        step("layer: Bob proposes a one-day layer with himself")
        runBlocking {
            val layer = SeasonalLayer.allWith(LAYER_ID, LAYER_NAME, today..today, bobSlot)
            assertEquals(PatternSubmission.PROPOSED, bob.custodyRepository.submitSeasonalLayers(listOf(layer)))
        }
        waitFor("Bob's layer proposal on Alice's Home", proposalDialogNaming(bob.name))
        tap(dialogButton(R.string.custody_proposal_accept))
        runBlocking {
            awaitShared(bob) { it.proposal == null && it.lastDecision?.outcome == CustodyDecisionOutcome.ACCEPTED }
            awaitPattern(custodyRepository) { it.seasonalLayers.any { layer -> layer.id == LAYER_ID } }
        }

        // The grid draws a layer only through the custody band; its description names the parent.
        step("layer: today's cell on the month grid")
        openTab(BottomNavDestination.CALENDAR)
        val todayCell = hasContentDescription(
            "${today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))}, " +
                string(R.string.calendar_day_desc_today),
            substring = true
        )
        val withBob = hasContentDescription(string(R.string.calendar_day_desc_with_parent, bob.name), substring = true)
        waitFor("today's cell naming Bob", todayCell and withBob)
    }

    // ---- the change request ---------------------------------------------------------------------

    /** An event of Alice's for tomorrow, saved through her app's own repository (and so uploaded). */
    private suspend fun aliceSavesAnEvent(title: String): Event {
        val start = LocalDateTime.now().plusDays(1).withHour(START_HOUR).withMinute(0).withSecond(0).withNano(0)
        val event = Event(
            id = UUID.randomUUID().toString(),
            title = title,
            startDateTime = start,
            endDateTime = start.plusHours(1),
            eventType = "appointment",
            parentOwner = checkNotNull(userDao.getUserById(aliceUid)).role,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        eventRepository.insertEvent(event)
        return checkNotNull(eventRepository.getEventById(event.id))
    }

    /** Bob asks to move [event] two hours later, as `RequestChangeViewModel` builds a request. */
    private suspend fun bobAsksToMove(event: Event): ChangeRequest {
        val request = ChangeRequest(
            id = UUID.randomUUID().toString(),
            eventId = event.id,
            eventTitle = event.title,
            requestedBy = bob.uid,
            requestedTo = aliceUid,
            currentStartDateTime = event.startDateTime,
            currentEndDateTime = event.endDateTime,
            proposedStartDateTime = event.startDateTime.plusHours(2),
            proposedEndDateTime = event.endDateTime?.plusHours(2),
            note = "Could we move it?",
            createdAt = LocalDateTime.now()
        )
        bob.changeRequestRepository.createChangeRequest(request)
        return request
    }

    /** Runs Bob's change-request listener until [requestId] is in his Room at [status]. */
    private suspend fun awaitRequestOnBobsPhone(requestId: String, status: ChangeRequestStatus) = coroutineScope {
        val listener = launch { bob.changeRequestRepository.observeRemote() }
        try {
            withTimeout(EmulatorParent.WAIT_MS) {
                bob.changeRequestRepository.getAllChangeRequests()
                    .first { list -> list.any { it.id == requestId && it.status == status } }
            }
        } finally {
            listener.cancel()
        }
    }

    // ---- custody --------------------------------------------------------------------------------

    /**
     * Alice's slot and Bob's, as the server assigned them at pairing (`assignSlots` writes each
     * `users/{uid}.role`) — the copy Alice's app reads for the co-parent. Not Bob's Room row:
     * on a phone the accepter's row is re-stamped by `PairingViewModel.withSlotReslot`, a screen
     * Bob's data-layer phone never runs, so there it keeps the default slot.
     */
    private suspend fun slots(): Pair<String, String> {
        val aliceSlot = checkNotNull(bob.userRepository.getRemoteUserProfile(aliceUid)).role
        val bobSlot = checkNotNull(bob.userRepository.getRemoteUserProfile(bob.uid)).role
        assertNotEquals("the pair still shares one slot", aliceSlot, bobSlot)
        return aliceSlot to bobSlot
    }

    /**
     * The pair's first schedule, activated on Bob's phone (there is no agreed pattern to protect
     * yet) and mirrored into Alice's Room: week on, week off from [anchor], the first week with
     * Alice when [aliceHasTheFirstWeek].
     */
    private suspend fun bobSetsTheFirstPattern(anchor: LocalDate, aliceHasTheFirstWeek: Boolean): CustodyModel {
        step("custody: Bob sets the first pattern")
        val (aliceSlot, _) = slots()
        val momFirst = (aliceSlot == SLOT_ONE) == aliceHasTheFirstWeek
        assertEquals(PatternSubmission.ACTIVATED, bob.custodyRepository.createWeekOnWeekOff(anchor, momFirst))
        val model = checkNotNull(bob.custodyRepository.getActiveModelSync())
        awaitPattern(custodyRepository) { it.modelType == model.modelType && it.startDate == model.startDate }
        return model
    }

    /** Bob offers [date] to whoever the agreed pattern does not give it to, as the calendar does. */
    private suspend fun bobOffers(agreed: CustodyModel, date: LocalDate) {
        val iso = date.toString()
        val toSlot = if (agreed.getCustodyFor(date) == SLOT_ONE) SLOT_TWO else SLOT_ONE
        bob.custodyRepository.applyDayOverrides(iso) { current ->
            DayOverrideTransition.offer(current, iso, toSlot, bob.uid, LocalDateTime.now().toString())
        }.getOrThrow()
    }

    /** The pair's document as [parent]'s listener delivers it, once [condition] holds. */
    private suspend fun awaitShared(parent: EmulatorParent, condition: (SharedCustody) -> Boolean): SharedCustody =
        withTimeout(EmulatorParent.WAIT_MS) {
            checkNotNull(parent.custodyRepository.observeShared().first { it != null && condition(it) })
        }

    /** The calendar pattern [repository] holds — Room, fed by its mirror — once [condition] holds. */
    private suspend fun awaitPattern(repository: CustodyModelRepository, condition: (CustodyModel) -> Boolean) =
        withTimeout(EmulatorParent.WAIT_MS) {
            checkNotNull(repository.getActiveModel().first { it != null && condition(it) })
        }

    /** The swap on [date] as Bob's Room holds it, once [condition] holds. */
    private suspend fun awaitOverride(date: LocalDate, condition: (DayOverride) -> Boolean): DayOverride =
        withTimeout(EmulatorParent.WAIT_MS) {
            bob.custodyRepository.observeDayOverrides()
                .first { overrides -> overrides[date.toString()]?.let(condition) == true }
                .getValue(date.toString())
        }

    // ---- what Home draws ------------------------------------------------------------------------

    /** Home's proposal pop-up, whose body names the parent who proposed it. */
    private fun proposalDialogNaming(name: String): SemanticsMatcher =
        hasAnyAncestor(isDialog()) and hasText(string(R.string.custody_proposal_inbox_body, name), substring = true)

    /** Home's swap pop-up for one day offered by Bob, worded and dated as `AwaitingDialogs` words it. */
    private fun swapDialogFor(date: LocalDate): SemanticsMatcher = hasAnyAncestor(isDialog()) and hasText(
        string(
            R.string.home_dialog_swap_message,
            bob.name,
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        )
    )

    private companion object {
        const val START_HOUR = 10
        const val POLL_MS = 250L

        /** Both inside the agreed pattern's first fortnight, and after today so the inbox shows them. */
        const val FIRST_SWAP_OFFSET = 2L
        const val SECOND_SWAP_OFFSET = 9L

        const val SLOT_ONE = "mom"
        const val SLOT_TWO = "dad"
        const val LAYER_ID = "e2e-visit"
        const val LAYER_NAME = "Visit"
    }
}
