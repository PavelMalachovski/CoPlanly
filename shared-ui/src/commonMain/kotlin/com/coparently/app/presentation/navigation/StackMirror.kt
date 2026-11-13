package com.coparently.app.presentation.navigation

/**
 * One change a native stack makes to show a shared back stack (MON-24 phase 3): what the iPhone's
 * stack host applies to a `UINavigationController` so that it shows what the shared
 * `NavHostController` holds.
 */
sealed interface StackChange {

    /** Pushes the screen of the back-stack entry [entryId] on top. */
    data class Push(val entryId: String) : StackChange

    /** Pops the top [count] screens. */
    data class Pop(val count: Int) : StackChange

    /** Replaces the whole stack with the screens of [entryIds]: its first screen changed. */
    data class Replace(val entryIds: List<String>) : StackChange
}

/**
 * The rules that keep a native stack and the shared back stack the same (MON-24 phase 3), pure
 * Kotlin over the two stacks' back-stack entry ids, bottom first, so that they are tested without
 * UIKit.
 *
 * The shared controller is the source of truth: [diff] says how the native stack follows it. The
 * one way the native stack moves first is the user's own back — the back button, the edge swipe,
 * a second tap on the selected tab — and [poppedByUser] says how many entries the shared
 * controller then pops to stand where the native stack stands.
 */
object StackMirror {

    /**
     * The changes that turn the stack [shown] into [wanted]: nothing when they are the same, the
     * pops down to the longest common bottom and then the pushes above it, or one [StackChange.Replace]
     * when the first screen differs (a stack never pops its first screen).
     */
    fun diff(shown: List<String>, wanted: List<String>): List<StackChange> {
        if (shown == wanted) return emptyList()
        if (shown.isEmpty() || wanted.isEmpty() || shown.first() != wanted.first()) {
            return listOf(StackChange.Replace(wanted))
        }
        val common = shown.zip(wanted).takeWhile { (a, b) -> a == b }.size
        return buildList {
            val pops = shown.size - common
            if (pops > 0) add(StackChange.Pop(pops))
            wanted.drop(common).forEach { add(StackChange.Push(it)) }
        }
    }

    /**
     * How many entries the shared controller pops after the native stack moved by itself.
     *
     * [asked] is what the host last told the native stack to show, [shown] what it shows once its
     * transition ended, and [wanted] what the shared controller holds now. The user popped when
     * [shown] is a shorter bottom of [asked] that the shared stack also starts with; the shared
     * controller then pops down to it. Anything else is not the user's pop and pops nothing: the
     * same stack (a back swipe that was cancelled), a stack the host asked for and the shared
     * controller has since moved past, or a stack that is no bottom of either.
     */
    fun poppedByUser(asked: List<String>, shown: List<String>, wanted: List<String>): Int {
        val userPopped = shown.isNotEmpty() &&
            shown.size < asked.size &&
            asked.take(shown.size) == shown
        if (!userPopped || wanted.size <= shown.size || wanted.take(shown.size) != shown) return 0
        return wanted.size - shown.size
    }
}
