package com.coparently.app.screenshots

import androidx.compose.runtime.Composable
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.changerequests.TimedRequest
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DaySwap
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.presentation.calendar.swapMiniMonths
import com.coparently.app.presentation.changerequests.InboxAnswers
import com.coparently.app.presentation.changerequests.InboxItem
import com.coparently.app.presentation.home.HomeRequestItems
import com.coparently.app.presentation.home.HomeRequestsContext
import com.coparently.app.presentation.home.RequestsBlockState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Home's Requests block in the fixtures' May 2026 (November 2026, board HomeG1): Pavel offers Olya
 * a Saturday of his and asks to move Leo's dentist, both with a deadline; Olya's own split waits on
 * him. The schedule agreed now is the fixtures' week on, week off ([ScreenshotFixtures.custodyFor]);
 * Pavel's proposal is 2-2-3 from Monday 18 May.
 */
internal object RequestFixtures {

    /** 13 May 2026, 12:00 UTC: the clocks' "now", the fixtures' today in every zone CI runs in. */
    private const val NOW_MILLIS = 1_778_673_600_000L

    /** 15 May 2026, 16:00 UTC: when Pavel's two requests want an answer by. */
    private const val ANSWER_BY_MILLIS = 1_778_860_800_000L

    /** Saturday 23 May 2026: one of Pavel's days, which he offers Olya. */
    val SWAP_DAY = LocalDate(2026, 5, 23)

    /** The week on, week off agreed now: Olya's from Monday 11 May (ISO week 20, an even one). */
    val agreed = CustodyModel(
        id = "agreed",
        modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = LocalDate(2026, 5, 11)
    )

    /** Pavel's 2-2-3 from Monday 18 May: Olya has Monday, Tuesday and the weekend, then the rest. */
    val proposal = CustodyProposal(
        model = CustodyModel(
            id = "proposed",
            modelType = CustodyModelType.TWO_TWO_THREE,
            patternDays = 14,
            momDayIndices = setOf(0, 1, 4, 5, 6, 9, 10),
            startDate = LocalDate(2026, 5, 18)
        ),
        repeatYearly = true,
        proposedBy = ScreenshotFixtures.CO_PARENT_UID,
        proposedAt = "2026-05-12T10:00:00"
    )

    private val custody = SharedCustody(
        model = agreed,
        lastModifiedBy = ScreenshotFixtures.MY_UID,
        lastModifiedAtMillis = 1L,
        createdAt = "2026-05-01T09:00:00",
        proposal = proposal
    )

    val swap = DaySwapGroup(
        listOf(
            DaySwap(
                date = SWAP_DAY,
                override = DayOverride(
                    toParent = "mom",
                    requestedBy = ScreenshotFixtures.CO_PARENT_UID,
                    requestedAt = "2026-05-12T09:00:00",
                    note = "My sister's wedding, I'll take Sunday the 31st instead.",
                    groupId = "g1"
                )
            )
        )
    )

    private val change = ChangeRequest(
        id = "cr1",
        eventId = "e1",
        eventTitle = "Dentist — Leo",
        requestedBy = ScreenshotFixtures.CO_PARENT_UID,
        requestedTo = ScreenshotFixtures.MY_UID,
        currentStartDateTime = LocalDateTime(2026, 5, 15, 10, 0),
        proposedStartDateTime = LocalDateTime(2026, 5, 15, 14, 30),
        createdAt = LocalDateTime(2026, 5, 12, 9, 30),
        syncedToFirestore = true
    )

    private val split = SplitRatioProposal(SplitRatio(6_000), ScreenshotFixtures.MY_UID, 1L)

    private fun clock(kind: RequestKind, ref: String) = TimedRequest(
        clock = RequestDeadline(
            id = "${kind.wire}_${ScreenshotFixtures.CO_PARENT_UID}_$ref",
            kind = kind,
            ref = ref,
            familyId = "family",
            proposerUid = ScreenshotFixtures.CO_PARENT_UID,
            responderUid = ScreenshotFixtures.MY_UID,
            answerByMillis = ANSWER_BY_MILLIS
        ),
        nowMillis = NOW_MILLIS
    )

    val swapItem = InboxItem.Swap(swap, clock(RequestKind.SWAP, "g1"))
    val changeItem = InboxItem.Request(change, clock(RequestKind.CHANGE, "cr1"))
    val proposalItem = InboxItem.Proposal(proposal, mine = false, timed = clock(RequestKind.PROPOSAL, "p1"))
    private val splitItem = InboxItem.Split(split, mine = true)

    /** Two action cards, and Olya's split waiting on Pavel. */
    val block = RequestsBlockState(
        items = HomeRequestItems(forMe = listOf(swapItem, changeItem), forThem = listOf(splitItem)),
        forYou = 2,
        newCount = 3
    )

    /** What the cards are drawn from, with answers that do nothing. */
    val context: HomeRequestsContext
        @Composable get() = HomeRequestsContext(
            scope = RequestScope(
                myUid = ScreenshotFixtures.MY_UID,
                coParentUid = ScreenshotFixtures.CO_PARENT_UID,
                familyId = "family",
                soleFamily = true
            ),
            parentNames = ScreenshotFixtures.parentNames,
            miniMonths = emptyMap(),
            swapMonths = mapOf(
                swap.key to swapMiniMonths(
                    mapOf(SWAP_DAY to "mom"),
                    ScreenshotFixtures.monthEvents.values.flatten(),
                    ScreenshotFixtures::custodyOn
                )
            ),
            proposalDiff = null,
            proposalCitation = CitationStatus.None,
            agreedSplit = SplitRatio(5_000),
            custody = custody,
            lastChanceInFlight = emptySet(),
            answers = InboxAnswers.inert(),
            onDetails = {},
            onOpenAgreement = {},
            onWithdrawProfessional = {}
        )
}
