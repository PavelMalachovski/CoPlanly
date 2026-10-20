package com.coparently.app.qa

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.presentation.navigation.BottomNavDestination
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The virtual tester's proof of life: the four tabs open, each step photographed, and the money
 * pattern the visual checks rely on reads Czech, Slovak and German amounts.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SmokeScenarioTest : QaOnScreenTest() {

    @Test
    fun fourTabsOpen() {
        BottomNavDestination.entries.forEach { tab ->
            val label = string(tab.labelRes)
            report.step(
                id = tab.name.lowercase(),
                title = "Open the $label tab",
                expected = "The $label tab is selected and its screen is shown"
            ) {
                openTab(tab)
                waitFor("$label selected", hasAnyAncestor(tabBar) and hasText(label) and isSelected())
            }
        }
        report.step(
            id = "money_pattern",
            title = "Recognise amounts the way the app prints them",
            expected = "1 290 Kč, 12,50 € and 1 290,00 CZK are amounts; \"Lena 2026\" is not",
            independent = true
        ) {
            val amounts = listOf("1 290 Kč", "12,50 €", "1 290,00 CZK")
            val missed = amounts.filterNot { moneyText.containsMatchIn(it) }
            check(missed.isEmpty()) { "not recognised as money: $missed" }
            check(!moneyText.containsMatchIn("Lena 2026")) { "\"Lena 2026\" read as money" }
        }
    }
}
