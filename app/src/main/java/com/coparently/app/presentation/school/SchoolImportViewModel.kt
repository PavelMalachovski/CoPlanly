package com.coparently.app.presentation.school

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.school.SchoolAccounts
import com.coparently.app.data.school.SchoolImporter
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolSyncFailure
import com.coparently.app.domain.school.SchoolSyncResult
import com.coparently.app.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One connection as the list shows it.
 *
 * @property connection The connection.
 * @property childName The CoPlanly child it imports for, as the family named them; the school's
 *   name for the child when the record is gone.
 */
data class SchoolConnectionRow(
    val connection: SchoolConnection,
    val childName: String
)

/**
 * The school-import screen's state (MON-8).
 *
 * @property rows The signed-in account's connections.
 * @property isLoading The first list has not come back yet.
 * @property updating The ids of the connections an "Update now" is running for.
 * @property message A one-off outcome for the snackbar, or null.
 */
data class SchoolImportUiState(
    val rows: List<SchoolConnectionRow> = emptyList(),
    val isLoading: Boolean = true,
    val updating: Set<String> = emptySet(),
    val message: UiText? = null
)

/**
 * Lists the school connections and runs "Update now" and "Disconnect" (MON-8).
 *
 * Signing in again is the connect screen's job, in its reconnect mode.
 */
@HiltViewModel
class SchoolImportViewModel @Inject constructor(
    private val accounts: SchoolAccounts,
    private val importer: SchoolImporter,
    childInfoRepository: ChildInfoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SchoolImportUiState())

    /** What the screen shows. */
    val state: StateFlow<SchoolImportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(accounts.observe(), childInfoRepository.getAllChildInfo()) { connections, children ->
                val names = children.associate { it.id to it.childName }
                connections.map { connection ->
                    SchoolConnectionRow(
                        connection = connection,
                        childName = names[connection.childId]?.takeIf { it.isNotBlank() }
                            ?: connection.studentName
                    )
                }
            }.collect { rows -> _state.update { it.copy(rows = rows, isLoading = false) } }
        }
    }

    /** Runs the import for [connectionId] now. A second tap while it runs does nothing. */
    fun updateNow(connectionId: String) {
        if (connectionId in _state.value.updating) return
        _state.update { it.copy(updating = it.updating + connectionId) }
        viewModelScope.launch {
            val result = importer.sync(connectionId)
            _state.update {
                it.copy(updating = it.updating - connectionId, message = messageFor(result))
            }
        }
    }

    /** Forgets [connectionId]. The events it imported stay in the calendar. */
    fun disconnect(connectionId: String) {
        viewModelScope.launch {
            accounts.disconnect(connectionId)
            _state.update { it.copy(message = UiText.Res(R.string.school_import_disconnected)) }
        }
    }

    /** The snackbar has shown [SchoolImportUiState.message]. */
    fun messageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun messageFor(result: SchoolSyncResult): UiText = when (result) {
        is SchoolSyncResult.Success -> UiText.Res(R.string.school_import_sync_done)
        SchoolSyncResult.NeedsPassword -> UiText.Res(R.string.school_import_sync_needs_password)
        is SchoolSyncResult.Failed -> when (result.reason) {
            SchoolSyncFailure.NETWORK -> UiText.Res(R.string.school_import_sync_network)
            SchoolSyncFailure.SERVER -> UiText.Res(R.string.school_import_sync_server)
        }
        SchoolSyncResult.NotFound -> UiText.Res(R.string.school_import_sync_server)
    }
}
