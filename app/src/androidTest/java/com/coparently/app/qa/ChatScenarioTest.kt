package com.coparently.app.qa

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.R
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.model.Message
import com.coparently.app.e2e.EmulatorParent
import com.coparently.app.presentation.navigation.BottomNavDestination
import com.coparently.app.testing.exists
import com.coparently.app.testing.settle
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * The chat as two parents use it: a message that arrives unread and comes back read, the tab's
 * badge, search that ignores diacritics, the pause before sending that makes Undo real, a file,
 * and a card about an event that opens it.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChatScenarioTest : QaOnScreenTest() {

    private val thread: String
        get() = ConversationKey.of(aliceUid, bob.uid)

    @Test
    fun sendAndRead() {
        val text = "Vyzvednu Lenu ve 4 ${shortId()}"
        report.step("send", "Zuzana writes to Igor", "Igor's phone receives it") {
            openTab(BottomNavDestination.CHAT)
            sendInChat(text)
            runBlocking { awaitOnCoParent { list -> list.any { it.content == text && it.senderId == aliceUid } } }
        }
        report.step("ticks_read", "Igor reads it", "Zuzana's bubble says Read") {
            runBlocking { bob.messageRepository.markRead(thread, bob.uid) }
            awaitDelivered(this, "the read tick", hasContentDescription(string(R.string.chat_status_read)), expectsListener = true)
        }
    }

    @Test
    fun unreadBadgeAsIgor() {
        val text = "Lena má zítra kroužek ${shortId()}"
        val badge = hasContentDescription(strings.resources.getQuantityString(R.plurals.chat_unread_messages_badge, 1, 1), substring = true)
        report.step("badge_counts", "$coParentName writes while Igor is on Home", "The Chat tab shows one unread message") {
            runBlocking { QaFamily.message(bob, aliceUid, text) }
            awaitDelivered(this, "the unread badge", badge, expectsListener = true)
        }
        report.step("badge_clears", "Igor opens Chat", "The message is shown and the badge is gone") {
            openTab(BottomNavDestination.CHAT)
            waitFor("the message", hasText(text, substring = true))
            composeTestRule.pumpWhile { exists(badge) }
            check(!composeTestRule.exists(badge)) { "the badge is still shown" }
        }
    }

    @Test
    fun search() {
        val id = shortId()
        val text = "Máme čas v pátek? $id"
        report.step("diacritics_ignored", "Search \"cas\" for Igor's \"Máme čas v pátek?\"", "The message is found") {
            runBlocking { QaFamily.message(bob, aliceUid, text) }
            openTab(BottomNavDestination.CHAT)
            awaitDelivered(this, "Igor's message", hasText(text, substring = true), expectsListener = true)
            tap(hasContentDescription(string(R.string.chat_search)) and hasClickAction())
            composeTestRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("cas")
            composeTestRule.settle()
            waitFor("a search result", hasText(string(R.string.chat_search_show_in_thread)) and hasClickAction())
        }
    }

    @Test
    fun holdUndo() {
        val text = "Tohle nechci poslat ${shortId()}"
        report.step("undo_keeps_unsent", "Send, then press Undo during the pause", "Igor never receives it; the text is back in the composer") {
            openTab(BottomNavDestination.CHAT)
            composeTestRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
            composeTestRule.settle()
            tap(hasContentDescription(string(R.string.chat_send)) and hasClickAction())
            waitFor("Undo", hasText(string(R.string.chat_pending_send_undo)) and hasClickAction())
            tap(hasText(string(R.string.chat_pending_send_undo)) and hasClickAction())
            waitFor("the text back in the composer", hasSetTextAction() and hasText(text, substring = true))
            composeTestRule.mainClock.advanceTimeBy(HOLD_LAPSE_MS)
            val arrived = runBlocking {
                withTimeoutOrNull(QUIET_MS) { awaitOnCoParent { list -> list.any { it.content == text } } }
            }
            check(arrived == null) { "Igor received the message Zuzana undid" }
        }
    }

    @Test
    fun attachment() {
        report.step("uploads", "Attach a PDF and send it", "Igor's phone receives a message with the file") {
            val file = File(context.cacheDir, "shared_files/qa/plan-${shortId()}.pdf").apply {
                parentFile?.mkdirs()
                writeText(MINIMAL_PDF)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intents.init()
            try {
                intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                    Instrumentation.ActivityResult(
                        Activity.RESULT_OK,
                        Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                )
                openTab(BottomNavDestination.CHAT)
                tap(hasContentDescription(string(R.string.chat_attach)) and hasClickAction())
                waitFor("the send-this-file dialog", hasAnyAncestor(isDialog()) and hasText(string(R.string.chat_attach_send)))
                tap(dialogButton(R.string.chat_attach_send))
            } finally {
                Intents.release()
            }
            runBlocking { awaitOnCoParent { list -> list.any { it.senderId == aliceUid && it.attachments.isNotEmpty() } } }
            composeTestRule.pumpWhile { exists(hasText(string(R.string.chat_attach_waiting))) }
            check(!composeTestRule.exists(hasText(string(R.string.chat_attach_waiting)))) { "the bubble still says Not uploaded yet" }
        }
        report.step("failed_upload", "A file whose upload fails waits until it succeeds", "The message is held back until the file is stored") {
            couldNotRun("held to the e2e job's TwoParentAttachmentsTest; not walked on screen yet")
        }
    }

    @Test
    fun eventCardOpensEvent() {
        val title = "Zubař Lena ${shortId()}"
        report.step("card_opens_event", "Igor adds an event; Zuzana taps its card in the chat", "The event's preview opens") {
            runBlocking {
                QaFamily.event(bob, aliceUid, title, LocalDate.now().plusDays(1).atTime(EVENT_HOUR, 0))
                syncService.performFullSync().getOrThrow()
            }
            openTab(BottomNavDestination.CHAT)
            awaitDelivered(this, "the event's card", hasText(title, substring = true) and hasClickAction(), expectsListener = true)
            tap(hasText(title, substring = true) and hasClickAction())
            waitFor("the event preview", textButton(R.string.event_preview_edit))
        }
    }

    private suspend fun awaitOnCoParent(condition: (List<Message>) -> Boolean) =
        withTimeout(EmulatorParent.WAIT_MS) { bob.messageRepository.observeMessages(thread).first(condition) }

    /** Pumps Compose time while [condition] holds, for at most [QUIET_MS] of real time. */
    private fun androidx.compose.ui.test.junit4.ComposeTestRule.pumpWhile(
        condition: androidx.compose.ui.test.junit4.ComposeTestRule.() -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + QUIET_MS
        while (condition() && System.currentTimeMillis() < deadline) {
            mainClock.advanceTimeBy(PUMP_STEP_MS)
            Thread.sleep(PUMP_STEP_MS)
        }
    }

    private companion object {
        const val HOLD_LAPSE_MS = 15_000L
        const val QUIET_MS = 10_000L
        const val PUMP_STEP_MS = 100L
        const val EVENT_HOUR = 9

        /** A one-page PDF, the smallest a viewer opens; the app checks the type, not the content. */
        val MINIMAL_PDF = """
            %PDF-1.4
            1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
            2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
            3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]>>endobj
            trailer<</Root 1 0 R>>
            %%EOF
        """.trimIndent()
    }
}
