package com.coparently.app.presentation.common

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A shell that presents a form as a sheet, and so needs to know whether a swipe may close it
 * (MON-24 phase 3; spec §5.4): the iPhone's form sheet.
 *
 * [rememberDiscardGuard] reports into it while it is provided: whether the form holds edits a
 * dismissal would lose, and how to ask the form's own "Discard changes?" question. The sheet then
 * refuses a swipe while the form is dirty and asks the question instead, by the same rule as the
 * system Back on Android (design item 16).
 */
fun interface SheetGuard {

    /**
     * The form in the sheet holds edits ([dirty]) or not; [ask] shows its discard question, whose
     * "Keep editing" leaves the form open and whose "Discard" leaves it.
     */
    fun report(dirty: Boolean, ask: () -> Unit)
}

/**
 * The sheet a form is presented in, or null — Android's answer, and every screen's that is not in
 * a sheet: the form asks only on the system Back and its up arrow, as before. Like [LocalBarHost],
 * a deliberate exception to the rule that a platform local throws outside its provider.
 */
val LocalSheetGuard: ProvidableCompositionLocal<SheetGuard?> = staticCompositionLocalOf { null }
