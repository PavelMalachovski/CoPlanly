package com.coparently.app.e2e

import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Message
import com.coparently.app.e2e.EmulatorEnvironment.step
import com.coparently.app.presentation.navigation.BottomNavDestination
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * Alice's **real app**, on screen, against the emulators; Bob on the data layer of a second phone
 * in the same process. The part of the two-phone round the other e2e tests could not reach: they
 * prove the data arrives, and this proves it is *drawn* — and that what Alice types into the real
 * composer reaches Bob. The setup is [AliceOnScreenTest]'s; `OnScreenAgreementsTest` and
 * `OnScreenFamiliesTest` do the same for the agreements and the family switcher.
 *
 * Still not covered, and kept on `docs/DEVICE-CHECKLIST.md`: the push that wakes a phone (FCM has
 * no emulator), and anything that needs two *screens* at once.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OneParentOnScreenTest : AliceOnScreenTest() {

    @Test
    fun whatBobSendsIsDrawnOnAlicesScreens_andWhatSheTypesReachesBob() {
        val bob = this.bob
        val conversationId = ConversationKey.of(aliceUid, bob.uid)

        // An event Bob saves for today appears on Alice's Home, once her sync has run.
        val eventTitle = "Dentist ${shortId()}"
        runBlocking {
            bob.eventRepository.insertEvent(eventForToday(bob, eventTitle))
            step("event: sync")
            syncService.performFullSync().getOrThrow()
        }
        waitFor("Bob's event on Alice's Home", hasText(eventTitle, substring = true))

        // A message Bob sends is drawn in Alice's thread.
        val fromBob = "Pickup moved to 5 pm ${shortId()}"
        runBlocking {
            bob.messageRepository.sendMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    senderId = bob.uid,
                    senderName = bob.name,
                    content = fromBob
                )
            )
        }
        step("chat: open the tab")
        openTab(BottomNavDestination.CHAT)
        waitFor("Bob's message in Alice's thread", hasText(fromBob))

        // What Alice types into the real composer arrives on Bob's phone.
        val fromAlice = "See you there ${shortId()}"
        step("chat: type and send")
        sendInChat(fromAlice)
        step("chat: wait for the reply on Bob's phone")
        runBlocking {
            withTimeout(EmulatorParent.WAIT_MS) {
                bob.messageRepository.observeMessages(conversationId).first { list ->
                    list.any { it.content == fromAlice && it.senderId == aliceUid }
                }
            }
        }
    }

    /** An event on today's date, owned by Bob's slot, starting soon enough to still be today. */
    private suspend fun eventForToday(bob: EmulatorParent, title: String): Event {
        val today = LocalDate.now()
        val start = minOf(LocalDateTime.now().plusMinutes(2), today.atTime(LATEST_START))
            .withSecond(0).withNano(0)
        return Event(
            id = UUID.randomUUID().toString(),
            title = title,
            startDateTime = start,
            endDateTime = start.plusMinutes(1),
            eventType = "appointment",
            parentOwner = bob.database.userDao().getUserById(bob.uid)?.role ?: "dad",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    private companion object {
        val LATEST_START: LocalTime = LocalTime.of(23, 58)
    }
}
