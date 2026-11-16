package com.coparently.app.presentation.common

/**
 * How tall an in-screen sheet opens on the iPhone (MON-24 phase 3, part C, Task 6).
 *
 * [Medium] opens at half the screen and can be pulled up to the full height, and what lies above
 * it stays in view (the event preview, the day swap's review, over the grid they are about), and
 * can be touched where the site asks for it. [Large] opens at the full height, the system's page
 * sheet.
 */
enum class SheetHeight {
    /** Half the screen, pulled up to the full height; the screen above it stays in view. */
    Medium,

    /** The full height. */
    Large
}
