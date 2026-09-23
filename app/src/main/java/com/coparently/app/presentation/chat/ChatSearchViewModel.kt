package com.coparently.app.presentation.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.chat.ChatSearch
import com.coparently.app.domain.chat.ChatSearchHit
import com.coparently.app.domain.chat.ChatSearchResult
import com.coparently.app.domain.repository.ChatSearchRepository
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the search panel over a chat thread shows. */
sealed interface ChatSearchState {

    /** Search is not open; the thread is shown as usual. */
    data object Closed : ChatSearchState

    /** Open, but the query is too short to search — the panel explains what search covers. */
    data object Prompt : ChatSearchState

    /** The debounced query is being run. */
    data object Searching : ChatSearchState

    /**
     * The answer for [query]. An empty [result] is "nothing matched", a real answer.
     *
     * @property query The query as typed, for the "nothing matches …" line.
     */
    data class Results(val query: String, val result: ChatSearchResult) : ChatSearchState

    /** The local read failed; nothing was found *or* ruled out, and the panel says so. */
    data object Failed : ChatSearchState
}

/**
 * Where to scroll the thread for a chosen result.
 *
 * @property messageId The message to scroll to and highlight.
 * @property windowNeeded How many of the newest messages the thread must hold for it to be there.
 */
data class ChatSearchJump(val messageId: String, val windowNeeded: Int)

/**
 * Search inside one chat thread (MON-15).
 *
 * **Local only, and one conversation only.** [open] names the thread the screen is showing — the
 * selected family's, since the Chat tab follows `ChatPartnerSource` (M-8) — and every query runs
 * against that conversation's Room rows through [ChatSearchRepository]. Nothing here reaches
 * Firestore, and nothing searches another family's thread.
 *
 * Its own ViewModel rather than more of `ChatViewModel`, which is already at detekt's function
 * budget and whose lifetime is tied to the process-wide unread badge.
 */
@HiltViewModel
class ChatSearchViewModel @Inject constructor(
    private val chatSearchRepository: ChatSearchRepository,
    parentsSource: ParentsSource
) : ViewModel() {

    /** The two parents, to name the sender of each result. */
    val parents: StateFlow<Parents> = parentsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), Parents())

    private val _query = MutableStateFlow("")

    /** The query as typed. */
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<ChatSearchState>(ChatSearchState.Closed)

    /** What the panel shows. See [ChatSearchState]. */
    val state: StateFlow<ChatSearchState> = _state.asStateFlow()

    private val _jumps = MutableSharedFlow<ChatSearchJump>(extraBufferCapacity = 1)

    /** One-shot: the thread should scroll to this message. */
    val jumps: SharedFlow<ChatSearchJump> = _jumps.asSharedFlow()

    private var conversationId: String? = null
    private var searchJob: Job? = null

    /** Opens search over [conversationId], empty. */
    fun open(conversationId: String) {
        this.conversationId = conversationId
        _query.value = ""
        _state.value = ChatSearchState.Prompt
    }

    /**
     * Closes a search that was opened over a different thread. The Chat tab renders whichever
     * family is selected (M-8), so the thread can change underneath an open search, and results
     * from the previous family's conversation must not stay on screen over the new one.
     */
    fun onThreadShown(conversationId: String) {
        val searched = this.conversationId
        if (searched != null && searched != conversationId) close()
    }

    /** Closes search and forgets the query — it is never stored anywhere. */
    fun close() {
        searchJob?.cancel()
        conversationId = null
        _query.value = ""
        _state.value = ChatSearchState.Closed
    }

    /**
     * Records the query and runs it once typing pauses for [SEARCH_DEBOUNCE_MS].
     *
     * A hand-rolled debounce rather than `Flow.debounce`, for the reason `ChatViewModel`'s draft
     * write gives: that operator is still `@FlowPreview`.
     */
    fun onQueryChange(query: String) {
        _query.value = query
        searchJob?.cancel()
        val thread = conversationId ?: return
        if (!ChatSearch.isSearchable(query)) {
            _state.value = ChatSearchState.Prompt
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            // Earlier results stay up while the next query runs; a spinner flashing between two
            // result lists on every pause in typing is noise.
            if (_state.value !is ChatSearchState.Results) _state.value = ChatSearchState.Searching
            _state.value = try {
                ChatSearchState.Results(query, chatSearchRepository.search(thread, query))
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                // The query itself is the reader's words; it is not logged.
                Log.w(TAG, "Chat search failed", e)
                ChatSearchState.Failed
            }
        }
    }

    /**
     * Closes search and asks the thread to scroll to [hit]'s message, after working out how far
     * back the thread's window has to reach for it to be loaded.
     */
    fun select(hit: ChatSearchHit) {
        val thread = conversationId ?: return
        viewModelScope.launch {
            val needed = try {
                chatSearchRepository.countMessagesSince(thread, hit.message.sentAtMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.w(TAG, "Could not size the window for a search result", e)
                0
            }
            _jumps.emit(ChatSearchJump(hit.message.id, needed))
        }
        close()
    }

    companion object {
        /**
         * Quiet period after the last keystroke before the thread is searched: long enough that a
         * word typed at speed is one query, short enough that the list keeps up with a pause.
         */
        const val SEARCH_DEBOUNCE_MS = 300L

        private const val TAG = "ChatSearchViewModel"

        /** How long a `WhileSubscribed` flow stays warm across a configuration change. */
        private const val SUBSCRIPTION_TIMEOUT_MS = 5000L
    }
}
