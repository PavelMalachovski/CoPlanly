package com.coparently.app.data.local.dao

import androidx.room.Room
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.repository.toEntity
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.chat.ChatReadState
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The thread's unread count in Room (`MessageDao.observeUnreadCount`) counts what the thread draws
 * (November 2026): a parent's message and a sticker, never another card, and it agrees with
 * `ChatReadState.unreadCount`, the rule it repeats in SQL. Rows go in through the production mapper,
 * so the stored `activityJson` is the text `StoredJson` writes.
 */
@OptIn(InternalSharedApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class MessageDaoUnreadTest {

    private lateinit var database: CoPlanlyDatabase

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            CoPlanlyDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun close() {
        database.close()
    }

    private fun message(
        id: String,
        at: Long,
        sender: String = BOB,
        type: MessageType = MessageType.TEXT,
        activity: ActivityAnnouncement? = null
    ) = Message(
        id = id,
        conversationId = THREAD,
        senderId = sender,
        senderName = sender,
        content = id,
        sentAtMillis = at,
        messageType = type,
        activity = activity
    )

    private fun card(id: String, at: Long, kind: ActivityKind, title: String = "Dentist") = message(
        id,
        at,
        type = MessageType.ACTIVITY,
        activity = ActivityAnnouncement(kind, ActivityEntityType.EVENT, "e-$id", title)
    )

    private suspend fun store(messages: List<Message>) {
        messages.forEach { database.messageDao().insertMessage(it.toEntity()) }
    }

    private suspend fun unread(after: Long) = database.messageDao().observeUnreadCount(THREAD, ALICE, after).first()

    @Test
    fun `a card the thread does not draw is not unread`() = runBlocking {
        store(listOf(message("m1", 200), card("c1", 300, ActivityKind.EVENT_DECLINED)))

        assertEquals(1, unread(after = 100))
    }

    @Test
    fun `a sticker is unread like a message`() = runBlocking {
        store(listOf(card("s1", 200, ActivityKind.STICKER_SENT, title = "thank_you")))

        assertEquals(1, unread(after = 100))
    }

    @Test
    fun `a card typed as one with no payload is not unread`() = runBlocking {
        store(listOf(message("c1", 200, type = MessageType.ACTIVITY)))

        assertEquals(0, unread(after = 100))
    }

    @Test
    fun `a title that quotes the sticker's kind does not make a card count`() = runBlocking {
        store(listOf(card("c1", 200, ActivityKind.EVENT_CREATED, title = "\"kind\":\"STICKER_SENT\"")))

        assertEquals(0, unread(after = 100))
    }

    @Test
    fun `every card kind counts as the domain rule counts it`() = runBlocking {
        val messages = ActivityKind.entries.mapIndexed { index, kind -> card("c$index", 200L + index, kind) } +
            message("m1", 1_000) + message("m2", 1_001, sender = ALICE) + message("i1", 1_002, type = MessageType.IMAGE)
        store(messages)

        assertEquals(ChatReadState.unreadCount(messages, ALICE, 100), unread(after = 100))
        assertEquals(3, unread(after = 100))
    }

    private companion object {
        const val THREAD = "alice__bob"
        const val ALICE = "alice"
        const val BOB = "bob"
    }
}
