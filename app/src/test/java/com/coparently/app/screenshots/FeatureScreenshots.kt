package com.coparently.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.coparently.app.data.family.FamilyView
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.expenses.ExpenseBalance
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.domain.money.SupportedCurrency
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.telemetry.TelemetryConsent
import com.coparently.app.presentation.chat.ChatThreadHeader
import com.coparently.app.presentation.chat.MessageItem
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.consent.TelemetryConsentScreen
import com.coparently.app.presentation.consent.TelemetryConsentViewModel
import com.coparently.app.presentation.custody.SchedulePreviewMonth
import com.coparently.app.presentation.event.EventPreviewContent
import com.coparently.app.presentation.expenses.CollapsedMonthSummary
import com.coparently.app.presentation.expenses.ExpenseSummaryHeader
import com.coparently.app.presentation.expenses.MonthNavigation
import com.coparently.app.presentation.expenses.currencyFormat
import com.coparently.app.presentation.family.FamilyMoneyCard
import com.coparently.app.presentation.home.MonthMoney
import com.coparently.app.presentation.pairing.InvitationView
import com.coparently.app.presentation.settings.CUSTOM_COLOUR
import com.coparently.app.presentation.settings.ParentColorPicker
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One text-heavy surface from each remaining main area: the Expenses summary header and its
 * collapsed line, a chat thread (header plus an incoming, a read and a failed bubble), the event
 * preview sheet's body, the telemetry consent screen — the first screen a fresh install shows —
 * and the "My colour" picker with a colour of one's own.
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
            onSettleUp = {}
        )
    }

    /** The summary folded to its pinned line, with two currencies so the totals are joined. */
    @Test
    fun expenseCollapsedSummary() = snap("expenses_collapsed_summary") {
        CollapsedMonthSummary(
            navigation = MonthNavigation(
                label = ScreenshotFixtures.MONTH
                    .format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
                    .replaceFirstChar { it.uppercase() },
                expenseCount = EXPENSE_COUNT,
                onPrevious = {},
                onNext = {}
            ),
            totals = listOf(
                currencyFormat("CZK").format(MOM_PAID + DAD_PAID),
                currencyFormat("EUR").format(EUR_TOTAL)
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

    /**
     * A schedule as the custody editor and onboarding preview it: the month it starts in. The
     * start is years ahead so the month drawn never follows the real calendar.
     */
    @Test
    fun schedulePreview() = snap("custody_schedule_preview") {
        SchedulePreviewMonth(
            model = CustodyModel.weekOnWeekOff(id = "preview", startDate = PREVIEW_START, momFirst = true),
            parentNames = ScreenshotFixtures.parentNames,
            description = "Week on, week off: Olya has the first week."
        )
    }

    /**
     * The Family tab's first card: the month's spend in two currencies and who owes whom. Amounts
     * are never cut, so the 2.0× German variant shows how the card grows rather than clips.
     */
    @Test
    fun familyMoneyCard() = snap("family_money_card") {
        val month = listOf(
            familyExpense("shoes", MOM_PAID, "CZK"),
            familyExpense("ski", EUR_TOTAL, "EUR")
        )
        val money = MonthMoney.of(
            expenses = month,
            view = FamilyView(me = ScreenshotFixtures.MY_UID, onScreen = FAMILY_ID, others = emptySet()),
            today = ScreenshotFixtures.TODAY,
            fallbackCurrency = SupportedCurrency.CZK,
            userId = ScreenshotFixtures.MY_UID,
            roleByUid = mapOf(ScreenshotFixtures.MY_UID to "mom", ScreenshotFixtures.CO_PARENT_UID to "dad")
        )
        SectionGroup { FamilyMoneyCard(money = money, onOpen = {}) }
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

    /** The invitation an unlinked parent answers before linking (the invited parent's first screen). */
    @Test
    fun invitation() = snap("pairing_invitation", fullScreen = true) {
        InvitationView(inviterName = "Alice", onAnswer = {})
    }

    /**
     * "My colour" with Custom open: the four presets as the calendar draws them in this theme,
     * the custom swatch, and the hue strip without the calendar friend's teal.
     */
    @Test
    fun parentColorPicker() = snap("settings_parent_color_picker") {
        ParentColorPicker(chosen = CUSTOM_COLOUR, customHue = CUSTOM_HUE, onChoose = {}, onCustomHue = {})
    }

    /**
     * An incoming message, one of mine the co-parent has read, a card the app composed for the
     * co-parent's decline (drawn centred and neutral, not in either bubble), and one of mine that
     * failed.
     */
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
                "c1",
                ScreenshotFixtures.CO_PARENT_UID,
                "Event declined: Parent-teacher meeting",
                base + MINUTE_MS + MINUTE_MS / 2
            ).copy(
                activity = ActivityAnnouncement(
                    kind = ActivityKind.EVENT_DECLINED,
                    entityType = ActivityEntityType.EVENT,
                    entityId = "e1",
                    title = "Parent-teacher meeting"
                )
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

    /** A shared expense this parent paid in the family on screen, in May 2026. */
    private fun familyExpense(id: String, amount: Double, currency: String) = Expense(
        id = id,
        title = id,
        amount = amount,
        currency = currency,
        category = ExpenseCategory.CLOTHING,
        paidBy = ScreenshotFixtures.MY_UID,
        splitBetween = listOf(ScreenshotFixtures.MY_UID, ScreenshotFixtures.CO_PARENT_UID),
        date = ScreenshotFixtures.TODAY,
        createdByFirebaseUid = ScreenshotFixtures.MY_UID,
        familyId = FAMILY_ID
    )

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
        /** The family on screen in the money card's fixture. */
        private const val FAMILY_ID = "u1__u2"

        /** A Monday far enough ahead that the preview never draws today's month. */
        private val PREVIEW_START = LocalDate.of(2030, 5, 6)
        private const val MOM_PAID = 3_120.0
        private const val DAD_PAID = 1_480.0
        private const val OWED_TO_ME = 820.0
        private const val EUR_TOTAL = 145.0
        private const val EXPENSE_COUNT = 5
        private const val PREVIEW_HOUR = 15
        private const val MESSAGE_HOUR = 9
        private const val MINUTE_MS = 60_000L

        /** A sky blue: the owner's "blue, bright", on the strip between the presets' hues. */
        private const val CUSTOM_HUE = 200
        private val BUBBLE_GAP = 4.dp

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
