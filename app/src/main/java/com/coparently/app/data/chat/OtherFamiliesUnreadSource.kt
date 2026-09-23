package com.coparently.app.data.chat

import android.util.Log
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.data.repository.longOrNull
import com.coparently.app.data.repository.markMap
import com.coparently.app.domain.chat.ChatReadState
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which families that are **not** on screen have something new in their chat (M-8).
 *
 * The chat mirrors only the selected family ([ChatMirror]), so a family the parent is not
 * looking at has no messages in Room to count, and a Room count across families would say 0 when
 * it is not — worse than no signal (design item 8). What *is* cheap to know is the conversation
 * document: it carries `lastMessageAt` and the `lastReadAt` marks, so one single-document
 * listener per other family answers "is there anything after my mark?" without reading a single
 * message. That is a yes/no, which is why the switcher draws a dot and never a number.
 *
 * Four properties are the point of the class:
 *
 * - **One listener per other family, shared.** [unreadFamilyIds] is one `shareIn` for the
 *   process, so the chip on Home, the chip on Expenses and the Settings dialog read the same
 *   listeners rather than each attaching their own. `WhileSubscribed`: when no switcher is on
 *   screen, nothing listens.
 * - **None at all for a one-family account** — the switcher is not drawn at one family
 *   (design item 13), and there is no "other" family to watch. The listener list is derived at
 *   two, not at one.
 * - **Re-derived when the families or the selection change**, and cancelled with them:
 *   `flatMapLatest` drops every listener on a switch, an unpair, a new pairing or a sign-out
 *   before attaching the new set.
 * - **Bounded retry, then silence**, the shape `MessageRepositoryImpl.reconnecting()` uses. A
 *   conversation document that does not exist yet reads as denied (the rule keys on its
 *   `participants`), and a dot is not worth a listener retried for the life of the process. A
 *   family that gave up shows no dot until the switcher is next subscribed, which restarts it.
 *
 * It never *creates* a conversation: a listener here is a read, and a family whose thread has
 * never been opened on either phone has nothing to be unread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OtherFamiliesUnreadSource @Inject constructor(
    private val userRepository: UserRepository,
    private val selectedFamilySource: SelectedFamilySource,
    private val firestoreMessageDataSource: FirestoreMessageDataSource
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Family ids, other than the one on screen, whose conversation moved after my read mark.
     * Empty for a signed-out or one-family account, and while nothing has answered yet.
     */
    val unreadFamilyIds: Flow<Set<String>> =
        observeUnread().shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** The unshared stream behind [unreadFamilyIds]; a test collects this directly. */
    internal fun observeUnread(): Flow<Set<String>> =
        userRepository.observeCurrentUserId()
            .flatMapLatest { uid -> if (uid.isNullOrBlank()) flowOf(emptySet<String>()) else unreadFor(uid) }
            .distinctUntilChanged()

    private fun unreadFor(myUid: String): Flow<Set<String>> =
        combine(
            selectedFamilySource.observeFamilies(myUid),
            selectedFamilySource.observe(myUid).map { it?.familyId }
        ) { families, selectedId -> familiesToWatch(families, selectedId) }
            .distinctUntilChanged()
            .flatMapLatest { familyIds -> watchAll(myUid, familyIds) }

    private fun watchAll(myUid: String, familyIds: List<String>): Flow<Set<String>> =
        if (familyIds.isEmpty()) {
            flowOf(emptySet())
        } else {
            combine(familyIds.map { id -> watch(myUid, id).map { unread -> id.takeIf { unread } } }) {
                it.filterNotNull().toSet()
            }
        }

    /**
     * One family's signal. The conversation id *is* the family id (`ConversationKey.of` and
     * `FamilyKey.of` are the same function), so no lookup stands between the two.
     */
    private fun watch(myUid: String, conversationId: String): Flow<Boolean> =
        firestoreMessageDataSource.observeConversation(conversationId)
            .map { document -> hasUnread(document, myUid) }
            .retryWhen { cause, attempt ->
                if (cause is CancellationException || attempt >= MAX_RECONNECT_ATTEMPTS) {
                    return@retryWhen false
                }
                delay((RECONNECT_BASE_DELAY_MS shl attempt.toInt()).coerceAtMost(RECONNECT_MAX_DELAY_MS))
                true
            }
            .catch { e ->
                // Deliberately no id in the message: it is two Firebase uids joined.
                Log.w(TAG, "Another family's conversation listener gave up; no dot for it", e)
                emit(false)
            }
            // `combine` waits for every family to answer once; before the first snapshot there
            // is nothing to show, which is what false says.
            .onStart { emit(false) }
            .distinctUntilChanged()

    companion object {
        private const val TAG = "OtherFamiliesUnread"

        /** Keeps the listeners across a tab change or a configuration change. */
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Same bound as `MessageRepositoryImpl.reconnecting()`. */
        private const val MAX_RECONNECT_ATTEMPTS = 8L
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
        private const val RECONNECT_MAX_DELAY_MS = 60_000L

        /**
         * The families to attach a listener to: every one but the selected, and none at all
         * below two — a one-family account has no "other" family, and must cost nothing.
         *
         * @param families Every family the signed-in parent is in.
         * @param selectedFamilyId The family on screen, or null when the row names none yet.
         */
        fun familiesToWatch(families: List<FamilyOption>, selectedFamilyId: String?): List<String> =
            if (families.size < 2) {
                emptyList()
            } else {
                families.map { it.familyId }.filter { it != selectedFamilyId }
            }

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
    }
}
