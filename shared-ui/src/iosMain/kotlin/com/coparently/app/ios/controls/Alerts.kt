package com.coparently.app.ios.controls

import com.coparently.app.presentation.common.BarMenuItem
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyle
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIViewController

/**
 * One answer of an [AlertSheet]: its title, its style (`.default`, `.destructive` or `.cancel`),
 * whether it can be chosen now, and what choosing it does.
 */
internal data class AlertChoice(
    val title: String,
    val style: UIAlertActionStyle,
    val enabled: Boolean = true,
    val onChoose: () -> Unit
)

/**
 * A `UIAlertController`, as an alert or an action sheet, that ends once (MON-24 phase 3, part C,
 * Task 5): what the shared confirmations and the chat bubble's long press are on the iPhone.
 *
 * Choosing an answer calls that answer's [AlertChoice.onChoose] and nothing else; the controller
 * takes itself away, as UIKit's alerts do. If UIKit cannot present it at all, [onDropped] is called
 * once, so the caller's open state is released and a later open works. [close] takes it away without
 * calling anything, for a composable that left composition while it was up. After the first of the
 * four, the others do nothing.
 */
internal class AlertSheet(
    title: String?,
    message: String?,
    actionSheet: Boolean,
    private val choices: List<AlertChoice>,
    private val onDropped: () -> Unit
) {
    /** Whether it has ended, by any of its ways. */
    var finished: Boolean = false
        private set

    /** The controller presented. */
    val controller: UIAlertController = UIAlertController.alertControllerWithTitle(
        title = title,
        message = message,
        preferredStyle = if (actionSheet) {
            UIAlertControllerStyleActionSheet
        } else {
            UIAlertControllerStyleAlert
        }
    )

    init {
        choices.forEachIndexed { index, choice ->
            val action = UIAlertAction.actionWithTitle(choice.title, choice.style) { _ -> choose(index) }
            action.enabled = choice.enabled
            controller.addAction(action)
        }
    }

    /**
     * Presents it over the top of what [from] presents, once nothing there is moving
     * ([presentWhenSettled]). No popover anchor is set: the app runs on iPhones only
     * (`TARGETED_DEVICE_FAMILY` 1), where an action sheet rises from the bottom with its Cancel, and
     * an anchor would make a newer iOS show it as a popover without one.
     */
    fun present(from: UIViewController?) {
        presentWhenSettled(controller, from, isWanted = { !finished }, onDropped = ::dropped)
    }

    /** The answer at [index] was chosen: its own lambda, once. */
    fun choose(index: Int) {
        if (finished) return
        finished = true
        choices.getOrNull(index)?.onChoose?.invoke()
    }

    /** UIKit could not present it: the caller's dismissal, once. */
    fun dropped() {
        if (finished) return
        finished = true
        onDropped()
    }

    /** Takes it away without calling back: its composable left composition while it was up. */
    fun close() {
        if (finished) return
        finished = true
        if (controller.presentingViewController != null && !controller.beingDismissed) {
            controller.dismissViewControllerAnimated(true, completion = null)
        }
    }
}

/**
 * A question with two answers: an alert, or an action sheet when [asActionSheet], with the confirm
 * action first (`.destructive` when [destructive], else `.default`) and the dismiss as `.cancel`,
 * which UIKit places as a separate button on an action sheet and on the left of an alert.
 */
@Suppress("LongParameterList") // the question, its two answers, how they look and what they do
internal fun confirmAlert(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    destructive: Boolean,
    asActionSheet: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
): AlertSheet = AlertSheet(
    title = title,
    message = message.ifEmpty { null },
    actionSheet = asActionSheet,
    choices = listOf(
        AlertChoice(
            title = confirmText,
            style = if (destructive) {
                UIAlertActionStyleDestructive
            } else {
                UIAlertActionStyleDefault
            },
            onChoose = onConfirm
        ),
        AlertChoice(title = dismissText, style = UIAlertActionStyleCancel, onChoose = onDismiss)
    ),
    onDropped = onDismiss
)

/**
 * An action sheet of [items] with [title] (none for null) and the system's Cancel, titled
 * [cancelText]. An item calls [onItem] with its place, so the caller runs the latest items' entry;
 * Cancel, or a sheet UIKit could not present, calls [onDismiss].
 */
internal fun menuActionSheet(
    title: String?,
    items: List<BarMenuItem>,
    cancelText: String,
    onItem: (Int) -> Unit,
    onDismiss: () -> Unit
): AlertSheet = AlertSheet(
    title = title,
    message = null,
    actionSheet = true,
    choices = items.mapIndexed { index, item ->
        AlertChoice(
            title = item.label,
            style = if (item.destructive) {
                UIAlertActionStyleDestructive
            } else {
                UIAlertActionStyleDefault
            },
            enabled = item.enabled,
            onChoose = { onItem(index) }
        )
    } + AlertChoice(title = cancelText, style = UIAlertActionStyleCancel, onChoose = onDismiss),
    onDropped = onDismiss
)
