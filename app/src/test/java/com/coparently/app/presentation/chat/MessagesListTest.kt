package com.coparently.app.presentation.chat

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.Message
import com.coparently.app.presentation.platform.ProvideAndroidPlatform
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.ContrastLevel
import com.coparently.app.testing.RobolectricTestApplication
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The thread is a reversed list (chat: newest message always in view, November 2026): it opens on
 * its newest message whatever the window holds, keeps a reader's place in history while messages
 * arrive, and offers a "newest messages" button that counts what arrived and brings it into view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class MessagesListTest {

    @get:Rule
    val rule = createComposeRule()

    private var messages by mutableStateOf(thread(MESSAGES))
    private var reveal by mutableStateOf<String?>(null)

    private fun compose() {
        rule.setContent {
            ProvideAndroidPlatform {
                CoPlanlyTheme(darkTheme = false, contrastLevel = ContrastLevel.STANDARD) {
                    MessagesList(
                        messages = messages,
                        currentUserId = ME,
                        canLoadEarlier = true,
                        onLoadEarlier = {},
                        // The composer's height taken off the window, as the chat screen does.
                        modifier = Modifier.height(LIST_HEIGHT.dp),
                        revealMessageId = reveal,
                        onRevealed = { reveal = null }
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `opens with the newest message displayed`() {
        compose()

        rule.onNodeWithText(textOf(MESSAGES - 1)).assertIsDisplayed()
        rule.onNodeWithContentDescription(NEWEST).assertDoesNotExist()
    }

    @Test
    fun `scrolled up the newest button shows and taps back`() {
        compose()
        scrollUp()

        rule.onNodeWithContentDescription(NEWEST).assertIsDisplayed().performClick()
        rule.waitForIdle()

        rule.onNodeWithText(textOf(MESSAGES - 1)).assertIsDisplayed()
        rule.onNodeWithContentDescription(NEWEST).assertDoesNotExist()
    }

    @Test
    fun `a new message while reading history leaves the list and counts on the button`() {
        compose()
        scrollUp()
        val reading = textOf(MESSAGES - 1 - FAR_UP)
        rule.onNodeWithText(reading).assertIsDisplayed()

        rule.runOnIdle { messages = messages + message(MESSAGES, from = THEM) + message(MESSAGES + 1, from = THEM) }
        rule.waitForIdle()

        rule.onNodeWithText(reading).assertIsDisplayed()
        rule.onNodeWithText(textOf(MESSAGES + 1)).assertDoesNotExist()
        rule.onNode(hasNewCount("2 new messages")).assertIsDisplayed()
    }

    @Test
    fun `a new message while at the newest comes into view`() {
        compose()

        rule.runOnIdle { messages = messages + message(MESSAGES, from = THEM) }
        rule.waitForIdle()

        rule.onNodeWithText(textOf(MESSAGES)).assertIsDisplayed()
    }

    @Test
    fun `one's own new message brings the newest into view from history`() {
        compose()
        scrollUp()

        rule.runOnIdle { messages = messages + message(MESSAGES, from = ME) }
        rule.waitForIdle()

        rule.onNodeWithText(textOf(MESSAGES)).assertIsDisplayed()
        rule.onNodeWithContentDescription(NEWEST).assertDoesNotExist()
    }

    @Test
    fun `a revealed message far up is shown clear of the newest button`() {
        compose()

        rule.runOnIdle { reveal = REVEALED }
        rule.waitForIdle()

        val target = rule.onNodeWithText(REVEALED_TEXT).assertIsDisplayed().getUnclippedBoundsInRoot()
        val button = rule.onNodeWithContentDescription(NEWEST).assertIsDisplayed().getUnclippedBoundsInRoot()
        // Vertically: the bubble reaches the end padding, where the button floats, while the text
        // node alone can end short of it.
        assertTrue(
            "The revealed message $target lies under the newest button $button",
            target.bottom <= button.top
        )
    }

    @Test
    fun `the reader's own message from another phone is not counted`() {
        compose()
        scrollUp()

        // The newest is the co-parent's, so nothing scrolls; the reader's own message beside it
        // (sent from another phone) is not news to them.
        rule.runOnIdle { messages = messages + message(MESSAGES, from = ME) + message(MESSAGES + 1, from = THEM) }
        rule.waitForIdle()

        rule.onNode(hasNewCount("1 new message")).assertIsDisplayed()
    }

    @Test
    fun `the count resets once the reader is back at the newest`() {
        compose()
        scrollUp()
        rule.runOnIdle { messages = messages + message(MESSAGES, from = THEM) + message(MESSAGES + 1, from = THEM) }
        rule.waitForIdle()
        rule.onNode(hasNewCount("2 new messages")).assertIsDisplayed()

        rule.onNodeWithContentDescription(NEWEST).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(textOf(MESSAGES + 1)).assertIsDisplayed()

        scrollUp()
        rule.onNodeWithContentDescription(NEWEST).assertIsDisplayed()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).assertDoesNotExist()
    }

    private fun scrollUp() {
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(FAR_UP)
        rule.waitForIdle()
    }

    private fun hasNewCount(count: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, count)

    private companion object {
        const val ME = "alice"
        const val THEM = "bob"
        const val MESSAGES = 60
        const val FAR_UP = 30
        const val LIST_HEIGHT = 400
        const val NEWEST = "Newest messages"

        /** An own message far up, so its bubble sits at the end side, where the button is. */
        const val REVEALED = "m11"
        const val REVEALED_TEXT = "Message number 11"

        /** 12:00 UTC on 2 November 2026, a minute apart, alternating senders so nothing groups. */
        const val NOON = 1_793_620_800_000L
        const val MINUTE = 60_000L

        fun textOf(index: Int) = "Message number $index"

        fun message(index: Int, from: String) = Message(
            id = "m$index",
            conversationId = "alice_bob",
            senderId = from,
            senderName = if (from == ME) "Alice" else "Bob",
            content = textOf(index),
            sentAtMillis = NOON + index * MINUTE
        )

        fun thread(count: Int) = (0 until count).map { message(it, from = if (it % 2 == 0) THEM else ME) }
    }
}
