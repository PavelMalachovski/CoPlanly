package com.coparently.app.domain.error

import com.coparently.app.R
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.domain.usecase.ValidationException
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.toUiText
import com.coparently.app.utils.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * The event operations' error boundary (CQ-11): every failure is recorded, and classified into
 * the kind the screen words — never into text of its own.
 */
class ErrorHandlerTest {

    private val crashlytics = mockk<CrashlyticsManager>(relaxed = true)
    private val network = mockk<NetworkMonitor>()
    private val handler = ErrorHandler(crashlytics, network)

    @Test
    fun `a validator's refusal keeps the field it named`() {
        val error = handler.handleError(ValidationException("Event title cannot be empty", field = "title"))

        assertEquals(
            AppError.ValidationError(field = "title", validationMessage = "Event title cannot be empty"),
            error
        )
    }

    @Test
    fun `an I-O failure says whether the device was offline`() {
        val failure = IOException("timeout")

        every { network.isOnline() } returns false
        val offline = handler.handleError(failure)
        every { network.isOnline() } returns true
        val serverSide = handler.handleError(failure)

        assertEquals(AppError.NetworkError(originalException = failure, offline = true), offline)
        assertEquals(AppError.NetworkError(originalException = failure, offline = false), serverSide)
        // The two need different advice, so they must not read the same.
        assertNotEquals(offline.toUiText(), serverSide.toUiText())
    }

    @Test
    fun `an already classified error passes through unchanged`() {
        val classified = AppError.PermissionError()

        assertSame(classified, handler.handleError(classified))
    }

    @Test
    fun `anything else is unknown, worded generically, and recorded`() {
        val failure = IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions.")

        val error = handler.handleError(failure)

        assertEquals(AppError.UnknownError(originalException = failure), error)
        assertEquals(UiText.Res(R.string.common_error_generic), error.toUiText())
        verify(exactly = 1) { crashlytics.recordException(failure) }
    }

    @Test
    fun `a platform refusal is a permission error`() {
        val failure = SecurityException("denied")

        val error = handler.handleError(failure)

        assertEquals(AppError.PermissionError(originalException = failure), error)
        assertEquals(UiText.Res(R.string.common_error_permission), error.toUiText())
    }
}
