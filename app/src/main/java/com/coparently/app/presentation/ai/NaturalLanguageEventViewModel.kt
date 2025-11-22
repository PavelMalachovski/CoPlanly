package com.coparently.app.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.model.ai.ParsedEvent
import com.coparently.app.domain.model.ai.ParsedEventResult
import com.coparently.app.domain.model.ai.ParsingContext
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.usecase.ai.NaturalLanguageEventParserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for natural language event parsing
 * Day 2 - Feature 2.1: Voice-to-event conversion
 */
@HiltViewModel
class NaturalLanguageEventViewModel @Inject constructor(
    private val naturalLanguageEventParserUseCase: NaturalLanguageEventParserUseCase,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _parsingState = MutableStateFlow<ParsingUiState>(ParsingUiState.Idle)
    val parsingState: StateFlow<ParsingUiState> = _parsingState.asStateFlow()

    fun parseEventFromText(text: String, context: ParsingContext = ParsingContext()) {
        if (text.isBlank()) {
            _parsingState.value = ParsingUiState.Error("Please provide event details")
            return
        }

        viewModelScope.launch {
            _parsingState.value = ParsingUiState.Parsing
            try {
                val result = naturalLanguageEventParserUseCase.parseEventFromText(text, context)

                when (result) {
                    is ParsedEventResult.Success -> {
                        _parsingState.value = ParsingUiState.Success(
                            parsedEvent = result.event,
                            confidence = result.confidence,
                            validationIssues = result.validationIssues
                        )
                    }
                    is ParsedEventResult.Error -> {
                        _parsingState.value = ParsingUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _parsingState.value = ParsingUiState.Error(
                    e.message ?: "Failed to parse event"
                )
            }
        }
    }

    fun confirmAndSaveEvent(parsedEvent: ParsedEvent) {
        viewModelScope.launch {
            try {
                val event = parsedEvent.toEvent()
                eventRepository.insertEvent(event)
                _parsingState.value = ParsingUiState.EventSaved
            } catch (e: Exception) {
                _parsingState.value = ParsingUiState.Error(
                    "Failed to save event: ${e.message}"
                )
            }
        }
    }

    fun reset() {
        _parsingState.value = ParsingUiState.Idle
    }
}

sealed class ParsingUiState {
    data object Idle : ParsingUiState()
    data object Parsing : ParsingUiState()
    data class Success(
        val parsedEvent: ParsedEvent,
        val confidence: Double,
        val validationIssues: List<String>
    ) : ParsingUiState()
    data class Error(val message: String) : ParsingUiState()
    data object EventSaved : ParsingUiState()
}
