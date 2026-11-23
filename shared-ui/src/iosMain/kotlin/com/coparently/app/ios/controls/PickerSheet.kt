package com.coparently.app.ios.controls

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UILabel
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UINavigationController
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UIKit.accessibilityLabel
import platform.UIKit.navigationItem
import platform.UIKit.presentationController
import platform.UIKit.secondaryLabelColor
import platform.UIKit.sheetPresentationController
import platform.UIKit.systemBackgroundColor
import platform.UIKit.transitionCoordinator
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * The view controller a sheet or alert is presented from: the top of what [from] (or, without one,
 * the key window's root) presents now, stopping under a controller that is being dismissed. That
 * presenter may still be mid-transition, when UIKit drops a presentation without a word, so present
 * through [presentWhenSettled] rather than calling `presentViewController` on this directly.
 */
internal fun topViewController(from: UIViewController? = null): UIViewController? {
    var top = from ?: keyWindowRoot() ?: return null
    var next = top.presentedViewController
    while (next != null && !next.beingDismissed) {
        top = next
        next = top.presentedViewController
    }
    return top
}

/**
 * Presents [controller] from [topViewController] of [from] once nothing there is moving: while a
 * presentation or dismissal is in flight (a previous sheet still sliding away, a form sheet sliding
 * in), UIKit ignores `presentViewController` without calling anything back. So it waits for the
 * transition's end (its coordinator's completion, or a short delay when a dismissal has no
 * coordinator), and tries again, at most [MAX_PRESENT_ATTEMPTS] times.
 *
 * [onDropped] is called once, when it cannot present at all: no controller to present from, the
 * attempts used up, or UIKit dropping the presentation after all (nothing presents [controller]
 * once asked), so a caller's "open" state is released and a later open works. Nothing is presented
 * once [isWanted] answers false (the dialog left composition while it waited).
 */
internal fun presentWhenSettled(
    controller: UIViewController,
    from: UIViewController?,
    isWanted: () -> Boolean,
    onDropped: () -> Unit
) {
    presentAttempt(controller, from, isWanted, onDropped, attemptsLeft = MAX_PRESENT_ATTEMPTS)
}

private fun presentAttempt(
    controller: UIViewController,
    from: UIViewController?,
    isWanted: () -> Boolean,
    onDropped: () -> Unit,
    attemptsLeft: Int
) {
    if (!isWanted()) return
    val host = topViewController(from)
    val busy = host != null && (host.presentedViewController != null || host.beingPresented || host.beingDismissed)
    when {
        host == null -> onDropped()
        busy && attemptsLeft <= 0 -> onDropped()
        busy -> retryAfterTransition(host) {
            presentAttempt(controller, from, isWanted, onDropped, attemptsLeft - 1)
        }
        else -> {
            host.presentViewController(controller, animated = true, completion = null)
            if (controller.presentingViewController == null) onDropped()
        }
    }
}

/**
 * Runs [retry] once the transition on [host] (or on what it presents) has ended, or after
 * [RETRY_DELAY_NS] when there is no coordinator to wait on.
 */
private fun retryAfterTransition(host: UIViewController, retry: () -> Unit) {
    val coordinator = host.presentedViewController?.transitionCoordinator ?: host.transitionCoordinator
    val waits = coordinator?.animateAlongsideTransition(null, completion = { _ -> retry() }) == true
    if (!waits) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, RETRY_DELAY_NS), dispatch_get_main_queue()) { retry() }
    }
}

/** How many times [presentWhenSettled] waits for a transition before it gives up. */
private const val MAX_PRESENT_ATTEMPTS = 10

/** The wait between two attempts when a transition has no coordinator to wait on: 0.15 s. */
private const val RETRY_DELAY_NS = 150_000_000L

/** The key window's root view controller, from the app's connected scenes. */
private fun keyWindowRoot(): UIViewController? =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstNotNullOfOrNull { it.keyWindow?.rootViewController }

/**
 * A `UIDatePicker` presented as a sheet with a grabber, Cancel on the left of its bar and Done on the
 * right (MON-24 phase 3, part C, Task 3): what the shared date and time dialogs are on the iPhone.
 * The time wheels open at the medium height; the inline calendar opens [fullHeight], because a
 * medium sheet is shorter than the calendar and UIKit then lays `UICalendarView` out again and again
 * on the main thread, allocating as it goes, until the app stops answering (iOS 27 every time,
 * iOS 26 now and then: the jump to a date hung at 3.6 GB, November 2026).
 *
 * It ends once, whichever way: Done reports the picker's value through [onDone] and then calls
 * [onClosed], as Material's dialogs call their confirm and then their dismiss; Cancel and a swipe
 * down call only [onClosed]. [close] takes it away without calling either, for a dialog that left
 * composition while shown. The object holds the bar buttons' target and the presentation delegate,
 * which UIKit does not retain, so it must be kept for as long as the sheet is up.
 */
// `CGRectMake` and `NSSelectorFromString` are Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal class PickerSheet(
    val picker: UIDatePicker,
    confirmLabel: String,
    dismissLabel: String,
    private val onDone: (NSDate) -> Unit,
    private val onClosed: () -> Unit,
    fullHeight: Boolean = false
) {
    /** Whether the sheet has ended, by any of its four ways. */
    var finished: Boolean = false
        private set

    private val actions = Actions()
    private val content = UIViewController(nibName = null, bundle = null)

    /** The navigation controller presented as the sheet, holding the picker's screen. */
    val navigation: UINavigationController

    init {
        content.view.backgroundColor = UIColor.systemBackgroundColor
        content.view.addSubview(picker)
        picker.translatesAutoresizingMaskIntoConstraints = false
        val guide = content.view.safeAreaLayoutGuide
        NSLayoutConstraint.activateConstraints(
            listOf(
                picker.topAnchor.constraintEqualToAnchor(guide.topAnchor),
                picker.leadingAnchor.constraintEqualToAnchor(guide.leadingAnchor, constant = MARGIN_PT),
                picker.trailingAnchor.constraintEqualToAnchor(guide.trailingAnchor, constant = -MARGIN_PT)
            )
        )
        content.navigationItem.leftBarButtonItem = UIBarButtonItem(
            title = dismissLabel,
            style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
            target = actions,
            action = NSSelectorFromString("cancel:")
        )
        content.navigationItem.rightBarButtonItem = UIBarButtonItem(
            title = confirmLabel,
            style = UIBarButtonItemStyle.UIBarButtonItemStyleDone,
            target = actions,
            action = NSSelectorFromString("done:")
        )
        navigation = UINavigationController(rootViewController = content).also { sheet ->
            sheet.modalPresentationStyle = UIModalPresentationPageSheet
            sheet.sheetPresentationController?.let { controller ->
                controller.detents = listOf(
                    if (fullHeight) {
                        UISheetPresentationControllerDetent.largeDetent()
                    } else {
                        UISheetPresentationControllerDetent.mediumDetent()
                    }
                )
                controller.prefersGrabberVisible = true
            }
            sheet.presentationController?.delegate = actions
        }
    }

    /**
     * Presents the sheet over the top of what [from] presents, once nothing there is moving
     * ([presentWhenSettled]). If it cannot be presented, it ends as a dismissal does, so the
     * dialog's state is released and a second open works.
     */
    fun present(from: UIViewController?) {
        presentWhenSettled(navigation, from, isWanted = { !finished }, onDropped = ::dismissed)
    }

    /** Done: the picker's value, then the end. */
    fun confirm() {
        if (finished) return
        finished = true
        takeAway()
        onDone(picker.date)
        onClosed()
    }

    /** Cancel: the end alone. */
    fun cancel() {
        if (finished) return
        finished = true
        takeAway()
        onClosed()
    }

    /** The sheet went by itself (a swipe down): the end alone, with nothing left to take away. */
    fun dismissed() {
        if (finished) return
        finished = true
        onClosed()
    }

    /** Takes the sheet away without calling back: its dialog left composition while it was shown. */
    fun close() {
        if (finished) return
        finished = true
        takeAway()
    }

    private fun takeAway() {
        if (navigation.presentingViewController != null && !navigation.beingDismissed) {
            navigation.dismissViewControllerAnimated(true, completion = null)
        }
    }

    /** The bar buttons' target and the sheet's presentation delegate. */
    private inner class Actions : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {

        /** Cancel's target-action; the sender is the selector's own argument, unused. */
        @ObjCAction
        fun cancel(@Suppress("UnusedParameter") sender: UIBarButtonItem) {
            this@PickerSheet.cancel()
        }

        /** Done's target-action; the sender is the selector's own argument, unused. */
        @ObjCAction
        fun done(@Suppress("UnusedParameter") sender: UIBarButtonItem) {
            confirm()
        }

        override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
            dismissed()
        }
    }

    private companion object {
        /** The picker's inset from the sheet's edges, in points: UIKit's standard margin. */
        const val MARGIN_PT = 16.0
    }
}

/** A date sheet: an inline calendar in date mode, opening on [initial]. */
internal fun dateSheet(
    initial: LocalDate,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
): PickerSheet = PickerSheet(
    picker = makeDatePicker(UIDatePickerMode.UIDatePickerModeDate, UIDatePickerStyle.UIDatePickerStyleInline).apply {
        setDate(nsDateFor(initial), animated = false)
    },
    confirmLabel = confirmLabel,
    dismissLabel = dismissLabel,
    onDone = { onConfirm(localDateOf(it)) },
    onClosed = onDismiss,
    // The inline calendar is taller than a medium sheet (see PickerSheet).
    fullHeight = true
)

/** A time sheet: wheels in time mode on the phone's own clock, opening on [initial]. */
internal fun timeSheet(
    initial: LocalTime,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
): PickerSheet = PickerSheet(
    picker = makeDatePicker(UIDatePickerMode.UIDatePickerModeTime, UIDatePickerStyle.UIDatePickerStyleWheels).apply {
        setDate(nsDateFor(initial), animated = false)
    },
    confirmLabel = confirmLabel,
    dismissLabel = dismissLabel,
    onDone = { onConfirm(localTimeOf(it)) },
    onClosed = onDismiss
)

/**
 * A `UIDatePicker` in [mode] and [style], on [pickerCalendar] and its zone, so what it shows is what
 * [localDateOf] and [localTimeOf] read back. The clock's 12 or 24 hours follow the phone's setting
 * through the picker's own locale.
 */
// `CGRectMake` is Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal fun makeDatePicker(mode: UIDatePickerMode, style: UIDatePickerStyle): UIDatePicker =
    UIDatePicker(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply { configure(mode, style) }

private fun UIDatePicker.configure(mode: UIDatePickerMode, style: UIDatePickerStyle) {
    val calendar = pickerCalendar()
    this.calendar = calendar
    timeZone = calendar.timeZone
    datePickerMode = mode
    preferredDatePickerStyle = style
}

/**
 * A compact `UIDatePicker` that is its own target, so the action and what it calls live exactly as
 * long as the view: UIKit does not retain a control's targets. UIKit sends
 * `UIControlEventValueChanged` only for the parent's own pick, never for a date set in code.
 */
// `CGRectMake` and `NSSelectorFromString` are Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal class ValueChangedDatePicker(
    mode: UIDatePickerMode,
    private val onValueChanged: (NSDate) -> Unit
) : UIDatePicker(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    init {
        configure(mode, UIDatePickerStyle.UIDatePickerStyleCompact)
        addTarget(this, action = NSSelectorFromString("valueChanged:"), forControlEvents = UIControlEventValueChanged)
    }

    /** The target-action of `UIControlEventValueChanged`. */
    @ObjCAction
    fun valueChanged(sender: UIDatePicker) {
        onValueChanged(sender.date)
    }
}

/**
 * The compact date pill: the picker, and over it a dash while no day is chosen. A compact picker
 * always shows a date, so with none chosen the picker is all but transparent (still above UIKit's
 * hit-test threshold, so a tap still opens the calendar) and the dash says nothing is set.
 *
 * Both are laid out at the pill's own bounds ([layoutSubviews]), as [SegmentedHost] lays out its
 * control: a picker made with a zero frame takes a default size of its own, and resizing it with the
 * pill (autoresizing) added the pill's size to that, so it reached past the pill and over the time
 * pill beside it, where no tap could reach it.
 */
// `CGRectMake` is Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal class CompactDatePill(onChange: (LocalDate) -> Unit) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    /** The compact picker the parent taps. */
    val picker = ValueChangedDatePicker(UIDatePickerMode.UIDatePickerModeDate) { onChange(localDateOf(it)) }

    /** The dash shown while no day is chosen. */
    val placeholder = UILabel(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
        text = NO_DATE
        textAlignment = NSTextAlignmentCenter
        textColor = UIColor.secondaryLabelColor
        userInteractionEnabled = false
        hidden = true
    }

    init {
        addSubview(picker)
        addSubview(placeholder)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        picker.setFrame(bounds)
        placeholder.setFrame(bounds)
    }
}

/**
 * Shows [value] on [pill] without reporting a change, or, for null, [today] under the dash; names
 * the picker [label] for VoiceOver.
 */
internal fun updateCompactDate(pill: CompactDatePill, value: LocalDate?, today: LocalDate, label: String) {
    val shown = value ?: today
    if (localDateOf(pill.picker.date) != shown) pill.picker.setDate(nsDateFor(shown), animated = false)
    pill.picker.alpha = if (value == null) UNCHOSEN_ALPHA else 1.0
    pill.placeholder.hidden = value != null
    pill.picker.accessibilityLabel = label
}

/** Shows [value] on [picker], a compact time picker, without reporting a change. */
internal fun updateCompactTime(picker: UIDatePicker, value: LocalTime) {
    if (localTimeOf(picker.date) != value) picker.setDate(nsDateFor(value), animated = false)
}

/**
 * The size of a compact picker in [mode] showing [date], in points, measured from UIKit's own; or
 * [fallback] if UIKit reports none.
 */
// `useContents` is Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal fun compactPickerSize(
    mode: UIDatePickerMode,
    date: NSDate,
    fallback: Pair<Double, Double>
): Pair<Double, Double> {
    val picker = makeDatePicker(mode, UIDatePickerStyle.UIDatePickerStyleCompact)
    picker.setDate(date, animated = false)
    return picker.intrinsicContentSize.useContents {
        if (width > 0.0 && height > 0.0) width to height else fallback
    }
}

/** What the date pill shows while no day is chosen: a dash, not a word, so no translation. */
private const val NO_DATE = "—"

/** The picker's opacity while no day is chosen: just above UIKit's hit-test threshold of 0.01. */
private const val UNCHOSEN_ALPHA = 0.02
