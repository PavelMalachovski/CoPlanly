package com.coparently.app.data.family

import android.util.Log
import com.coparently.app.data.remote.firebase.FirestoreChangeRequestDataSource
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.data.repository.longOrNull
import com.coparently.app.data.repository.markMap
import com.coparently.app.domain.chat.ChatReadState
import com.coparently.app.domain.custody.DaySwapInbox
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One kind of news a family that is not on screen can have waiting (M-8). The switcher draws a
 * dot for any of them and names which in its content description and dialog row.
 */
enum class FamilySignal {
    /** The conversation moved after this parent's read mark. */
    CHAT,

    /** The co-parent asked to move an event, and this parent has not answered. */
    CHANGE_REQUEST,

    /** The co-parent proposed a new schedule, or offered a day swap, awaiting this parent. */
    SCHEDULE
}

/**
 * What is waiting in each family that is **not** on screen (M-8): the family switcher's dot.
 *
 * Everything else in the app follows the selected family, so a family the parent is not looking
 * at has nothing in Room to count, and a Room count across families would say 0 when it is not —
 * worse than no signal (design item 8). What *is* cheap to know is a yes or no from the server,
 * which is why the switcher draws a dot and never a number. Per other family, three listeners:
 *
 * - **Chat** — the conversation *document*, whose `lastMessageAt` and `lastReadAt` marks answer
 *   "anything after my mark?" without reading a message ([hasUnread]).
 * - **Change requests** — a `limit(1)` query for a request from that co-parent to me that is
 *   still pending ([FirestoreChangeRequestDataSource.observeHasPendingFrom]).
 * - **Schedule** — the pair's `custody_models` document, one per family and read by id (the rule
 *   grants `allow get` only, so this must never become a query): a pending proposal or a pending
 *   day swap from the co-parent ([hasPendingSchedule]).
 *
 * Four properties are the point of the class:
 *
 * - **Shared.** [signals] is one `shareIn` for the process, so the chip on Home, the chip on
 *   Expenses and the Settings dialog read the same listeners. `WhileSubscribed`: when no switcher
 *   is on screen, nothing listens.
 * - **None at all for a one-family account** — the switcher is not drawn at one family (design
 *   item 13), and there is no "other" family to watch.
 * - **Re-derived when the families or the selection change**, and cancelled with them:
 *   `flatMapLatest` drops every listener on a switch, an unpair, a new pairing or a sign-out.
 * - **Bounded retry, then silence**, per listener, the shape `MessageRepositoryImpl.reconnecting()`
 *   uses. A conversation document that does not exist yet reads as denied (the rule keys on its
 *   `participants`), and a dot is not worth a listener retried for the life of the process. A
 *   listener that gave up raises nothing until the switcher is next subscribed.
 *
 * It only reads: it never creates a conversation or a custody document.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OtherFamiliesSignals @Inject constructor(
    private val userRepository: UserRepository,
    private val selectedFamilySource: SelectedFamilySource,
    private val messageDataSource: FirestoreMessageDataSource,
    private val changeRequestDataSource: FirestoreChangeRequestDataSource,
    private val custodyDataSource: FirestoreCustodyDataSource
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Family id → what is waiting there, for every family other than the one on screen that has
     * something. Empty for a signed-out or one-family account, and while nothing has answered.
     */
    val signals: Flow<Map<String, Set<FamilySignal>>> =
        observeSignals().shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** The unshared stream behind [signals]; a test collects this directly. */
    internal fun observeSignals(): Flow<Map<String, Set<FamilySignal>>> =
        userRepository.observeCurrentUserId()
            .flatMapLatest { uid -> if (uid.isNullOrBlank()) flowOf(emptyMap()) else signalsFor(uid) }
            .distinctUntilChanged()

    private fun signalsFor(myUid: String): Flow<Map<String, Set<FamilySignal>>> =
        combine(
            selectedFamilySource.observeFamilies(myUid),
            selectedFamilySource.observe(myUid).map { it?.familyId }
        ) { families, selectedId -> familiesToWatch(families, selectedId) }
            .distinctUntilChanged()
            .flatMapLatest { families -> watchAll(myUid, families) }

    private fun watchAll(myUid: String, families: List<FamilyOption>): Flow<Map<String, Set<FamilySignal>>> =
        if (families.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(families.map { family -> watch(myUid, family).map { family.familyId to it } }) { pairs ->
                pairs.filter { (_, signals) -> signals.isNotEmpty() }.toMap()
            }
        }

    /**
     * One family's three listeners. The conversation id and the custody document id *are* the
     * family id (`ConversationKey.of`, `CustodyKey.of` and `FamilyKey.of` are one function), so
     * no lookup stands between them.
     */
    private fun watch(myUid: String, family: FamilyOption): Flow<Set<FamilySignal>> {
        val chat = messageDataSource.observeConversation(family.familyId)
            .map { hasUnread(it, myUid) }
            .bounded(FamilySignal.CHAT)
        val requests = if (family.partnerUid.isBlank()) {
            flowOf(false)
        } else {
            changeRequestDataSource.observeHasPendingFrom(myUid, family.partnerUid)
                .bounded(FamilySignal.CHANGE_REQUEST)
        }
        val schedule = custodyDataSource.observeCustody(family.familyId)
            .map { hasPendingSchedule(it, myUid, LocalDate.now()) }
            .bounded(FamilySignal.SCHEDULE)
        return combine(chat, requests, schedule) { c, r, s ->
            buildSet {
                if (c) add(FamilySignal.CHAT)
                if (r) add(FamilySignal.CHANGE_REQUEST)
                if (s) add(FamilySignal.SCHEDULE)
            }
        }.distinctUntilChanged()
    }

    /**
     * Bounded retry, then "nothing waiting" for this kind alone — one family's failing custody
     * listener must not take its chat dot with it.
     */
    private fun Flow<Boolean>.bounded(kind: FamilySignal): Flow<Boolean> =
        this
            .retryWhen { cause, attempt ->
                if (cause is CancellationException || attempt >= MAX_RECONNECT_ATTEMPTS) {
                    return@retryWhen false
                }
                delay((RECONNECT_BASE_DELAY_MS shl attempt.toInt()).coerceAtMost(RECONNECT_MAX_DELAY_MS))
                true
            }
            .catch { e ->
                // Deliberately no id in the message: it is two Firebase uids joined.
                Log.w(TAG, "Another family's $kind listener gave up; no dot for it", e)
                emit(false)
            }
            // `combine` waits for every listener to answer once; before the first snapshot
            // there is nothing to show, which is what false says.
            .onStart { emit(false) }
            .distinctUntilChanged()

    companion object {
        private const val TAG = "OtherFamiliesSignals"

        /** Keeps the listeners across a tab change or a configuration change. */
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Same bound as `MessageRepositoryImpl.reconnecting()`. */
        private const val MAX_RECONNECT_ATTEMPTS = 8L
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
        private const val RECONNECT_MAX_DELAY_MS = 60_000L

        /**
         * The families to attach listeners to: every one but the selected, and none at all
         * below two — a one-family account has no "other" family, and must cost nothing.
         *
         * @param families Every family the signed-in parent is in.
         * @param selectedFamilyId The family on screen, or null when the row names none yet.
         */
        fun familiesToWatch(families: List<FamilyOption>, selectedFamilyId: String?): List<FamilyOption> =
            if (families.size < 2) emptyList() else families.filter { it.familyId != selectedFamilyId }

        /**
         * Whether one conversation document says there is something after [myUid]'s read
         * mark. A missing document has nothing unread; see [ChatReadState.hasUnread] for the
         * comparison and why it is safe although `lastMessageAt` does not name its sender.
         */
        fun hasUnread(document: Map<String, Any>?, myUid: String): Boolean =
            document != null &&
                ChatReadState.hasUnread(
                    lastMessageAtMillis = document.longOrNull("lastMessageAt"),
                    lastReadAtMillis = document.markMap("lastReadAt")[myUid]
                )

        /**
         * Whether the pair's custody document holds something [myUid] is being asked to answer:
         * a proposal the *other* parent made (a parent's own proposal waits on nobody here), or
         * a day swap awaiting them on [today] or later — [DaySwapInbox]'s rules, so the dot
         * agrees with the inbox the parent lands on. A missing document asks nothing.
         */
        fun hasPendingSchedule(custody: SharedCustody?, myUid: String, today: LocalDate): Boolean {
            if (custody == null) return false
            val proposal = custody.proposal
            if (proposal != null && proposal.proposedBy != myUid) return true
            return DaySwapInbox.visible(custody.dayOverrides, today)
                .any { DaySwapInbox.awaitsAnswerFrom(it, myUid) }
        }
    }
}
