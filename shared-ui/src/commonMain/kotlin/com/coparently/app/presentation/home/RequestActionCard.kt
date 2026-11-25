// Home's request cards (November 2026, board HomeG1): the context they are drawn from, the action
// card, the "Waiting for {co-parent}" row, and the pieces the Details sheet shares with them.
@file:Suppress("TooManyFunctions")
@file:OptIn(ExperimentalLayoutApi::class)

package com.coparently.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.changerequests.ClockState
import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.presentation.changerequests.AgreementScreen
import com.coparently.app.presentation.changerequests.CardClock
import com.coparently.app.presentation.changerequests.CardClockDetails
import com.coparently.app.presentation.changerequests.ClockRole
import com.coparently.app.presentation.changerequests.InboxAnswers
import com.coparently.app.presentation.changerequests.InboxItem
import com.coparently.app.presentation.changerequests.LocalCardClock
import com.coparently.app.presentation.changerequests.PlanRequestRow
import com.coparently.app.presentation.changerequests.ProfessionalRequestRow
import com.coparently.app.presentation.changerequests.SupportRequestRow
import com.coparently.app.presentation.changerequests.SwapNoteQuote
import com.coparently.app.presentation.changerequests.SwapOfferSentence
import com.coparently.app.presentation.changerequests.SwapOfferSides
import com.coparently.app.presentation.changerequests.SwapProposedByLine
import com.coparently.app.presentation.changerequests.daySwapSentence
import com.coparently.app.presentation.changerequests.eventWhenText
import com.coparently.app.presentation.changerequests.isMadeBy
import com.coparently.app.presentation.changerequests.ownSwapTitle
import com.coparently.app.presentation.changerequests.sidesAsked
import com.coparently.app.presentation.changerequests.splitSharesText
import com.coparently.app.presentation.changerequests.swapAcceptLabel
import com.coparently.app.presentation.changerequests.swapHeadingNamesChildren
import com.coparently.app.presentation.changerequests.swapOfferHeading
import com.coparently.app.presentation.common.AnswerButtons
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.WhenScreenVisible
import com.coparently.app.presentation.custody.custodyDiffDescription
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.bodyMediumEmphasized
import com.coparently.app.presentation.theme.titleMediumEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.answer_by_fixed
import com.coparently.app.resources.change_request_accept
import com.coparently.app.resources.change_request_decline
import com.coparently.app.resources.change_request_withdraw
import com.coparently.app.resources.custody_proposal_accept
import com.coparently.app.resources.custody_proposal_decline
import com.coparently.app.resources.custody_proposal_inbox_body
import com.coparently.app.resources.custody_proposal_inbox_body_yours
import com.coparently.app.resources.custody_proposal_inbox_title
import com.coparently.app.resources.day_swap_decline
import com.coparently.app.resources.event_acceptance_accept
import com.coparently.app.resources.event_acceptance_decline
import com.coparently.app.resources.event_acceptance_offered_to_you
import com.coparently.app.resources.home_request_change_title
import com.coparently.app.resources.home_request_details
import com.coparently.app.resources.split_request_body_theirs
import com.coparently.app.resources.split_request_now
import com.coparently.app.resources.split_request_proposed
import com.coparently.app.resources.split_request_title
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.unbreakableDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One request waiting on this parent, as an action card: "Waiting for you" with its deadline, an
 * icon by kind, the inbox's own words for it, the proposer's note, then Details over Decline and
 * Accept. The answers are the inbox's ([InboxAnswers]), refused there when the family on screen
 * changed meanwhile. Child support, a professional's access and the plan draw the inbox's compact
 * rows instead, opening their own screens.
 *
 * A request of this parent's own waits here only past its deadline, while its last chance is theirs
 * to give (phase 3a): its card then carries the clock's rows and button, and Withdraw.
 */
@Composable
internal fun RequestActionCard(item: InboxItem, ctx: HomeRequestsContext) {
    CompositionLocalProvider(LocalCardClock provides ctx.clockFor(item)) {
        when (item) {
            is InboxItem.Support, is InboxItem.Professional, is InboxItem.Plan -> AgreementRow(item, ctx)
            else -> ActionCardBody(item, ctx)
        }
    }
}

@Composable
private fun ActionCardBody(item: InboxItem, ctx: HomeRequestsContext) {
    val mine = item.isMadeBy(ctx.scope.myUid)
    SectionGroup {
        Column(
            modifier = Modifier.padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            StatusLine(waitingForYou = true, coParentName = ctx.coParentName)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                KindDisc(icon = kindIcon(item), outlined = false)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.XS)
                ) {
                    Text(text = requestTitle(item, ctx), style = MaterialTheme.typography.titleMediumEmphasized)
                    RequestSummary(item, ctx)
                    RequestNote(item, ctx)
                }
            }
            if (mine) {
                // Past its deadline: the proposer's rows and the last-chance button, as the inbox shows.
                CardClockDetails()
                if (waitingRowAction(item, ctx.scope.myUid) == WaitingRowAction.WITHDRAW) {
                    TextButton(onClick = { withdraw(item, ctx) }) {
                        Text(stringResource(Res.string.change_request_withdraw))
                    }
                }
            } else {
                DetailsAndAnswers(item, ctx)
            }
        }
    }
}

/**
 * Details on a line of its own, then Decline and Accept at the card's full width. Details used to sit
 * beside the two answers, which left each a third of the card: in Russian "Отклонить" and "Принять"
 * broke inside the word. The answers stack from font scale 1.3 ([AnswerButtons]).
 */
@Composable
private fun DetailsAndAnswers(item: InboxItem, ctx: HomeRequestsContext) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        if (hasDetails(item)) DetailsButton { ctx.onDetails(item) }
        RequestAnswerButtons(item, ctx)
    }
}

/** "Details" with the calendar glyph: it opens the request on the reader's own month. */
@Composable
private fun DetailsButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        AppIcon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(IconSizes.Small)
        )
        Text(
            text = stringResource(Res.string.home_request_details),
            modifier = Modifier.padding(start = Spacing.XS)
        )
    }
}

/**
 * The status chip and, beside it, by when the answer is due; it wraps rather than squeezes the
 * deadline at large text. Records the answering parent's first look, once, as the inbox card does.
 */
@Composable
internal fun StatusLine(waitingForYou: Boolean, coParentName: String) {
    val clock = LocalCardClock.current
    clock?.onSeen?.let { seen -> WhenScreenVisible(clock.timed.clock.id) { seen() } }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        RequestStatusChip(waitingForYou = waitingForYou, coParentName = coParentName)
        clock?.let(::responderDeadlineMillis)?.let { millis ->
            Text(
                text = stringResource(Res.string.answer_by_fixed, unbreakableDate(ReaderDateText.answerBy(millis))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * By when the answering parent's answer is due: the deadline while it is open, a last chance's end
 * while one runs; null for the proposer's side and once the answer is no longer asked for.
 */
internal fun responderDeadlineMillis(clock: CardClock): Long? {
    if (clock.role != ClockRole.RESPONDER) return null
    return when (clock.timed.state) {
        ClockState.OPEN -> clock.timed.clock.answerByMillis
        ClockState.LAST_CHANCE -> RequestClock.endMillis(clock.timed.clock)
        ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE, ClockState.NO_ANSWER -> null
    }
}

/**
 * Decline and Accept for [item] in the inbox's own words, through the inbox's own answers; [after]
 * runs on either tap (the Details sheet closes). Past a custody or split proposal's deadline only
 * Decline is offered ([CardClock.declineOnly]); past any other request's, neither
 * ([CardClock.hidesAnswers]), because the next move is the proposer's.
 */
@Composable
internal fun RequestAnswerButtons(item: InboxItem, ctx: HomeRequestsContext, after: () -> Unit = {}) {
    val clock = LocalCardClock.current
    if (clock?.hidesAnswers == true) return
    val declineOnly = clock?.declineOnly == true
    val answer = answerOf(item, ctx) ?: return
    AnswerButtons(
        acceptLabel = stringResource(answer.acceptLabel),
        declineLabel = stringResource(answer.declineLabel),
        onAccept = {
            after()
            answer.onAnswer(true)
        }.takeUnless { declineOnly },
        onDecline = {
            after()
            answer.onAnswer(false)
        }
    )
}

/** One kind's two answers: their labels and what they do. */
private class Answer(
    val acceptLabel: StringResource,
    val declineLabel: StringResource,
    val onAnswer: (accept: Boolean) -> Unit
)

private fun answerOf(item: InboxItem, ctx: HomeRequestsContext): Answer? = when (item) {
    is InboxItem.Swap -> Answer(
        acceptLabel = swapAcceptLabel(item.group.waitingOn(ctx.scope.myUid)?.dayCount ?: 0),
        declineLabel = Res.string.day_swap_decline
    ) { accept -> ctx.answers.answerSwap(item.group, accept) }
    is InboxItem.Request -> Answer(Res.string.change_request_accept, Res.string.change_request_decline) { accept ->
        ctx.answers.answerRequest(item.request.id, accept)
    }
    is InboxItem.AwaitingEvent -> Answer(
        acceptLabel = Res.string.event_acceptance_accept,
        declineLabel = Res.string.event_acceptance_decline
    ) { yes -> ctx.answers.answerEvent(item.event.id, yes) }
    is InboxItem.Proposal -> Answer(Res.string.custody_proposal_accept, Res.string.custody_proposal_decline) { yes ->
        ctx.answers.answerProposal(yes)
    }
    is InboxItem.Split -> Answer(Res.string.change_request_accept, Res.string.change_request_decline) { accept ->
        ctx.answers.answerSplit(accept)
    }
    is InboxItem.Support, is InboxItem.Professional, is InboxItem.Plan,
    is InboxItem.Header, is InboxItem.History -> null
}

/**
 * One request waiting on the co-parent, as a compact row under "Waiting for {co-parent}": an
 * outlined disc by kind, the request in the inbox's words, and Withdraw for this parent's own — or,
 * for the co-parent's custody or split proposal past its deadline, Decline ([waitingRowAction]).
 * Child support and a professional's access draw the inbox's own rows.
 */
@Composable
internal fun WaitingForThemRow(item: InboxItem, ctx: HomeRequestsContext) {
    CompositionLocalProvider(LocalCardClock provides ctx.clockFor(item)) {
        when (item) {
            is InboxItem.Support, is InboxItem.Professional, is InboxItem.Plan -> AgreementRow(item, ctx)
            else -> WaitingRowBody(item, ctx)
        }
    }
}

@Composable
private fun WaitingRowBody(item: InboxItem, ctx: HomeRequestsContext) {
    val mine = item.isMadeBy(ctx.scope.myUid)
    SectionGroup {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.M),
            horizontalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            KindDisc(icon = kindIcon(item), outlined = true)
            // The summary takes the row's whole width and the action sits under it: beside the text,
            // Withdraw squeezed a split's shares into a column a word wide ("40 / %").
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
                Text(text = requestTitle(item, ctx), style = MaterialTheme.typography.bodyMediumEmphasized)
                if (mine) {
                    RequestSummary(item, ctx)
                } else {
                    // The co-parent's request past its deadline: where it stands, in the clock's words.
                    CardClockDetails()
                }
                WaitingRowActionButton(item, ctx, Modifier.align(Alignment.End))
            }
        }
    }
}

/** Withdraw or Decline under a waiting row's summary ([waitingRowAction]), or nothing. */
@Composable
private fun WaitingRowActionButton(item: InboxItem, ctx: HomeRequestsContext, modifier: Modifier) {
    when (waitingRowAction(item, ctx.scope.myUid)) {
        WaitingRowAction.WITHDRAW -> TextButton(onClick = { withdraw(item, ctx) }, modifier = modifier) {
            Text(stringResource(Res.string.change_request_withdraw))
        }
        WaitingRowAction.DECLINE ->
            TextButton(onClick = { answerOf(item, ctx)?.onAnswer?.invoke(false) }, modifier = modifier) {
                Text(stringResource(Res.string.change_request_decline))
            }
        WaitingRowAction.NONE -> Unit
    }
}

/** Takes this parent's own [item] back, through the inbox's own withdrawals. */
private fun withdraw(item: InboxItem, ctx: HomeRequestsContext) {
    when (item) {
        is InboxItem.Swap -> ctx.answers.withdrawSwap(item.group)
        is InboxItem.Request -> ctx.answers.withdrawRequest(item.request.id)
        is InboxItem.AwaitingEvent -> ctx.answers.withdrawEvent(item.event.id)
        is InboxItem.Proposal -> ctx.answers.withdrawProposal()
        is InboxItem.Split -> ctx.answers.withdrawSplit()
        is InboxItem.Support, is InboxItem.Professional, is InboxItem.Plan,
        is InboxItem.Header, is InboxItem.History -> Unit
    }
}

/** Child support, a professional's access or the plan: the inbox's compact row, opening its screen. */
@Composable
private fun AgreementRow(item: InboxItem, ctx: HomeRequestsContext) {
    when (item) {
        is InboxItem.Support -> SupportRequestRow(
            proposal = item.proposal,
            mine = item.mine,
            parentNames = ctx.parentNames,
            onOpen = { ctx.onOpenAgreement(AgreementScreen.SUPPORT) },
            onWithdraw = { ctx.answers.withdrawSupport(ctx.scope.familyId) }
        )
        is InboxItem.Professional -> ProfessionalRequestRow(
            grant = item.grant,
            mine = item.mine,
            parentNames = ctx.parentNames,
            onOpen = { ctx.onOpenAgreement(AgreementScreen.PROFESSIONALS) },
            onWithdraw = { ctx.onWithdrawProfessional(item.grant.id) }
        )
        is InboxItem.Plan -> PlanRequestRow(
            questionCount = item.questionCount,
            coParentName = ctx.coParentName,
            onOpen = { ctx.onOpenAgreement(AgreementScreen.PARENTING_PLAN) }
        )
        else -> Unit
    }
}

/** The icon a request leads with, by kind, on a tonal disc. */
private fun kindIcon(item: InboxItem): ImageVector = when (item) {
    is InboxItem.Swap -> Icons.Default.SwapHoriz
    is InboxItem.Request -> Icons.Default.Schedule
    is InboxItem.AwaitingEvent -> Icons.Default.Event
    is InboxItem.Proposal -> Icons.Default.CalendarMonth
    is InboxItem.Split -> Icons.Default.Payments
    is InboxItem.Support, is InboxItem.Professional, is InboxItem.Plan,
    is InboxItem.Header, is InboxItem.History -> Icons.Default.Event
}

/**
 * The disc behind a request's icon: the primary container for one waiting on this parent, an
 * outline for one waiting on the co-parent — the status language's filled and outlined. The row's
 * words say what it is, so the disc carries no description.
 */
@Composable
private fun KindDisc(icon: ImageVector, outlined: Boolean) {
    val colors = MaterialTheme.colorScheme
    val disc = if (outlined) {
        Modifier.border(DISC_OUTLINE, colors.outline, CircleShape)
    } else {
        Modifier.background(colors.primaryContainer, CircleShape)
    }
    Box(
        modifier = Modifier
            .size(KIND_DISC)
            .then(disc),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = null,
            tint = if (outlined) colors.onSurfaceVariant else colors.onPrimaryContainer,
            modifier = Modifier.size(IconSizes.Small)
        )
    }
}

/**
 * A request's title in the inbox's words: the co-parent's offer as the push heads it, the event a
 * new time is asked for, the event waiting, the proposal or the split — and this parent's own by
 * what they sent.
 */
@Composable
internal fun requestTitle(item: InboxItem, ctx: HomeRequestsContext): String {
    val names = ctx.parentNames
    val mine = item.isMadeBy(ctx.scope.myUid)
    return when (item) {
        is InboxItem.Swap -> if (mine) {
            ownSwapTitle(item.group)
        } else {
            val shown = item.group.waitingOn(ctx.scope.myUid) ?: item.group
            swapOfferHeading(
                shown.sidesAsked(names),
                names.labelForUid(item.group.swaps.first().override.requestedBy),
                ctx.swapChildren
            )
        }
        is InboxItem.Request -> if (mine) {
            dashKeptWithItsWord(item.request.eventTitle)
        } else {
            stringResource(
                Res.string.home_request_change_title,
                names.labelForUid(item.request.requestedBy),
                dashKeptWithItsWord(item.request.eventTitle)
            )
        }
        is InboxItem.AwaitingEvent -> dashKeptWithItsWord(item.event.title)
        is InboxItem.Proposal -> stringResource(Res.string.custody_proposal_inbox_title)
        is InboxItem.Split -> stringResource(Res.string.split_request_title)
        is InboxItem.Support, is InboxItem.Professional, is InboxItem.Plan,
        is InboxItem.Header, is InboxItem.History -> ""
    }
}

/**
 * [title] — a parent's own words — with the space before a dash made no-break, so a wrapped title
 * never starts a line with the dash ("«Dentist" over "— Leo»", text review, November 2026).
 */
internal fun dashKeptWithItsWord(title: String): String =
    title.replace(" \u2014", "\u00A0\u2014").replace(" \u2013", "\u00A0\u2013")

/** What a request asks, under its title, in the inbox's words. */
@Composable
internal fun RequestSummary(item: InboxItem, ctx: HomeRequestsContext) {
    val names = ctx.parentNames
    val mine = item.isMadeBy(ctx.scope.myUid)
    when (item) {
        is InboxItem.Swap -> if (mine) {
            SummaryText(daySwapSentence(item.group, offeredByMe = true, parentNames = names))
        } else {
            val shown = item.group.waitingOn(ctx.scope.myUid) ?: item.group
            val offerer = names.labelForUid(item.group.swaps.first().override.requestedBy)
            val sides = shown.sidesAsked(names)
            SwapOfferSentence(sides, offerer, ctx.swapChildren)
            // The deadline is the status row's, so the line names the sender alone.
            if (swapHeadingNamesChildren(sides, ctx.swapChildren)) SwapProposedByLine(offerer, answerByMillis = null)
            sides?.let { SwapOfferSides(it, offerer, names) }
        }
        is InboxItem.Request -> NewTimeLine(item.request)
        is InboxItem.AwaitingEvent -> {
            SummaryText(eventWhenText(item.event))
            if (!mine) {
                SummaryText(
                    stringResource(
                        Res.string.event_acceptance_offered_to_you,
                        names.labelForUid(item.event.createdByFirebaseUid)
                    )
                )
            }
        }
        is InboxItem.Proposal -> if (mine) {
            SummaryText(stringResource(Res.string.custody_proposal_inbox_body_yours))
        } else {
            SummaryText(
                custodyDiffDescription(ctx.proposalDiff, names)
                    ?: stringResource(
                        Res.string.custody_proposal_inbox_body,
                        names.labelForUid(item.proposal.proposedBy)
                    )
            )
        }
        is InboxItem.Split -> SplitLines(item, ctx, mine)
        is InboxItem.Support, is InboxItem.Professional, is InboxItem.Plan,
        is InboxItem.Header, is InboxItem.History -> Unit
    }
}

/**
 * A split's lines: who proposes it (for the co-parent's), what it would be and, when known, the one
 * agreed now. Money is never cut off (design item 15): every line wraps.
 */
@Composable
private fun SplitLines(item: InboxItem.Split, ctx: HomeRequestsContext, mine: Boolean) {
    val names = ctx.parentNames
    if (!mine) {
        SummaryText(stringResource(Res.string.split_request_body_theirs, names.labelForUid(item.proposal.proposedBy)))
    }
    Text(
        text = stringResource(Res.string.split_request_proposed, splitSharesText(item.proposal.ratio, names)),
        style = MaterialTheme.typography.bodyMediumEmphasized
    )
    ctx.agreedSplit?.let { now ->
        SummaryText(stringResource(Res.string.split_request_now, splitSharesText(now, names)))
    }
}

/** The event's time now and the one asked for, with the arrow between, on the reader's clock. */
@Composable
internal fun NewTimeLine(request: ChangeRequest) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        SummaryText(ReaderDateText.dateWithTime(request.currentStartDateTime, "MMMEd"))
        AppIcon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSizes.Inline)
        )
        Text(
            text = ReaderDateText.dateWithTime(request.proposedStartDateTime, "MMMEd"),
            style = MaterialTheme.typography.bodyMediumEmphasized
        )
    }
}

/** The proposer's own words, quoted and attributed, when they wrote any. */
@Composable
internal fun RequestNote(item: InboxItem, ctx: HomeRequestsContext) {
    if (item.isMadeBy(ctx.scope.myUid)) return
    val names = ctx.parentNames
    when (item) {
        is InboxItem.Swap -> {
            val override = item.group.swaps.first().override
            SwapNoteQuote(override.note, names.labelForUid(override.requestedBy))
        }
        is InboxItem.Request -> SwapNoteQuote(item.request.note, names.labelForUid(item.request.requestedBy))
        is InboxItem.AwaitingEvent ->
            SwapNoteQuote(item.event.description, names.labelForUid(item.event.createdByFirebaseUid))
        else -> Unit
    }
}

@Composable
private fun SummaryText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The disc behind a request's icon. */
private val KIND_DISC = 40.dp

/** The outline of a waiting row's disc. */
private val DISC_OUTLINE = 1.5.dp
