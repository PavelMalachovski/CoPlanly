package com.coparently.app.presentation.export

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.export.CommunicationRecordSource
import com.coparently.app.data.export.ExportFileWriter
import com.coparently.app.data.export.ExportReceipts
import com.coparently.app.data.export.ExportedFile
import com.coparently.app.data.export.OptionalRecordSections
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.export.CommunicationRecord
import com.coparently.app.domain.export.CommunicationRecordBuilder
import com.coparently.app.domain.export.ExportFingerprint
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.domain.export.RecordScope
import com.coparently.app.domain.export.RecordVerification
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

/**
 * The export screen's state.
 *
 * @property from First day of the range, inclusive.
 * @property to Last day of the range, inclusive.
 * @property includePlan Whether the record carries the family's parenting plan. On by default: a
 *   plan is the other document two parents hand to a mediator, and leaving it out is the choice.
 * @property includeJournal Whether the record carries this parent's own private journal entries
 *   about days in the period (MON-22). **Off by default**, the opposite of the plan: nobody else
 *   has ever seen the journal, and putting it in a file for a third person is the parent's choice.
 * @property working The format being produced, or null when idle.
 * @property error What went wrong with the last attempt, for a snackbar; cleared once shown.
 */
data class ExportUiState(
    val from: LocalDate,
    val to: LocalDate,
    val includePlan: Boolean = true,
    val includeJournal: Boolean = false,
    val working: ExportFormat? = null,
    val error: UiText? = null
)

/**
 * The words a record uses for a parent it cannot name — resolved in composition, like
 * [RecordLabels], because a ViewModel holds no `Context` (CQ-14).
 */
data class NameFallbacks(val you: String, val coParent: String, val unknown: String)

/**
 * A finished export, ready for the share sheet.
 *
 * @property recordId The id the file prints and the server holds its hash under, or null when the
 *   file could not be registered — the screen then says so before it shares (MON-16).
 */
data class FinishedExport(val file: ExportedFile, val recordId: String?)

/**
 * Produces a communication record for a date range, as CSV or PDF (MON-3), and registers the
 * file's fingerprint so it can be verified later (MON-16).
 *
 * **Ungated.** MON-1 has not set a price, so there is no entitlement to check; the gate arrives
 * with MON-11's entitlement layer, not as a flag invented here (ROADMAP MON-3).
 *
 * **The record id is inside the bytes it vouches for**, so the order is fixed: reserve an id,
 * render the file with it, hash exactly those bytes, register the hash under the id, and save
 * those same bytes. A phone that cannot reserve renders the file as not registered; one whose
 * registration then fails renders it *again* without the id. No file ever names an id the server
 * holds no hash for — that would be claiming a verifiability it lacks (design item 8).
 *
 * A save path in the sense of CLAUDE.md item 17: everything it needs at the moment of export is
 * read fresh — the signed-in uid, the co-parent, the names — never from a `WhileSubscribed`
 * state's `.value`.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val source: CommunicationRecordSource,
    private val sections: OptionalRecordSections,
    private val writer: ExportFileWriter,
    private val receipts: ExportReceipts,
    private val parentsSource: ParentsSource,
    private val userRepository: UserRepository
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _state = MutableStateFlow(
        LocalDate.now(zone).let { today -> ExportUiState(from = today.minusMonths(DEFAULT_MONTHS), to = today) }
    )

    /** The range and whether an export is running. */
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private val _files = Channel<FinishedExport>(Channel.BUFFERED)

    /** Each finished file, once — the screen hands it to the share sheet. */
    val files: Flow<FinishedExport> = _files.receiveAsFlow()

    /** Moves the start of the range; an end before it moves with it. */
    fun setFrom(date: LocalDate) = _state.update { it.copy(from = date, to = maxOf(it.to, date)) }

    /** Moves the end of the range; a start after it moves with it. */
    fun setTo(date: LocalDate) = _state.update { it.copy(to = date, from = minOf(it.from, date)) }

    /** Puts the parenting plan in the record, or leaves it out. */
    fun setIncludePlan(include: Boolean) = _state.update { it.copy(includePlan = include) }

    /** Puts this parent's private journal entries for the period in the record, or leaves them out. */
    fun setIncludeJournal(include: Boolean) = _state.update { it.copy(includeJournal = include) }

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
        val request = _state.value
        _state.update { it.copy(working = format, error = null) }
        viewModelScope.launch {
            try {
                val finished = produce(format, request, labels, fallbacks)
                if (finished == null) {
                    _state.update { it.copy(error = UiText.Res(R.string.export_error_signed_out)) }
                } else {
                    _files.send(finished)
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

    /** [request] is the screen's state when the parent tapped: the range and which optional sections go in. */
    private suspend fun produce(
        format: ExportFormat,
        request: ExportUiState,
        labels: RecordLabels,
        fallbacks: NameFallbacks
    ): FinishedExport? {
        val myUid = userRepository.getCurrentUserId() ?: return null
        val partnerUid = parentsSource.coParentUid()
        // Everything that goes into the file is gathered here, before an id is reserved and the
        // bytes are rendered: nothing may be added between the hash and the save (MON-16).
        val record = buildRecord(myUid, partnerUid, request, fallbacks)
        val family = FamilyKey.orNull(myUid, partnerUid).orEmpty()
        val recordId = receipts.reserve(family, request.from, request.to, format)
        val registered = recordId?.let { registeredBytes(record, labels, format, it) }
        val bytes = registered
            ?: writer.render(record.copy(verification = RecordVerification.Unregistered), labels, format)
        return FinishedExport(writer.save(bytes, record, format), recordId.takeIf { registered != null })
    }

    /**
     * The file rendered with [recordId] on its face — but only if the server then registered
     * exactly these bytes. Null means "render it again as not registered".
     */
    private suspend fun registeredBytes(
        record: CommunicationRecord,
        labels: RecordLabels,
        format: ExportFormat,
        recordId: String
    ): ByteArray? {
        val verification = RecordVerification.Registered(recordId, receipts.verifyUrl)
        val bytes = writer.render(record.copy(verification = verification), labels, format)
        val landed = receipts.register(recordId, ExportFingerprint.sha256Hex(bytes), bytes.size)
        return bytes.takeIf { landed }
    }

    private suspend fun buildRecord(
        myUid: String,
        partnerUid: String?,
        request: ExportUiState,
        fallbacks: NameFallbacks
    ): CommunicationRecord {
        val from = request.from
        val to = request.to
        // The pairing half can arrive a moment after the profile half; a record that named the
        // co-parent "Parent" because it was built in that moment would be a worse document.
        val parents = withTimeoutOrNull(NAMES_WAIT_MS) {
            parentsSource.observe().first { partnerUid == null || it.coParent != null }
        } ?: parentsSource.observe().first()
        val gathered = source.gather(
            myUid = myUid,
            conversationId = partnerUid?.let { ConversationKey.of(myUid, it) },
            from = from,
            to = to,
            zone = zone
        )
        val sources = gathered.copy(
            plan = if (request.includePlan) sections.plan(myUid, partnerUid) else null,
            journal = if (request.includeJournal) sections.journal(myUid, from, to) else null
        )
        return CommunicationRecordBuilder.build(
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
