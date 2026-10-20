package com.coparently.app.domain.activity

import android.util.Log
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.domain.model.MessageType
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the co-parent what changed, from the place the change became durable.
 *
 * **Called from the repositories, never from a ViewModel.** A repository is where a change is
 * actually committed; a card posted from a screen does not appear when the same change arrives
 * from a sync, from an undo, or from a screen written later. One announcer means there is exactly
 * one way a change reaches the thread, which is what this package's own spec asks for.
 *
 * **It must never break its caller.** Every failure is logged and dropped. A parent's expense must
 * not fail to save because their co-parent's chat listener is down — the same discipline package A
 * applied to the password-save prompt, and the reason nothing here rethrows except cancellation.
 *
 * Nothing is announced when there is nobody to announce it to. An unpaired account has no
 * conversation, and this device's own changes are not announced back to it: a card telling you
 * what you just did is clutter in the one place both parents look.
 *
 * **Which thread.** Unless told otherwise, the family on screen: the signed-in user's `partnerId`,
 * which is what a screen wants. A background step acts for the family its request's clock names,
 * and the switcher may show another by then (spec §5.6), so it names the co-parent itself.
 *
 * @param messageRepository Posts the message, and inherits chat's existing delivery — an
 *   announcement is not retried on its own; see this package's spec, §8.
 * @param userRepository Resolves the signed-in uid and the co-parent's.
 */
@Singleton
class ActivityAnnouncer @Inject constructor(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) {

    /**
     * Posts [announcement] to a pair's thread, best-effort.
     *
     * @param announcement What changed.
     * @param senderName The signed-in parent's display name, for the bubble's attribution. Blank
     *   is tolerated: an unnamed sender is still worth announcing.
     * @param suppress True when this change must not be announced at all — a private event, or
     *   anything else the caller knows the co-parent must never see. Passed rather than inferred
     *   so each caller states its own rule at the call site, where the reader of that repository
     *   can check it.
     * @param partnerUid The co-parent whose thread with this parent receives the card, or null for
     *   the family on screen. A named co-parent is taken as given and the user's row is not
     *   consulted: whether that pairing is still live is the caller's to have checked, and the
     *   rules refuse a message once it has ended. An invalid uid (blank, or this parent's own)
     *   posts nothing, like any other failure here.
     */
    suspend fun announce(
        announcement: ActivityAnnouncement,
        senderName: String = "",
        suppress: Boolean = false,
        partnerUid: String? = null
    ) {
        if (suppress) return
        try {
            val myUid = userRepository.getCurrentUserId() ?: return
            val partner = partnerUid
                ?: userRepository.getUserById(myUid)?.partnerId?.takeIf { it.isNotBlank() }
                ?: return

            // Derived from the two uids, never generated. Randomly generated ids are what once
            // put the two phones on separate threads, each talking to nobody.
            val conversationId = ConversationKey.of(myUid, partner)
            // An expense split proposal's fallback names the proposer's own share, which only their
            // slot says; no other card reads the profile for its text.
            val senderSlot = if (announcement.kind == ActivityKind.SPLIT_PROPOSED) {
                userRepository.getUserById(myUid)?.role
            } else {
                null
            }

            messageRepository.sendMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    senderId = myUid,
                    senderName = senderName,
                    // The fallback an older build shows; this build's reader composes its own
                    // sentence from `activity` instead. See `ActivityFallbackText`.
                    content = ActivityFallbackText.of(announcement, senderSlot),
                    messageType = MessageType.ACTIVITY,
                    attachments = listOf(
                        announcement.entityType.name,
                        announcement.entityId
                    ),
                    status = MessageSendStatus.SENDING,
                    activity = announcement
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "Could not announce ${announcement.kind}; the change itself is saved", e)
        }
    }

    private companion object {
        const val TAG = "ActivityAnnouncer"
    }
}
