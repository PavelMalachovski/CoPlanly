package com.coparently.app.data.stickers

import com.coparently.app.data.requests.RequestTime
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.domain.stickers.StickerAwards
import com.coparently.app.domain.stickers.StickerKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The app's stickers ride on an answer: on time once a week, regularity after four weeks in a row,
 * and nothing for a late answer, a request with no clock, or one this parent did not have to answer.
 */
class StickerAwarderTest {

    private val today = LocalDate.of(2026, 10, 23)
    private val now = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val week = StickerAwards.isoWeek(today)
    private val clocks = mockk<RequestDeadlineRepository>()
    private val store = mockk<StickerStore>(relaxed = true)
    private val awarder = StickerAwarder(clocks, store, RequestTime({ now }, { ZoneOffset.UTC }, emptyFlow()))

    private val clock = RequestDeadline(
        id = "swap_uA_g",
        kind = RequestKind.SWAP,
        ref = "g",
        familyId = "uA__uB",
        proposerUid = "uA",
        responderUid = "uB",
        answerByMillis = now + 60_000L
    )

    private fun onTime(weeksAgo: Long) = StickerAwards.isoWeek(today.minusWeeks(weeksAgo)).let { w ->
        Sticker(StickerAwards.appStickerId("uB", StickerKind.ON_TIME, w), "uB", null, "uA__uB", StickerKind.ON_TIME)
    }

    private fun given(received: List<Sticker> = emptyList(), clock: RequestDeadline? = this.clock) {
        every { store.signedInUid() } returns "uB"
        coEvery { clocks.get("swap_uA_g") } returns clock
        coEvery { store.receivedOnce() } returns received
        coEvery { store.award(any(), any(), any()) } returns true
        every { clocks.observeFamily("uB", "uA__uB") } returns flowOf(emptyList())
    }

    @Test
    fun `an answer before the deadline gives this week's on-time sticker`() = runTest {
        given()
        awarder.onAnswered("swap_uA_g")
        coVerify { store.award("app_uB_on_time_$week", StickerKind.ON_TIME, "uA__uB") }
        coVerify(exactly = 0) { store.award(any(), StickerKind.REGULAR, any()) }
    }

    @Test
    fun `a second answer in the same week gives nothing new`() = runTest {
        given(received = listOf(onTime(0)))
        awarder.onAnswered("swap_uA_g")
        coVerify(exactly = 0) { store.award(any(), any(), any()) }
    }

    @Test
    fun `the fourth week in a row also gives regularity`() = runTest {
        given(received = listOf(onTime(1), onTime(2), onTime(3)))
        awarder.onAnswered("swap_uA_g")
        coVerify { store.award("app_uB_regular_$week", StickerKind.REGULAR, "uA__uB") }
    }

    @Test
    fun `a late answer, another parent's request or no clock gives nothing`() = runTest {
        given(clock = clock.copy(answerByMillis = now - 1))
        awarder.onAnswered("swap_uA_g")
        given(clock = clock.copy(responderUid = "uA"))
        awarder.onAnswered("swap_uA_g")
        given(clock = null)
        awarder.onAnswered("swap_uA_g")
        awarder.onAnswered(null)
        coVerify(exactly = 0) { store.award(any(), any(), any()) }
    }
}
