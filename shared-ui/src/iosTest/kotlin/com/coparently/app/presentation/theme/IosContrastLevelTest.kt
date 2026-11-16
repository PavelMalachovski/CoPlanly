package com.coparently.app.presentation.theme

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIAccessibilityDarkerSystemColorsStatusDidChangeNotification
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Increase Contrast on the iPhone (MON-24 phase 3, part D, Task 2): it is the medium scheme, and the
 * level is read again whenever the system says the switch moved. The simulator's switch cannot be set
 * from a test, so the second test pins the re-read, not the value.
 */
class IosContrastLevelTest {

    @Test
    fun mapsDarkerColorsToMedium() {
        assertEquals(ContrastLevel.MEDIUM, contrastLevelFor(true))
        assertEquals(ContrastLevel.STANDARD, contrastLevelFor(false))
    }

    @Test
    fun notificationUpdatesLevel() = runTest {
        val levels = async { iosContrastLevelChanges.take(2).toList() }
        runCurrent()

        NSNotificationCenter.defaultCenter.postNotificationName(
            UIAccessibilityDarkerSystemColorsStatusDidChangeNotification,
            `object` = null
        )

        val current = currentIosContrastLevel()
        assertEquals(listOf(current, current), levels.await())
    }
}
