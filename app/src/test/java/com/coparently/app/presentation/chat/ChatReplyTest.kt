package com.coparently.app.presentation.chat

import android.content.ClipboardManager
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.coparently.app.domain.chat.ChatAttachment
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.chat.ReplyExcerpt
import com.coparently.app.domain.chat.ReplyQuote
import com.coparently.app.domain.model.Message
import com.coparently.app.presentation.platform.ProvideAndroidPlatform
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.ContrastLevel
import com.coparently.app.testing.RobolectricTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Replies in the thread (chat: newest message in view, and replies, November 2026): a bubble starts
 * a reply by a swipe to the right or from its long-press menu, a reply's bubble quotes its original
 * and a tap on the quote asks for that original, and the reply bar over the composer names whom the
 * parent answers and cancels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class ChatReplyTest {

    @get:Rule
    val rule = createComposeRule()

    private val replied = mutableListOf<String>()
    private val quoteTaps = mutableListOf<String>()

    private fun compose(messages: List<Message>, quotes: Map<String, ReplyQuote> = emptyMap()) {
        rule.setContent {
            Themed {
                MessagesList(
                    messages = messages,
                    currentUserId = ME,
                    modifier = Modifier.height(LIST_HEIGHT.dp),
                    quotes = quotes,
                    onQuoteClick = { quoteTaps += it },
                    onReply = { replied += it.id }
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a swipe past the threshold starts a reply`() {
        compose(listOf(message("a", "Pick-up at five?")))

        rule.onNodeWithText("Pick-up at five?").performTouchInput { swipeRight(endX = 70.dp.toPx()) }
        rule.waitForIdle()

        assertEquals(listOf("a"), replied)
    }

    @Test
    fun `a short or vertical swipe starts none`() {
        compose(listOf(message("a", "Pick-up at five?")))
        val bubble = rule.onNodeWithText("Pick-up at five?")

        bubble.performTouchInput { swipeRight(endX = 30.dp.toPx()) }
        bubble.performTouchInput { swipeUp() }
        bubble.performTouchInput { swipeDown() }
        rule.waitForIdle()

        assertTrue("A swipe short of the threshold or along the list replied: $replied", replied.isEmpty())
    }

    @Test
    fun `long press offers reply and copy for text only`() {
        compose(listOf(message("a", "Pick-up at five?"), fileMessage("f", "report.pdf")))

        rule.onNodeWithText("Pick-up at five?").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithText(COPY).assertIsDisplayed().performClick()
        rule.waitForIdle()
        val clipboard = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(ClipboardManager::class.java)
        assertEquals("Pick-up at five?", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        rule.onNodeWithText("Pick-up at five?").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithText(REPLY).assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(listOf("a"), replied)

        rule.onNodeWithText("report.pdf").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithText(REPLY).assertIsDisplayed()
        rule.onNodeWithText(COPY).assertDoesNotExist()
    }

    @Test
    fun `every bubble offers reply to TalkBack`() {
        compose(listOf(message("a", "Pick-up at five?")))

        val actions = rule.onNode(hasCustomAction(REPLY)).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { actions.first { it.label == REPLY }.action() }

        assertEquals(listOf("a"), replied)
    }

    @Test
    fun `tapping a quote asks for its original`() {
        compose(
            messages = listOf(message("a", "Pick-up at five?", from = THEM), message("b", "Yes", replyTo = "a")),
            quotes = mapOf("b" to ReplyQuote.Shown("a", "Bob", ReplyExcerpt.Text("Pick-up at five, then?")))
        )

        rule.onNodeWithText("Pick-up at five, then?").assertIsDisplayed().performClick()

        assertEquals(listOf("a"), quoteTaps)
    }

    @Test
    fun `an unavailable quote says so and does nothing`() {
        compose(
            messages = listOf(message("b", "Yes", replyTo = "gone")),
            quotes = mapOf("b" to ReplyQuote.Unavailable)
        )

        rule.onNodeWithText(UNAVAILABLE).assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun `a quote words a file and a sticker`() {
        rule.setContent {
            Themed {
                androidx.compose.foundation.layout.Column {
                    ReplyQuoteBlock(
                        quote = ReplyQuote.Shown("f", "Bob", ReplyExcerpt.File("report.pdf")),
                        onOwnBubble = false,
                        onClick = {}
                    )
                    ReplyQuoteBlock(
                        quote = ReplyQuote.Shown("s", "Bob", ReplyExcerpt.Sticker),
                        onOwnBubble = true,
                        onClick = {}
                    )
                }
            }
        }

        rule.onNodeWithText("File: report.pdf").assertIsDisplayed()
        rule.onNodeWithText("Sticker").assertIsDisplayed()
    }

    @Test
    fun `the reply bar names the author and cancels`() {
        var cancelled = 0
        rule.setContent {
            Themed {
                ReplyBar(
                    quote = ReplyQuote.Shown("a", "Bob", ReplyExcerpt.Text("Pick-up at five?")),
                    onCancel = { cancelled++ }
                )
            }
        }

        rule.onNodeWithText("Reply to Bob").assertIsDisplayed()
        rule.onNodeWithText("Pick-up at five?").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cancel reply").performClick()

        assertEquals(1, cancelled)
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        ProvideAndroidPlatform {
            CoPlanlyTheme(darkTheme = false, contrastLevel = ContrastLevel.STANDARD, content = content)
        }
    }

    private fun hasCustomAction(label: String) = SemanticsMatcher("has the custom action $label") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == label }
    }

    private companion object {
        const val ME = "alice"
        const val THEM = "bob"
        const val LIST_HEIGHT = 600
        const val REPLY = "Reply"
        const val COPY = "Copy text"
        const val UNAVAILABLE = "Message unavailable"

        /** 12:00 UTC on 2 November 2026. */
        const val NOON = 1_793_620_800_000L

        fun message(id: String, text: String, from: String = ME, replyTo: String? = null) = Message(
            id = id,
            conversationId = "alice_bob",
            senderId = from,
            senderName = if (from == ME) "Alice" else "Bob",
            content = text,
            sentAtMillis = NOON + id.hashCode().toLong().mod(60) * 60_000L,
            replyToMessageId = replyTo
        )

        fun fileMessage(id: String, name: String) = message(id, name, from = THEM).copy(
            attachments = listOf(
                ChatAttachmentCodec.encode(
                    ChatAttachment(
                        storagePath = "chat_attachments/alice_bob/$id/$name",
                        contentType = "application/pdf",
                        sizeBytes = 1024,
                        sha256 = "0".repeat(64),
                        fileName = name
                    )
                )
            )
        )
    }
}
