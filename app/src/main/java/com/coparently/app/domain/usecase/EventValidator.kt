package com.coparently.app.domain.usecase

import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.ValidationResult
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Validator for Event domain model.
 * Validates event data before creation or update.
 */
class EventValidator @Inject constructor() {

    /**
     * Validates an event.
     * @return ValidationResult.Success if valid, ValidationResult.Error otherwise
     */
    fun validate(event: Event): ValidationResult {
        return when {
            event.title.isBlank() ->
                ValidationResult.Error("Event title cannot be empty", "title")

            event.title.length > 100 ->
                ValidationResult.Error("Event title is too long (max 100 characters)", "title")

            event.endDateTime != null && event.startDateTime.isAfter(event.endDateTime) ->
                ValidationResult.Error("End time must be after start time", "endDateTime")

            event.parentOwner.isBlank() ->
                ValidationResult.Error("Parent owner must be specified", "parentOwner")

            event.eventType.isBlank() ->
                ValidationResult.Error("Event type must be specified", "eventType")

            else -> ValidationResult.Success
        }
    }

    /**
     * Validates event date/time.
     */
    fun validateDateTime(startDateTime: LocalDateTime, endDateTime: LocalDateTime?): ValidationResult {
        return when {
            endDateTime != null && startDateTime.isAfter(endDateTime) ->
                ValidationResult.Error("End time must be after start time", "endDateTime")

            startDateTime.isBefore(LocalDateTime.now().minusYears(1)) ->
                ValidationResult.Error("Event date cannot be more than 1 year in the past", "startDateTime")

            startDateTime.isAfter(LocalDateTime.now().plusYears(5)) ->
                ValidationResult.Error("Event date cannot be more than 5 years in the future", "startDateTime")

            else -> ValidationResult.Success
        }
    }
}
