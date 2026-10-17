package com.coparently.app.qa

import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.Message
import com.coparently.app.e2e.EmulatorEnvironment
import com.coparently.app.e2e.EmulatorParent
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * What the family *writes*, through a co-parent's own production repositories — the records a
 * scenario then looks for on the screen of the parent on screen. Each record is stamped with the
 * family it belongs to, as the app's own forms stamp it, and the call returns once the server has
 * it, so the scenario's next step tests delivery rather than a race with the upload.
 */
internal object QaFamily {

    /** The slot [parent]'s own profile holds, as pairing assigned it. */
    suspend fun slotOf(parent: EmulatorParent): String =
        parent.database.userDao().getUserById(parent.uid)?.role ?: ContactWindow.SLOT_ONE

    /** An event [by] writes in their family with [partnerUid], starting at [start]. */
    suspend fun event(
        by: EmulatorParent,
        partnerUid: String,
        title: String,
        start: LocalDateTime,
        forMembers: List<FamilyMemberRef> = emptyList()
    ): Event {
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        val event = Event(
            id = UUID.randomUUID().toString(),
            title = title,
            startDateTime = start,
            endDateTime = start.plusHours(1),
            eventType = "other",
            parentOwner = slotOf(by),
            createdAt = now,
            updatedAt = now,
            createdByFirebaseUid = by.uid,
            lastModifiedBy = by.uid,
            forMembers = forMembers,
            familyId = FamilyKey.of(by.uid, partnerUid)
        )
        by.eventRepository.insertEvent(event)
        awaitDocument("events/${event.id}")
        return event
    }

    /** A shared expense [by] paid in their family with [partnerUid], divided evenly. */
    suspend fun expense(
        by: EmulatorParent,
        partnerUid: String,
        title: String,
        amount: Double,
        currency: String = "CZK",
        category: ExpenseCategory = ExpenseCategory.CLOTHING
    ): Expense {
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            title = title,
            amount = amount,
            currency = currency,
            category = category,
            paidBy = by.uid,
            splitBetween = listOf(by.uid, partnerUid),
            date = LocalDate.now(),
            createdByFirebaseUid = by.uid,
            splitBasisPoints = HALF_BASIS_POINTS,
            familyId = FamilyKey.of(by.uid, partnerUid)
        )
        by.expenseRepository.addExpense(expense)
        awaitDocument("expenses/${expense.id}")
        return expense
    }

    /** A child [by] enters in their family with [partnerUid]. */
    suspend fun child(by: EmulatorParent, partnerUid: String, name: String, born: LocalDate): ChildInfo {
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        val child = ChildInfo(
            id = UUID.randomUUID().toString(),
            childName = name,
            dateOfBirth = born.atStartOfDay(),
            createdAt = now,
            updatedAt = now,
            createdByFirebaseUid = by.uid,
            lastModifiedBy = by.uid,
            familyId = FamilyKey.of(by.uid, partnerUid)
        )
        by.childInfoRepository.upsertChildInfo(child)
        by.syncService.performFullSync().getOrThrow()
        awaitDocument("child_info/${child.id}")
        return child
    }

    /** A chat message [by] sends to [toUid] in their family's thread. */
    suspend fun message(by: EmulatorParent, toUid: String, text: String): Message {
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = ConversationKey.of(by.uid, toUid),
            senderId = by.uid,
            senderName = by.name,
            content = text
        )
        by.messageRepository.sendMessage(message)
        return message
    }

    private suspend fun awaitDocument(path: String) {
        withTimeout(EmulatorParent.WAIT_MS) {
            while (!EmulatorEnvironment.documentExists(path)) delay(POLL_MS)
        }
    }

    const val HALF_BASIS_POINTS = 5_000
    private const val POLL_MS = 250L
}
