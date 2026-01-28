package com.coparently.app.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.local.dao.CustodyScheduleDao
import com.coparently.app.data.local.entity.CustodyScheduleEntity
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.model.CustodyModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for calendar screen.
 * Handles custody schedule data, custody model, and view mode.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val custodyScheduleDao: CustodyScheduleDao,
    private val custodyModelRepository: CustodyModelRepository
) : ViewModel() {

    private val _custodySchedules = MutableStateFlow<List<CustodyScheduleEntity>>(emptyList())
    val custodySchedules: StateFlow<List<CustodyScheduleEntity>> = _custodySchedules.asStateFlow()

    private val _custodyModel = MutableStateFlow<CustodyModel?>(null)
    val custodyModel: StateFlow<CustodyModel?> = _custodyModel.asStateFlow()

    private val _viewMode = MutableStateFlow<CalendarViewMode>(CalendarViewMode.MONTH)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    init {
        loadCustodySchedules()
        loadCustodyModel()
    }

    /**
     * Loads all active custody schedules.
     * Legacy method - used when no CustodyModel is available.
     */
    fun loadCustodySchedules() {
        viewModelScope.launch {
            custodyScheduleDao.getAllActiveSchedules().collect { schedules ->
                _custodySchedules.value = schedules
            }
        }
    }

    /**
     * Loads the active custody model.
     * This is the preferred method for determining custody.
     */
    private fun loadCustodyModel() {
        viewModelScope.launch {
            custodyModelRepository.getActiveModel().collect { model ->
                _custodyModel.value = model
            }
        }
    }

    /**
     * Gets custody for a specific date.
     * Uses CustodyModel if available, falls back to legacy schedules.
     *
     * @param date The date to check
     * @return "mom", "dad", or null
     */
    fun getCustodyForDate(date: LocalDate): String? {
        // Prefer CustodyModel if available
        _custodyModel.value?.let { model ->
            return model.getCustodyFor(date)
        }

        // Fall back to legacy schedules
        val schedules = _custodySchedules.value
        return CustodyHelper.getCustodyForDate(date, schedules)
    }

    /**
     * Sets the calendar view mode.
     */
    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    /**
     * Sets the selected date.
     */
    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }
}
