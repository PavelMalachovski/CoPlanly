package com.coparently.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.expenses.ExpenseBalance
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.telemetry.TelemetryConsent
import com.coparently.app.presentation.chat.ChatThreadHeader
import com.coparently.app.presentation.chat.MessageItem
import com.coparently.app.presentation.consent.TelemetryConsentScreen
import com.coparently.app.presentation.consent.TelemetryConsentViewModel
import com.coparently.app.presentation.event.EventPreviewContent
import com.coparently.app.presentation.expenses.ExpenseSummaryHeader
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One text-heavy surface from each remaining main area: the Expenses summary header, a chat
 * thread (header plus an incoming, a read and a failed bubble), the event preview sheet's body,
 * and the telemetry consent screen — the first screen a fresh install shows.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class FeatureScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    @Test
    fun expenseSummaryHeader() = snap("expenses_summary_header") {
        ExpenseSummaryHeader(
            balance = ExpenseBalance(
                momPaid = MOM_PAID,
                dadPaid = DAD_PAID,
                total = MOM_PAID + DAD_PAID,
                netForCurrentUser = OWED_TO_ME,
                splitKnown = true
            ),
            currency = "CZK",
            parentNames = ScreenshotFixtures.parentNames,
            onSettleUp = {},
            monthLabel = ScreenshotFixtures.MONTH.format(
                DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
            )
        )
    }

    @Test
    fun chatThread() = snap("chat_thread") {
        val messages = chatMessages()
        Column(verticalArrangement = Arrangement.spacedBy(BUBBLE_GAP)) {
            ChatThreadHeader(title = "Pavel", messages = messages, currentUserId = ScreenshotFixtures.MY_UID)
            messages.forEach { message ->
                MessageItem(message = message, isCurrentUser = message.senderId == ScreenshotFixtures.MY_UID)
            }
        }
    }

    @Test
    fun eventPreview() = snap("event_preview") {
        EventPreviewContent(
            event = ScreenshotFixtures.event(
                id = "p1",
                title = "Swimming lesson at the city pool",
                start = ScreenshotFixtures.TODAY.atTime(PREVIEW_HOUR, 30),
                owner = "dad"
            ).copy(
                description = "Bring the blue towel and the goggles.",
                forMembers = listOf(FamilyMemberRef.Child("c1"))
            ),
            parentNames = ScreenshotFixtures.parentNames,
            members = ScreenshotFixtures.members,
            onEdit = {},
            onDelete = {}
        )
    }

    @Test
    fun consentScreen() {
        val preferences = mockk<PreferencesRepository>(relaxed = true) {
            every { getTelemetryConsentFlow() } returns flowOf(TelemetryConsent.UNANSWERED)
        }
        val viewModel = TelemetryConsentViewModel(preferences)
        snap("consent_screen", fullScreen = true) {
            TelemetryConsentScreen(onAnswered = {}, viewModel = viewModel)
        }
    }

    /** An incoming message, one of mine the co-parent has read, and one of mine that failed. */
    private fun chatMessages(): List<Message> {
        val base = ScreenshotFixtures.TODAY.atTime(MESSAGE_HOUR, 0)
            .atZone(ZoneId.of("Europe/Prague")).toInstant().toEpochMilli()
        return listOf(
            message("m1", ScreenshotFixtures.CO_PARENT_UID, "Can you take her to swimming on Thursday?", base),
            message(
                "m2",
                ScreenshotFixtures.MY_UID,
                "Yes, I'll pick her up from school at three.",
                base + MINUTE_MS,
                MessageSendStatus.READ
            ),
            message(
                "m3",
                ScreenshotFixtures.MY_UID,
                "Towel is in her bag.",
                base + 2 * MINUTE_MS,
                MessageSendStatus.ERROR
            )
        )
    }

    private fun message(
        id: String,
        sender: String,
        text: String,
        sentAt: Long,
        status: MessageSendStatus = MessageSendStatus.SENT
    ) = Message(
        id = id,
        conversationId = "${ScreenshotFixtures.MY_UID}_${ScreenshotFixtures.CO_PARENT_UID}",
        senderId = sender,
        senderName = if (sender == ScreenshotFixtures.MY_UID) "Olya" else "Pavel",
        content = text,
        sentAtMillis = sentAt,
        status = status
    )

    companion object {
        private const val MOM_PAID = 3_120.0
        private const val DAD_PAID = 1_480.0
        private const val OWED_TO_ME = 820.0
        private const val PREVIEW_HOUR = 15
        private const val MESSAGE_HOUR = 9
        private const val MINUTE_MS = 60_000L
        private val BUBBLE_GAP = 4.dp

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
