package com.coparently.app.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.custody.CustodyPatternDiff
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.changerequests.AgreementScreen
import com.coparently.app.presentation.changerequests.CardClock
import com.coparently.app.presentation.changerequests.ChangeRequestViewModel
import com.coparently.app.presentation.changerequests.InboxAnswers
import com.coparently.app.presentation.changerequests.InboxItem
import com.coparently.app.presentation.changerequests.InboxItems
import com.coparently.app.presentation.changerequests.InboxOpeners
import com.coparently.app.presentation.changerequests.RequestsState
import com.coparently.app.presentation.changerequests.inboxCardClock
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.resources.Res
import com.coparently.app.resources.change_request_withdraw
import com.coparently.app.resources.professional_dialog_revoke
import com.coparently.app.resources.professional_revoke_confirm
import org.jetbrains.compose.resources.stringResource

/**
 * Home's Requests block as the screen holds it: what waits on each parent, what the cards are
 * drawn from (null while the family's requests load), which request's Details sheet is open, and
 * which professional's waiting access this parent was asked to withdraw.
 */
@Stable
internal class HomeRequests(
    val items: HomeRequestItems,
    val ctx: HomeRequestsContext?,
    val detailsKey: MutableState<String?>,
    val confirmingGrantId: MutableState<String?>,
    val professionals: List<ProfessionalGrant>
) {
    /**
     * By when the co-parent's custody proposal waits for this parent's answer, for its pop-up; null
     * when it has no clock or the answer is no longer asked for.
     */
    fun proposalDeadlineMillis(): Long? {
        val context = ctx ?: return null
        val proposal = items.forMe.firstOrNull { it is InboxItem.Proposal } ?: return null
        return context.clockFor(proposal)?.let(::responderDeadlineMillis)
    }

    /**
     * By when each of the co-parent's day-swap offers waits for this parent's answer, by group key,
     * for its pop-up; an offer with no clock, or no longer answered, is absent.
     */
    fun swapDeadlinesMillis(): Map<String, Long> {
        val context = ctx ?: return emptyMap()
        return items.forMe.filterIsInstance<InboxItem.Swap>().mapNotNull { swap ->
            context.clockFor(swap)?.let(::responderDeadlineMillis)?.let { swap.group.key to it }
        }.toMap()
    }
}

/**
 * The family on screen's requests for Home, from the inbox's own ViewModel and list
 * ([InboxItems]), so a request answered on the other phone leaves Home without a reload, and every
 * answer goes through the inbox's own ([InboxAnswers]).
 *
 * @param parentNames Home's names for the two parents.
 * @param onOpenEvent Opens an event's editor, as the inbox's change request card does.
 * @param onOpenAgreement Opens the screen a child support, professional or plan request is answered on.
 */
@Composable
internal fun rememberHomeRequests(
    viewModel: ChangeRequestViewModel,
    parentNames: ParentNames,
    onOpenEvent: (String) -> Unit,
    onOpenAgreement: (AgreementScreen) -> Unit
): HomeRequests {
    val state by viewModel.requests.collectAsState()
    val miniMonths by viewModel.miniMonths.collectAsState()
    val swapMonths by viewModel.swapMonths.collectAsState()
    val swapChildren by viewModel.swapChildNames.collectAsState()
    val proposalDiff by viewModel.pendingProposalDiff.collectAsState()
    val proposalCitation by viewModel.pendingProposalCitation.collectAsState()
    val lastChanceInFlight by viewModel.lastChanceInFlight.collectAsState()
    val detailsKey = rememberSaveable { mutableStateOf<String?>(null) }
    val confirmingGrantId = rememberSaveable { mutableStateOf<String?>(null) }
    val answers = remember(viewModel, onOpenEvent, onOpenAgreement) {
        InboxAnswers.of(viewModel, InboxOpeners(onOpenEvent, onOpenAgreement)) {}
    }
    val ready = state as? RequestsState.Ready
    val items = remember(ready?.inbox, ready?.scope) {
        ready?.let { HomeRequestItems.of(InboxItems.of(it.inbox, it.scope)) } ?: HomeRequestItems()
    }
    val ctx = ready?.let {
        HomeRequestsContext(
            scope = it.scope,
            parentNames = parentNames,
            miniMonths = miniMonths,
            swapMonths = swapMonths,
            proposalDiff = proposalDiff,
            proposalCitation = proposalCitation,
            agreedSplit = it.settings?.ratio,
            custody = it.custody,
            lastChanceInFlight = lastChanceInFlight,
            answers = answers,
            onDetails = { item -> detailsKey.value = item.key },
            onOpenAgreement = onOpenAgreement,
            onWithdrawProfessional = { grantId -> confirmingGrantId.value = grantId },
            swapChildren = swapChildren
        )
    }
    return HomeRequests(items, ctx, detailsKey, confirmingGrantId, ready?.agreements?.professionals.orEmpty())
}

/**
 * What Home's Requests block opens over the screen: the Details sheet of the request it names, and
 * the question before a professional's waiting access is withdrawn, which ends the grant — asked as
 * the inbox asks it. A sheet whose request left (answered on the other phone, withdrawn) closes.
 */
@Composable
internal fun HomeRequestsOverlays(requests: HomeRequests, viewModel: ChangeRequestViewModel) {
    val ctx = requests.ctx ?: return
    requests.detailsKey.value?.let { key ->
        val item = requests.items.forMe.firstOrNull { it.key == key }
        if (item == null) {
            LaunchedEffect(key) { requests.detailsKey.value = null }
        } else {
            RequestDetailsSheet(item, ctx, onDismiss = { requests.detailsKey.value = null })
        }
    }
    val withdrawing = requests.confirmingGrantId.value
        ?.let { id -> requests.professionals.firstOrNull { it.id == id } }
    if (withdrawing != null) {
        ConfirmationDialog(
            title = stringResource(Res.string.change_request_withdraw),
            message = stringResource(Res.string.professional_revoke_confirm, withdrawing.name),
            confirmText = stringResource(Res.string.professional_dialog_revoke),
            onConfirm = {
                viewModel.withdrawProfessional(withdrawing.id)
                requests.confirmingGrantId.value = null
            },
            onDismiss = { requests.confirmingGrantId.value = null },
            isDestructive = true
        )
    }
}

/**
 * What Home's Requests section draws in the dashboard: the block's state, what its cards are drawn
 * from (null while loading), and where "See all" leads.
 */
internal class HomeRequestsSection(
    val state: RequestsBlockState,
    val ctx: HomeRequestsContext?,
    val onSeeAll: () -> Unit
)

/**
 * What Home's request cards are drawn from, collected once by the screen from the inbox's own
 * ViewModel ([com.coparently.app.presentation.changerequests.ChangeRequestViewModel]), so a request
 * answered on the other phone leaves Home as it leaves the inbox.
 *
 * @property scope The family on screen: who "you" are and who the co-parent is.
 * @property miniMonths Each waiting event's month, by event id.
 * @property swapMonths Each waiting swap's months, by group key.
 * @property proposalDiff What the co-parent's proposal would change.
 * @property proposalCitation Where the co-parent's proposal came from (MON-21).
 * @property agreedSplit The family's agreed expense split, or null while it is not known.
 * @property custody The family's custody document: the schedule agreed now and its day swaps.
 * @property lastChanceInFlight The clocks whose last chance is being given right now, by id.
 * @property answers The inbox's own answers, withdrawals and clock actions.
 * @property onDetails Opens the Details sheet over Home for an item.
 * @property onOpenAgreement Opens the screen a child support, professional or plan row names.
 * @property onWithdrawProfessional Asks before ending a professional's waiting access, as the inbox does.
 * @property swapChildren The children a day swap moves, by first name, for an offer phrased by who
 *   has them (meeting of 25 November 2026, item 3); null while not known.
 *
 * Public, not internal, so the app's screenshot tests can draw Home's cards with their own.
 */
@Immutable
@Suppress("LongParameterList") // the inbox's collected state and its actions, as the inbox's cards take them
class HomeRequestsContext(
    val scope: RequestScope,
    val parentNames: ParentNames,
    val miniMonths: Map<String, MiniMonthModel>,
    val swapMonths: Map<String, List<MiniMonthModel>>,
    val proposalDiff: CustodyPatternDiff?,
    val proposalCitation: CitationStatus,
    val agreedSplit: SplitRatio?,
    val custody: SharedCustody?,
    val lastChanceInFlight: Set<String>,
    val answers: InboxAnswers,
    val onDetails: (InboxItem) -> Unit,
    val onOpenAgreement: (AgreementScreen) -> Unit,
    val onWithdrawProfessional: (String) -> Unit,
    val swapChildren: List<String>? = null
) {
    /** The co-parent of the family on screen, by name. */
    val coParentName: String get() = parentNames.labelForUid(scope.coParentUid)

    /** The clock [item]'s card shows, by the inbox's rule ([inboxCardClock]). */
    internal fun clockFor(item: InboxItem): CardClock? =
        inboxCardClock(item, scope, parentNames, lastChanceInFlight, answers.markSeen, answers.giveLastChance)
}
