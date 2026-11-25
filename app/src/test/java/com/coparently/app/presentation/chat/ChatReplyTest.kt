package com.coparently.app.presentation.chat

import android.content.ClipboardManager
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
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
import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
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
                    onQuoteClick = { quoteTaps += it.messageId },
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
            quotes = mapOf("b" to shown("a", ReplyExcerpt.Text("Pick-up at five, then?")))
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
                        quote = shown("f", ReplyExcerpt.File("report.pdf")),
                        onOwnBubble = false,
                        readerId = ME,
                        onClick = {}
                    )
                    ReplyQuoteBlock(
                        quote = shown("s", ReplyExcerpt.Sticker),
                        onOwnBubble = true,
                        readerId = ME,
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
                    quote = shown("a", ReplyExcerpt.Text("Pick-up at five?")),
                    readerId = ME,
                    onCancel = { cancelled++ }
                )
            }
        }

        rule.onNodeWithText("Reply to Bob").assertIsDisplayed()
        rule.onNodeWithText("Pick-up at five?").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cancel reply").performClick()

        assertEquals(1, cancelled)
    }

    @Test
    fun `the reader's own message is quoted as you, never by their name`() {
        rule.setContent {
            Themed {
                androidx.compose.foundation.layout.Column {
                    ReplyQuoteBlock(
                        quote = shown("a", ReplyExcerpt.Text("On my way"), from = ME),
                        onOwnBubble = false,
                        readerId = ME,
                        onClick = {}
                    )
                    ReplyBar(
                        quote = shown("a", ReplyExcerpt.Text("On my way"), from = ME),
                        readerId = ME,
                        onCancel = {}
                    )
                }
            }
        }

        rule.onNodeWithText("You").assertIsDisplayed()
        rule.onNodeWithText("Reply to your message").assertIsDisplayed()
        rule.onNodeWithText("Alice", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a quote and the reply bar name the co-parent as the family does now - not as the message stored`() {
        val names = ParentNames(
            parents = Parents(
                me = NamedParent(uid = ME, slot = "mom", name = "Alice"),
                coParent = NamedParent(uid = THEM, slot = "dad", name = "Robert"),
                isPaired = true,
                loaded = true
            ),
            youFallback = "You",
            coParentFallback = "Co-parent",
            unknownFallback = "Parent"
        )
        rule.setContent {
            Themed {
                androidx.compose.foundation.layout.Column {
                    MessagesList(
                        messages = listOf(
                            message("a", "Pick-up at five?", from = THEM),
                            message("b", "Yes", replyTo = "a")
                        ),
                        currentUserId = ME,
                        modifier = Modifier.height(300.dp),
                        quotes = mapOf("b" to shown("a", ReplyExcerpt.Text("Pick-up at five?"))),
                        names = names
                    )
                    ReplyBar(
                        quote = shown("a", ReplyExcerpt.Text("Pick-up at five?")),
                        readerId = ME,
                        onCancel = {},
                        names = names
                    )
                }
            }
        }

        rule.onNodeWithText("Robert").assertIsDisplayed()
        rule.onNodeWithText("Reply to Robert").assertIsDisplayed()
        rule.onNodeWithText("Bob", substring = true).assertDoesNotExist()
    }

    @Test
    fun `long press highlights the pressed bubble and dims the others until the menu closes`() {
        compose(listOf(message("a", "Pick-up at five?"), message("b", "See you", from = THEM)))
        rule.onNode(SemanticsMatcher.keyIsDefined(BubbleEmphasisKey)).assertDoesNotExist()

        rule.onNodeWithText("Pick-up at five?").performTouchInput { longClick() }
        rule.waitForIdle()

        rule.onNode(emphasis(BubbleEmphasis.PRESSED) and hasAnyDescendant(hasText("Pick-up at five?")))
            .assertExists()
        rule.onNode(emphasis(BubbleEmphasis.DIMMED) and hasAnyDescendant(hasText("See you"))).assertExists()

        rule.onNodeWithText(REPLY).performClick()
        rule.waitForIdle()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(BubbleEmphasisKey)).assertCountEquals(0)
    }

    @Test
    fun `a drag that starts leftward leaves the bubble where it is`() {
        compose(listOf(message("a", "Pick-up at five?", from = THEM)))
        val text = rule.onNodeWithText("Pick-up at five?", useUnmergedTree = true)
        val resting = text.getUnclippedBoundsInRoot().left

        text.performTouchInput {
            down(center)
            moveBy(Offset(-DRAG_SLOPS * viewConfiguration.touchSlop, 0f))
        }
        rule.waitForIdle()
        assertEquals(resting, text.getUnclippedBoundsInRoot().left)
        text.performTouchInput { up() }

        // The same drag to the right does move it, so the check above can see a move.
        text.performTouchInput {
            down(center)
            moveBy(Offset(DRAG_SLOPS * viewConfiguration.touchSlop, 0f))
        }
        rule.waitForIdle()
        assertTrue(text.getUnclippedBoundsInRoot().left > resting)
        text.performTouchInput { up() }
    }

    @Test
    fun `a quote's original pages older than the window is revealed once the window reaches it`() {
        val recent = (OLDER until OLDER + WINDOW).map { message("m$it", "Message $it", at = it) }
        val older = (0 until OLDER).map { message("m$it", "Message $it", at = it) }
        val reply = message("r", "Yes", replyTo = "m3", at = OLDER + WINDOW)
        var messages by mutableStateOf(recent + reply)
        var reveal by mutableStateOf<String?>(null)
        rule.setContent {
            Themed {
                MessagesList(
                    messages = messages,
                    currentUserId = ME,
                    modifier = Modifier.height(LIST_HEIGHT.dp),
                    revealMessageId = reveal,
                    onRevealed = { reveal = null },
                    quotes = mapOf("r" to shown("m3", ReplyExcerpt.Text("Message 3"))),
                    // What the chat screen does with the jump: the window is sized to reach the
                    // original in one step, then it is revealed.
                    onQuoteClick = { original ->
                        reveal = original.messageId
                        messages = older + recent + reply
                    }
                )
            }
        }

        rule.onNodeWithText("Message 3").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Message 3", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Yes").assertDoesNotExist()
    }

    private fun emphasis(value: BubbleEmphasis) = SemanticsMatcher.expectValue(BubbleEmphasisKey, value)

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

        /** A thread of [OLDER] messages before the loaded [WINDOW], a minute apart. */
        const val OLDER = 150
        const val WINDOW = 50

        /** One move just past the touch slop, so it is the first move a drag reports. */
        const val DRAG_SLOPS = 1.5f

        fun message(
            id: String,
            text: String,
            from: String = ME,
            replyTo: String? = null,
            at: Int = id.hashCode().mod(60)
        ) = Message(
            id = id,
            conversationId = "alice_bob",
            senderId = from,
            senderName = if (from == ME) "Alice" else "Bob",
            content = text,
            sentAtMillis = NOON + at * 60_000L,
            replyToMessageId = replyTo
        )

        fun shown(id: String, excerpt: ReplyExcerpt, from: String = THEM) = ReplyQuote.Shown(
            messageId = id,
            authorName = if (from == ME) "Alice" else "Bob",
            excerpt = excerpt,
            authorId = from,
            sentAtMillis = NOON
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
