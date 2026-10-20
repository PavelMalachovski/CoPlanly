package com.coparently.app.presentation.changerequests

import com.coparently.app.data.chat.ChatPartner
import com.coparently.app.data.chat.ChatPartnerSource
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.requests.RequestClocks
import com.coparently.app.domain.changerequests.ClocksAt
import com.coparently.app.domain.changerequests.RequestInbox
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.observeSignedInUser
import com.coparently.app.presentation.common.projectedCoParentUid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate
import javax.inject.Inject

/** Where the requests of the family on screen stand. */
sealed interface RequestsState {

    /** The pairing state or the signed-in parent is not known yet. */
    data object Loading : RequestsState

    /** There is no co-parent, so there is nobody to ask or answer. */
    data object NoCoParent : RequestsState

    /**
     * The family on screen's requests.
     *
     * @property scope Who the family is.
     * @property inbox Its three sections.
     * @property changeRequests The family's change requests, every status — what a chat card's
     *   highlight looks a request up in.
     * @property events The family's events, every acceptance — what the mini months draw from.
     * @property custody The family's own custody document — its schedule and swaps, what the mini
     *   months colour the days with — or null while it has none or has not arrived.
     * @property clocks The family's request clocks at the moment [inbox] was placed by them (phase
     *   3a); none until the clocks listener first answers.
     * @property settings The family's own expense split document — the agreed split and any
     *   proposal of a new one — or null while it has none or has not arrived.
     */
    data class Ready(
        val scope: RequestScope,
        val inbox: RequestInbox,
        val changeRequests: List<ChangeRequest>,
        val events: List<Event>,
        val custody: SharedCustody? = null,
        val clocks: ClocksAt = ClocksAt(),
        val settings: FamilySettings? = null
    ) : RequestsState
}

/**
 * The requests of the family on screen, for every surface that shows them (phase 2, spec §6.3):
 * the inbox, Home's Requests row and feed, and the calendar's banner. One source, so the three
 * cannot count different things.
 *
 * **Which family** is [ChatPartnerSource.resolve]'s rule, the one the chat and the names follow
 * (design refresh item 12): the server says whether there is a co-parent, the family switcher's
 * projection on the signed-in row says which. A parent in two families sees one family's requests
 * at a time; another family's waiting items are what the switcher's dot says.
 *
 * Day swaps and the custody proposal come from that family's own custody document, asked for by
 * its id ([CustodyModelRepository.observeSharedOf]) — never from Room's one active custody model,
 * which keeps the previous family's swaps while the family on screen has no document (phase 2
 * review). Change requests and events are narrowed here through [RequestScope].
 *
 * **The clocks** (phase 3a, spec §4.1): every request is placed by its clock as well as its record,
 * so that family's request clocks ([RequestClocks.observeFamily], that family's alone) are a fourth
 * source, and their minute tick re-places the requests when nothing was written or received — a
 * request passes its deadline on every screen at the minute it does. Until the clocks listener
 * first answers, the requests are placed as if none had a clock: the inbox is never held back by
 * it, and a listener that keeps failing reads as no clocks.
 *
 * **The expense split** is a request too: a proposed split waits for the co-parent's answer like a
 * custody proposal, and is read the same way — from that family's own `family_settings` document,
 * asked for by its id ([FamilySettingsRepository.observeSettingsOf]), so it can never be another
 * family's. Until that listener first answers, the inbox is placed as if the family had no split
 * document, as it is for the clocks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// the five sources the requests are derived from, and the two that say whose and which family's they are
@Suppress("LongParameterList")
class RequestsSource @Inject constructor(
    private val changeRequestRepository: ChangeRequestRepository,
    private val eventRepository: EventRepository,
    private val custodyModelRepository: CustodyModelRepository,
    private val userRepository: UserRepository,
    private val pairingRepository: PairingRepository,
    private val requestClocks: RequestClocks,
    private val familySettingsRepository: FamilySettingsRepository
) {

    /** The family on screen's requests, re-derived on every change to any of them. */
    fun observe(): Flow<RequestsState> = combine(
        userRepository.observeCurrentUserId(),
        pairingRepository.observePairingState(),
        userRepository.observeSignedInUser()
    ) { uid, pairing, row -> scopeOf(uid, pairing, row) }
        .distinctUntilChanged()
        .flatMapLatest { answer ->
            when (answer) {
                ScopeAnswer.Resolving -> flowOf(RequestsState.Loading)
                ScopeAnswer.None -> flowOf(RequestsState.NoCoParent)
                is ScopeAnswer.Of -> readyFor(answer.scope)
            }
        }

    private fun readyFor(scope: RequestScope): Flow<RequestsState> = combine(
        changeRequestRepository.getAllChangeRequests(),
        eventRepository.getAllEvents(),
        custodyModelRepository.observeSharedOf(scope.familyId),
        requestClocks.observeFamily(scope.myUid, scope.familyId).onStart { emit(ClocksAt()) },
        familySettingsRepository.observeSettingsOf(scope.familyId).onStart { emit(null) }
    ) { requests, events, custody, clocks, settings ->
        val familyRequests = requests.filter(scope::covers)
        val familyEvents = events.filter(scope::covers)
        val swaps = custody?.dayOverrides.orEmpty()
        RequestsState.Ready(
            scope = scope,
            inbox = RequestInbox.build(
                scope,
                familyRequests,
                familyEvents,
                swaps,
                custody,
                // Read per emission rather than once: this flow outlives midnight.
                LocalDate.now(),
                clocks,
                settings = settings
            ),
            changeRequests = familyRequests,
            events = familyEvents,
            custody = custody,
            clocks = clocks,
            settings = settings
        )
    }

    /** What the three inputs say about the family on screen. */
    private sealed interface ScopeAnswer {
        data object Resolving : ScopeAnswer
        data object None : ScopeAnswer
        data class Of(val scope: RequestScope) : ScopeAnswer
    }

    private fun scopeOf(uid: String?, pairing: PairingState, row: User?): ScopeAnswer {
        val myUid = uid?.takeIf { it.isNotBlank() } ?: return ScopeAnswer.Resolving
        return when (val partner = ChatPartnerSource.resolve(pairing, row?.projectedCoParentUid())) {
            ChatPartner.Resolving -> ScopeAnswer.Resolving
            ChatPartner.None -> ScopeAnswer.None
            is ChatPartner.Linked -> {
                val coParents = FamilyAudience.coParents(myUid, row?.partnerIds.orEmpty(), partner.partnerUid)
                RequestScope.of(myUid, partner.partnerUid, coParents)?.let { ScopeAnswer.Of(it) } ?: ScopeAnswer.None
            }
        }
    }
}
