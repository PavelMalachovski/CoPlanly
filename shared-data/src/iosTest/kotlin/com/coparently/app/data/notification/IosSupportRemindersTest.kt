package com.coparently.app.data.notification

import com.coparently.app.data.platform.MemoryTextStore
import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.support.SupportRead
import com.coparently.app.data.support.SupportReminders
import com.coparently.app.domain.changerequests.ReminderPlan
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentId
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.domain.support.SupportAgreement
import com.coparently.app.domain.support.SupportTerms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The payer's child support reminder on the iPhone (iPhone phase 4 part C, design §7.5) over a fake
 * notification center: at 09:00 three days before the 15th, never for a month paid, never in the
 * quiet hours, once a month, removed when "I paid" is recorded, kept when the server cannot be
 * read, and only for the payer.
 */
class IosSupportRemindersTest {

    private val zone = TimeZone.UTC
    private val center = FakeNotificationCenter(nowMillis = millis(LocalDateTime(2026, 11, 10, 10, 0)))
    private val reminders = SupportReminders(SealedSecurePreferences(MemoryTextStore()))
    private val payments = mutableMapOf<String, Payment>()
    private var agreementRead: SupportRead? = known(payer = ALICE)
    private var paymentsReadable = true
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val planner = SupportReminderPlanner(
        reminders = reminders,
        uid = { ALICE },
        agreement = { agreementRead },
        payment = { id ->
            if (paymentsReadable) Result.success(payments[id]) else Result.failure(IllegalStateException("offline"))
        },
        payeeName = { uid -> if (uid == BOB) "Bob" else null },
        wording = ::echoWording,
        zone = { zone },
    )

    private val book = IosLocalReminders(
        center = center,
        events = { emptyList() },
        support = { now -> planner.plan(now) },
        onCancelAll = { reminders.forgetArmed() },
        scope = scope,
        now = { center.nowMillis },
    )

    init {
        reminders.set(ALICE, FAMILY, DAYS_BEFORE)
    }

    @AfterTest
    fun stop() {
        scope.cancel()
    }

    @Test
    fun theReminderIsArmedOnItsDayOutsideTheQuietHours() = runBlocking {
        book.rearm()

        val armed = center.requests.getValue("support-$FAMILY-2026-11")
        assertEquals(millis(LocalDateTime(2026, 11, 12, 9, 0)), armed.atMillis)
        assertEquals("support", armed.data["coplanly.destination"])
        assertEquals(FAMILY, armed.data["familyId"])
        assertEquals(ALICE, armed.data["targetUserId"])
        assertEquals(1, center.requests.size)
    }

    @Test
    fun theReminderTextIsReminderWordingsInTheAppsLanguage() = runBlocking {
        book.rearm()

        val armed = center.requests.getValue("support-$FAMILY-2026-11")
        val due = LocalDate(2026, 11, 15).atStartOfDayIn(zone).toEpochMilliseconds()
        val expected = echoWording().support(due, "5000.00", "CZK", "Bob")
        assertEquals(expected.title, armed.title)
        assertEquals(expected.body, armed.body)
        assertEquals("support_reminder_body(5000.00 CZK|Bob)", armed.body)
    }

    @Test
    fun theSupportReminderSkipsAPaidMonth() = runBlocking {
        pay(YearMonth(2026, 11))

        book.rearm()

        assertEquals(setOf("support-$FAMILY-2026-12"), center.requests.keys)
        val december = center.requests.getValue("support-$FAMILY-2026-12")
        assertEquals(millis(LocalDateTime(2026, 12, 12, 9, 0)), december.atMillis)
    }

    @Test
    fun theSupportReminderIsOutsideTheQuietHours() = runBlocking {
        // Past the reminder's day at night, not reminded and not paid: the next morning, never now.
        center.nowMillis = millis(LocalDateTime(2026, 11, 13, 22, 30))

        book.rearm()

        val armed = center.requests.getValue("support-$FAMILY-2026-11")
        assertEquals(millis(LocalDateTime(2026, 11, 14, 9, 0)), armed.atMillis)
        assertFalse(ReminderPlan.isQuietAt(armed.atMillis, zone))
    }

    @Test
    fun aReminderWhoseTimePassedInTheDayComesSoon() = runBlocking {
        center.nowMillis = millis(LocalDateTime(2026, 11, 13, 12, 0))

        book.rearm()

        assertEquals(center.nowMillis + MINUTE_MILLIS, center.requests.getValue("support-$FAMILY-2026-11").atMillis)
    }

    @Test
    fun aLateReminderOnAMonthsLastEveningIsDroppedForTheNextMonth() = runBlocking {
        // November's reminder is late at 22:30 on the 30th: the next allowed time is 1 December,
        // about a due day a month gone, so November's is dropped and December's is armed.
        center.nowMillis = millis(LocalDateTime(2026, 11, 30, 22, 30))

        book.rearm()

        assertEquals(setOf("support-$FAMILY-2026-12"), center.requests.keys)
        val december = center.requests.getValue("support-$FAMILY-2026-12")
        assertEquals(millis(LocalDateTime(2026, 12, 12, 9, 0)), december.atMillis)
    }

    @Test
    fun theArmedRecordIsWrittenOnlyOnceArmed() = runBlocking {
        center.failPending = true

        book.rearm()

        assertTrue(center.requests.isEmpty())
        assertNull(reminders.armed(ALICE, FAMILY))

        center.failPending = false
        book.rearm()

        assertEquals(millis(LocalDateTime(2026, 11, 12, 9, 0)), reminders.armed(ALICE, FAMILY)?.atMillis)
    }

    @Test
    fun recordingIPaidCancelsIt() = runBlocking {
        book.rearm()
        assertEquals(setOf("support-$FAMILY-2026-11"), center.requests.keys)

        // "I paid" landed on this phone; the screen asks the scheduler to decide again.
        pay(YearMonth(2026, 11))
        book.rearm()

        assertFalse("support-$FAMILY-2026-11" in center.requests)
        assertEquals(setOf("support-$FAMILY-2026-12"), center.requests.keys)
    }

    @Test
    fun aMonthIsRemindedOfOnce() = runBlocking {
        book.rearm()
        // The system showed it.
        center.nowMillis = millis(LocalDateTime(2026, 11, 12, 9, 30))

        book.rearm()

        assertEquals(YearMonth(2026, 11), reminders.lastReminded(ALICE, FAMILY))
        assertEquals(listOf("support-$FAMILY-2026-12"), center.waiting().map { it.id })
    }

    @Test
    fun aReminderTurnedOffIsRemoved() = runBlocking {
        book.rearm()

        reminders.set(ALICE, FAMILY, null)
        book.rearm()

        assertTrue(center.waiting().isEmpty())
        assertEquals(null, reminders.armed(ALICE, FAMILY))
    }

    @Test
    fun aServerThatCannotBeReadKeepsTheArmedReminder() = runBlocking {
        book.rearm()

        agreementRead = null
        book.rearm()
        assertEquals(setOf("support-$FAMILY-2026-11"), center.requests.keys)

        agreementRead = known(payer = ALICE)
        paymentsReadable = false
        book.rearm()
        assertEquals(setOf("support-$FAMILY-2026-11"), center.requests.keys)
    }

    @Test
    fun onlyThePayerIsReminded() = runBlocking {
        agreementRead = known(payer = BOB)

        book.rearm()

        assertTrue(center.requests.isEmpty())
    }

    @Test
    fun cancelAllForgetsWhatWasArmed() = runBlocking {
        book.rearm()

        book.cancelAll()

        assertTrue(center.requests.isEmpty())
        assertEquals(null, reminders.armed(ALICE, FAMILY))
    }

    private fun pay(month: YearMonth) {
        val id = PaymentId.of(FAMILY, PaymentKind.SUPPORT, month, "CZK")
        payments[id] = Payment(id, FAMILY, PaymentKind.SUPPORT, month, "CZK", AMOUNT, ALICE, BOB)
    }

    private fun known(payer: String) = SupportRead.Known(
        SupportAgreement(
            familyId = FAMILY,
            participants = listOf(ALICE, BOB),
            agreement = SupportTerms(AMOUNT, "CZK", DUE_DAY, payer, YearMonth(2026, 1)),
        )
    )

    private fun millis(time: LocalDateTime): Long = time.toInstant(zone).toEpochMilliseconds()

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
        const val FAMILY = "alice__bob"
        const val AMOUNT = 500_000L
        const val DUE_DAY = 15
        const val DAYS_BEFORE = 3
        const val MINUTE_MILLIS = 60_000L
    }
}
