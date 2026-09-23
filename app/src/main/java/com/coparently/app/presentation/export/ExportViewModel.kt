package com.coparently.app.presentation.export

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.export.CommunicationRecordSource
import com.coparently.app.data.export.ExportFileWriter
import com.coparently.app.data.export.ExportedFile
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.export.CommunicationRecordBuilder
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.domain.export.RecordScope
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.parentLabel
import com.coparently.app.presentation.common.parentLabelByUid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** The two files an export can be. */
enum class ExportFormat { CSV, PDF }

/**
 * The export screen's state.
 *
 * @property from First day of the range, inclusive.
 * @property to Last day of the range, inclusive.
 * @property working The format being produced, or null when idle.
 * @property error What went wrong with the last attempt, for a snackbar; cleared once shown.
 */
data class ExportUiState(
    val from: LocalDate,
    val to: LocalDate,
    val working: ExportFormat? = null,
    val error: UiText? = null
)

/**
 * The words a record uses for a parent it cannot name — resolved in composition, like
 * [RecordLabels], because a ViewModel holds no `Context` (CQ-14).
 */
data class NameFallbacks(val you: String, val coParent: String, val unknown: String)

/**
 * Produces a communication record for a date range, as CSV or PDF (MON-3).
 *
 * **Ungated.** MON-1 has not set a price, so there is no entitlement to check; the gate arrives
 * with MON-11's entitlement layer, not as a flag invented here (ROADMAP MON-3).
 *
 * A save path in the sense of CLAUDE.md item 17: everything it needs at the moment of export is
 * read fresh — the signed-in uid, the co-parent, the names — never from a `WhileSubscribed`
 * state's `.value`.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val source: CommunicationRecordSource,
    private val writer: ExportFileWriter,
    private val parentsSource: ParentsSource,
    private val userRepository: UserRepository
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _state = MutableStateFlow(
        LocalDate.now(zone).let { today -> ExportUiState(from = today.minusMonths(DEFAULT_MONTHS), to = today) }
    )

    /** The range and whether an export is running. */
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private val _files = Channel<ExportedFile>(Channel.BUFFERED)

    /** Each finished file, once — the screen hands it to the share sheet. */
    val files: Flow<ExportedFile> = _files.receiveAsFlow()

    /** Moves the start of the range; an end before it moves with it. */
    fun setFrom(date: LocalDate) = _state.update { it.copy(from = date, to = maxOf(it.to, date)) }

    /** Moves the end of the range; a start after it moves with it. */
    fun setTo(date: LocalDate) = _state.update { it.copy(to = date, from = minOf(it.from, date)) }

    /** Clears the error once the screen has shown it. */
    fun errorShown() = _state.update { it.copy(error = null) }

    /**
     * Builds the record for the current range and writes it as [format].
     *
     * @param labels Every word the file prints, already in the reader's language.
     * @param fallbacks What to call a parent the app cannot name.
     */
    fun export(format: ExportFormat, labels: RecordLabels, fallbacks: NameFallbacks) {
        if (_state.value.working != null) return
        val range = _state.value
        _state.update { it.copy(working = format, error = null) }
        viewModelScope.launch {
            try {
                val file = produce(format, range.from, range.to, labels, fallbacks)
                if (file == null) {
                    _state.update { it.copy(error = UiText.Res(R.string.export_error_signed_out)) }
                } else {
                    _files.send(file)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "Export failed", e)
                _state.update { it.copy(error = UiText.Res(R.string.export_error_failed)) }
            } finally {
                _state.update { it.copy(working = null) }
            }
        }
    }

    // Each argument is one input of the record; bundling them would only rename this list.
    @Suppress("LongParameterList")
    private suspend fun produce(
        format: ExportFormat,
        from: LocalDate,
        to: LocalDate,
        labels: RecordLabels,
        fallbacks: NameFallbacks
    ): ExportedFile? {
        val myUid = userRepository.getCurrentUserId() ?: return null
        val partnerUid = parentsSource.coParentUid()
        // The pairing half can arrive a moment after the profile half; a record that named the
        // co-parent "Parent" because it was built in that moment would be a worse document.
        val parents = withTimeoutOrNull(NAMES_WAIT_MS) {
            parentsSource.observe().first { partnerUid == null || it.coParent != null }
        } ?: parentsSource.observe().first()
        val sources = source.gather(
            myUid = myUid,
            conversationId = partnerUid?.let { ConversationKey.of(myUid, it) },
            from = from,
            to = to,
            zone = zone
        )
        val record = CommunicationRecordBuilder.build(
            sources,
            RecordScope(
                from = from,
                to = to,
                zone = zone,
                generatedAtMillis = System.currentTimeMillis(),
                families = setOfNotNull(FamilyKey.orNull(myUid, partnerUid)),
                parents = listOfNotNull(parents.me, parents.coParent).map { nameForUid(it.uid, parents, fallbacks) },
                nameForUid = { uid -> nameForUid(uid, parents, fallbacks) },
                nameForSlot = { slot -> nameForSlot(slot, parents, fallbacks) }
            )
        )
        return when (format) {
            ExportFormat.CSV -> writer.writeCsv(record, labels)
            ExportFormat.PDF -> writer.writePdf(record, labels)
        }
    }

    private fun nameForUid(uid: String, parents: Parents, fallbacks: NameFallbacks): String =
        parentLabelByUid(uid, parents.me, parents.coParent, fallbacks.you, fallbacks.coParent, fallbacks.unknown)

    private fun nameForSlot(slot: String, parents: Parents, fallbacks: NameFallbacks): String =
        parentLabel(slot, parents.me, parents.coParent, fallbacks.you, fallbacks.coParent, fallbacks.unknown)

    private companion object {
        const val TAG = "ExportViewModel"

        /** The range a parent starts from: the last quarter, which is what a hearing usually asks. */
        const val DEFAULT_MONTHS = 3L

        /** How long an export waits for the co-parent's name before printing the fallback. */
        const val NAMES_WAIT_MS = 3_000L
    }
}
