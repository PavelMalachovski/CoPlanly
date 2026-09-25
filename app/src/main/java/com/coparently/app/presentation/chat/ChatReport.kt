package com.coparently.app.presentation.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.coparently.app.BuildConfig
import com.coparently.app.R
import com.coparently.app.domain.chat.MessageReport
import com.coparently.app.domain.model.Message
import com.coparently.app.presentation.common.AppMessages
import com.coparently.app.presentation.common.LocalAppMessages

private const val TAG = "ChatReport"

/**
 * The mailbox a chat message is reported to (`BuildConfig.SUPPORT_EMAIL`), or null while the build
 * names none. Null hides "Report message" everywhere: a report with nowhere to go is the affordance
 * design item 8 forbids.
 */
internal val chatReportAddress: String? = BuildConfig.SUPPORT_EMAIL.trim().takeIf { it.isNotEmpty() }

/**
 * Reports a message, or null when reporting is not offered (no support address, or outside a
 * thread). A CompositionLocal rather than a parameter for the reason [LocalChatAttachments] gives:
 * `MessageItem`'s signature is what the detekt baseline keys its entries on.
 */
val LocalChatMessageReport = compositionLocalOf<((Message) -> Unit)?> { null }

/**
 * Offers "Report message" to the thread in [content] (play-final audit F-10): the parent's own
 * email app opens to the support address with the message's id, conversation id and time — never
 * its text (see [MessageReport]). With no email app the reader is told the address instead.
 * Provides nothing while [chatReportAddress] is null.
 */
@Composable
fun ChatReportHost(content: @Composable () -> Unit) {
    val address = chatReportAddress
    if (address == null) {
        content()
        return
    }
    // The Activity's context, so the subject and body follow the per-app language.
    val context = LocalContext.current
    val appMessages = LocalAppMessages.current
    val report = remember(context, appMessages, address) {
        val send: (Message) -> Unit = { message -> reportMessage(context, appMessages, address, message) }
        send
    }
    CompositionLocalProvider(LocalChatMessageReport provides report, content = content)
}

private fun reportMessage(context: Context, appMessages: AppMessages?, address: String, message: Message) {
    val subject = context.getString(R.string.chat_report_subject)
    val body = context.getString(
        R.string.chat_report_body,
        message.id,
        message.conversationId,
        MessageReport.sentAtUtc(message.sentAtMillis)
    )
    // The address, subject and body ride in the URI (RFC 6068) and again as extras, because mail
    // apps differ in which of the two they read.
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(MessageReport.mailtoUri(address, subject, body)))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No email app can send the report", e)
        appMessages?.show(context.getString(R.string.chat_report_no_email_app, address))
    }
}

/**
 * Wraps a message bubble so a long press (or TalkBack's actions menu) offers "Report message" —
 * only for a message [MessageReport.isReportable] accepts, and only inside a [ChatReportHost] that
 * has an address. Otherwise it draws [content] unchanged.
 *
 * @param message The message the bubble shows
 * @param isCurrentUser Whether the reader sent it
 * @param content The bubble
 */
@Composable
internal fun ReportableBubble(message: Message, isCurrentUser: Boolean, content: @Composable () -> Unit) {
    val report = LocalChatMessageReport.current
    if (report == null || !MessageReport.isReportable(message, isCurrentUser)) {
        content()
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val label = stringResource(R.string.chat_report_message)
    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                )
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label) {
                        menuOpen = true
                        true
                    }
                )
            }
    ) {
        content()
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    report(message)
                }
            )
        }
    }
}

/**
 * The thread header's overflow: "Block and stop sharing", which opens the existing unpair
 * confirmation. Blocking *is* unpairing — the rules refuse a message on a thread whose pairing
 * has ended — so there is no second mechanism. Red, and it confirms before anything happens,
 * as every destructive action does (design item 8).
 *
 * @param onBlock Opens the unpair confirmation
 */
@Composable
internal fun ChatOverflowMenu(onBlock: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.chat_more_actions)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.chat_block),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    open = false
                    onBlock()
                }
            )
        }
    }
}
