package com.coparently.app.presentation.chat

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.coparently.app.domain.chat.MessageReport
import com.coparently.app.domain.model.Message
import com.coparently.app.presentation.common.BarMenuItem
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.presentation.platform.LocalAppBuildValues
import com.coparently.app.presentation.platform.LocalOpenUrl
import com.coparently.app.resources.Res
import com.coparently.app.resources.chat_block
import com.coparently.app.resources.chat_more_actions
import com.coparently.app.resources.chat_report_body
import com.coparently.app.resources.chat_report_message
import com.coparently.app.resources.chat_report_no_email_app
import com.coparently.app.resources.chat_report_subject
import com.coparently.app.resources.nav_settings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The mailbox a chat message is reported to (the build's support address, `BuildConfig.SUPPORT_EMAIL`
 * on Android, read through `AppBuildValues`), or null while the build names none. Null hides
 * "Report message" everywhere: a report with nowhere to go is the affordance design item 8 forbids.
 */
@Composable
private fun chatReportAddress(): String? =
    LocalAppBuildValues.current.supportEmail?.trim()?.takeIf { it.isNotEmpty() }

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
 * Provides nothing while [chatReportAddress] is null, nor where the phone cannot hand a mail draft
 * to another app (`OpenUrl.available`, the iPhone until phase 3).
 */
@Composable
fun ChatReportHost(content: @Composable () -> Unit) {
    val address = chatReportAddress()
    val openUrl = LocalOpenUrl.current
    if (address == null || !openUrl.available) {
        content()
        return
    }
    // The composition's resolver, so the subject and body follow the per-app language.
    val textResolver = rememberUiTextResolver()
    val scope = rememberCoroutineScope()
    val appMessages = LocalAppMessages.current
    val report = remember(textResolver, scope, openUrl, appMessages, address) {
        val send: (Message) -> Unit = { message ->
            scope.launch {
                val subject = UiText.Res(Res.string.chat_report_subject).asString(textResolver)
                val body = UiText.Res(
                    Res.string.chat_report_body,
                    listOf(message.id, message.conversationId, MessageReport.sentAtUtc(message.sentAtMillis))
                ).asString(textResolver)
                // The address, subject and body ride in the URI (RFC 6068) and again as extras,
                // because mail apps differ in which of the two they read (`OpenUrl.composeEmail`).
                if (!openUrl.composeEmail(address, subject, body)) {
                    val noApp = UiText.Res(Res.string.chat_report_no_email_app, listOf(address))
                    appMessages?.show(noApp.asString(textResolver))
                }
            }
        }
        send
    }
    CompositionLocalProvider(LocalChatMessageReport provides report, content = content)
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
    val label = stringResource(Res.string.chat_report_message)
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
 * The thread header's overflow: Settings, when the thread is the Chat tab (UX audit M12), and
 * "Block and stop sharing", which opens the existing unpair confirmation. Blocking *is* unpairing —
 * the rules refuse a message on a thread whose pairing has ended — so there is no second mechanism.
 * Red, last, and it confirms before anything happens, as every destructive action does (design
 * item 8).
 *
 * @param onOpenSettings Opens Settings, or null when the tab's own gear is elsewhere
 * @param onBlock Opens the unpair confirmation, or null when there is nobody to block
 */
@Composable
internal fun ChatOverflowMenu(onOpenSettings: (() -> Unit)?, onBlock: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.chat_more_actions)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            onOpenSettings?.let { openSettings ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.nav_settings)) },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = {
                        open = false
                        openSettings()
                    }
                )
            }
            onBlock?.let { block ->
                BlockMenuItem(
                    onClick = {
                        open = false
                        block()
                    }
                )
            }
        }
    }
}

/**
 * The overflow's entries as the iPhone's bar draws them in a menu: exactly [ChatOverflowMenu]'s, in
 * its order and each left out where it leaves it out — Settings (`gearshape`) when the thread is the
 * Chat tab, then "Block and stop sharing" (`nosign`), destructive as Android draws it in red. Android
 * keeps [ChatOverflowMenu], whose entries carry icons a bar's menu does not draw.
 *
 * @param onOpenSettings Opens Settings, or null when the tab's own gear is elsewhere
 * @param onBlock Opens the unpair confirmation, or null when there is nobody to block
 */
@Composable
internal fun chatOverflowEntries(onOpenSettings: (() -> Unit)?, onBlock: (() -> Unit)?): List<BarMenuItem> {
    val settingsLabel = stringResource(Res.string.nav_settings)
    val blockLabel = stringResource(Res.string.chat_block)
    return listOfNotNull(
        onOpenSettings?.let { BarMenuItem(label = settingsLabel, sfSymbol = "gearshape", onClick = it) },
        onBlock?.let { BarMenuItem(label = blockLabel, sfSymbol = "nosign", onClick = it, destructive = true) }
    )
}

/** The overflow's red "Block and stop sharing" entry. */
@Composable
private fun BlockMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(Res.string.chat_block),
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
        onClick = onClick
    )
}
