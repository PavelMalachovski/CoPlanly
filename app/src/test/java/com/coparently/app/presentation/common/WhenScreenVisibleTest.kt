package com.coparently.app.presentation.common

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.TimedRequest
import com.coparently.app.presentation.changerequests.CardClock
import com.coparently.app.presentation.changerequests.CardClockDetails
import com.coparently.app.presentation.changerequests.ClockRole
import com.coparently.app.presentation.changerequests.LocalCardClock
import com.coparently.app.testing.RobolectricTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a screen records on being seen — a sticker's "seen", a request clock's first opening — is
 * recorded only while the parent sees it (MON-24 phase 3, part B, final review). The iPhone's shell
 * keeps a hidden tab's screens composed and says so through `LocalScreenVisible`; Android provides
 * nothing, and [WhenScreenVisible] is then `LaunchedEffect(key)`, as the screens drew before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class WhenScreenVisibleTest {

    @get:Rule
    val rule = createComposeRule()

    private val seen = mutableListOf<String>()

    @Test
    fun `composed hidden it records nothing until the screen is shown`() {
        val visible: MutableState<Boolean> = mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalScreenVisible provides visible) {
                WhenScreenVisible(KEY) { seen += KEY }
            }
        }
        rule.waitForIdle()
        assertEquals(emptyList<String>(), seen)

        rule.runOnIdle { visible.value = true }
        rule.waitForIdle()
        assertEquals(listOf(KEY), seen)
    }

    @Test
    fun `visible it records once, and again only when shown after being hidden`() {
        val visible: MutableState<Boolean> = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalScreenVisible provides visible) {
                WhenScreenVisible(KEY) { seen += KEY }
            }
        }
        rule.waitForIdle()
        assertEquals(listOf(KEY), seen)

        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        assertEquals(listOf(KEY), seen)

        rule.runOnIdle { visible.value = true }
        rule.waitForIdle()
        assertEquals(listOf(KEY, KEY), seen)
    }

    @Test
    fun `a new key while hidden waits for the screen to be shown`() {
        val visible: MutableState<Boolean> = mutableStateOf(true)
        var key by mutableStateOf(KEY)
        rule.setContent {
            CompositionLocalProvider(LocalScreenVisible provides visible) {
                WhenScreenVisible(key) { seen += key }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle {
            visible.value = false
            key = OTHER
        }
        rule.waitForIdle()
        assertEquals(listOf(KEY), seen)

        rule.runOnIdle { visible.value = true }
        rule.waitForIdle()
        assertEquals(listOf(KEY, OTHER), seen)
    }

    @Test
    fun `with no visibility it runs once per key, as a LaunchedEffect`() {
        var key by mutableStateOf(KEY)
        rule.setContent { WhenScreenVisible(key) { seen += key } }
        rule.waitForIdle()
        rule.runOnIdle { }
        assertEquals(listOf(KEY), seen)

        rule.runOnIdle { key = OTHER }
        rule.waitForIdle()
        assertEquals(listOf(KEY, OTHER), seen)
    }

    @Test
    fun `an inbox card in a hidden tab records no opening until it is shown`() {
        val visible: MutableState<Boolean> = mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalScreenVisible provides visible, LocalCardClock provides cardClock()) {
                CardClockDetails()
            }
        }
        rule.waitForIdle()
        assertEquals(emptyList<String>(), seen)

        rule.runOnIdle { visible.value = true }
        rule.waitForIdle()
        assertEquals(listOf(CLOCK.id), seen)
    }

    @Test
    fun `on Android an inbox card records its opening once when drawn`() {
        rule.setContent {
            CompositionLocalProvider(LocalCardClock provides cardClock()) { CardClockDetails() }
        }
        rule.waitForIdle()
        assertEquals(listOf(CLOCK.id), seen)
    }

    private fun cardClock() = CardClock(
        timed = TimedRequest(CLOCK, NOW),
        role = ClockRole.RESPONDER,
        otherName = "Bob",
        onSeen = { seen += CLOCK.id },
        onGiveLastChance = null
    )

    private companion object {
        const val KEY = "swap_alice_group"
        const val OTHER = "proposal_alice_family"
        const val NOW = 1_778_000_000_000L
        const val HOUR = 3_600_000L

        /** A change request Bob sent Alice an hour ago, to be answered within three days. */
        val CLOCK = RequestDeadline(
            id = RequestDeadlineId.of(RequestKind.CHANGE, "bob", "r1", ""),
            kind = RequestKind.CHANGE,
            ref = "r1",
            familyId = "alice__bob",
            proposerUid = "bob",
            responderUid = "alice",
            answerByMillis = NOW + 72 * HOUR,
            createdAtMillis = NOW - HOUR,
            deliveredAtMillis = NOW - HOUR
        )
    }
}
