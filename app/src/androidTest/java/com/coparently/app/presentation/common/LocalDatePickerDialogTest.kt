package com.coparently.app.presentation.common

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.R
import com.coparently.app.presentation.childinfo.components.DatePickerDialog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone

/**
 * Drives the real Material3 date picker, by tapping, with the default time zone east and west of
 * Greenwich — the UI half of the audited off-by-one (`docs/AUDIT-2026-09.md` §1 item 3).
 *
 * Two picker types exist in the app, and both are driven here:
 * - [LocalDatePickerDialog], which the event form (start date, recurrence end), change requests,
 *   custody setup, expenses and the export range all open;
 * - the child/pet [DatePickerDialog] (dates of birth, vaccinations; also profile and onboarding),
 *   which wraps it and must keep the time of day it was given.
 *
 * East of Greenwich the old code highlighted the previous day, so the tests check which day is
 * *selected* when the picker opens; west of it the old code saved the previous day, so they tap a
 * day and check what comes back.
 */
@RunWith(Parameterized::class)
class LocalDatePickerDialogTest(private val zone: String) {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var savedZone: TimeZone

    @Before
    fun setZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(savedZone)
    }

    @Test
    fun opensWithTheGivenDaySelected_andConfirmingReturnsIt() {
        var picked: LocalDate? = null
        var dismissed = false
        composeTestRule.setContent {
            LocalDatePickerDialog(
                initialDate = INITIAL,
                confirmLabel = OK,
                dismissLabel = CANCEL,
                onConfirm = { picked = it },
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.onAllNodes(isSelected() and dayCell(INITIAL.dayOfMonth)).assertCountEquals(1)
        composeTestRule.onAllNodes(isSelected() and dayCell(INITIAL.dayOfMonth - 1)).assertCountEquals(0)

        composeTestRule.onNodeWithText(OK).performClick()
        composeTestRule.waitForIdle()

        assertEquals(INITIAL, picked)
        assertTrue(dismissed)
    }

    @Test
    fun tappingADay_returnsThatDay() {
        var picked: LocalDate? = null
        composeTestRule.setContent {
            LocalDatePickerDialog(
                initialDate = INITIAL,
                confirmLabel = OK,
                dismissLabel = CANCEL,
                onConfirm = { picked = it },
                onDismiss = {}
            )
        }

        composeTestRule.onAllNodes(dayCell(DST_DAY.dayOfMonth)).onFirst().performClick()
        composeTestRule.onNodeWithText(OK).performClick()
        composeTestRule.waitForIdle()

        assertEquals(DST_DAY, picked)
    }

    @Test
    fun cancelling_returnsNothing() {
        var picked: LocalDate? = null
        var dismissed = false
        composeTestRule.setContent {
            LocalDatePickerDialog(
                initialDate = INITIAL,
                confirmLabel = OK,
                dismissLabel = CANCEL,
                onConfirm = { picked = it },
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.onAllNodes(dayCell(DST_DAY.dayOfMonth)).onFirst().performClick()
        composeTestRule.onNodeWithText(CANCEL).performClick()
        composeTestRule.waitForIdle()

        assertNull(picked)
        assertTrue(dismissed)
    }

    @Test
    fun childAndPetPicker_returnsTheTappedDay_atTheTimeItWasGiven() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var picked: LocalDateTime? = null
        composeTestRule.setContent {
            DatePickerDialog(
                onDateSelected = { picked = it },
                onDismiss = {},
                initialDate = INITIAL.atTime(BIRTH_HOUR, BIRTH_MINUTE)
            )
        }

        composeTestRule.onAllNodes(isSelected() and dayCell(INITIAL.dayOfMonth)).assertCountEquals(1)
        composeTestRule.onAllNodes(dayCell(PICKED_BIRTHDAY.dayOfMonth)).onFirst().performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.childinfo_ok)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(PICKED_BIRTHDAY.atTime(BIRTH_HOUR, BIRTH_MINUTE), picked)
    }

    /**
     * A clickable day of the displayed month whose text or description names [day] as a whole
     * number. Material3 describes a day as its full date ("Sunday, March 29, 2026"), so the match
     * is on a word, not the whole text; none of the days used here is a substring of the year.
     */
    private fun dayCell(day: Int): SemanticsMatcher {
        val word = Regex("\\b$day\\b")
        return SemanticsMatcher("a day cell for $day") { node ->
            val words = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            node.config.contains(SemanticsActions.OnClick) && words.any { word.containsMatchIn(it) }
        }
    }

    companion object {

        /** UTC+1/+2, UTC+14, UTC−8/−7, UTC−11. */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun zones(): List<String> = listOf(
            "Europe/Prague",
            "Pacific/Kiritimati",
            "America/Los_Angeles",
            "Pacific/Pago_Pago"
        )

        private const val OK = "OK"
        private const val CANCEL = "Cancel"
        private const val BIRTH_HOUR = 8
        private const val BIRTH_MINUTE = 30

        private val INITIAL: LocalDate = LocalDate.of(2026, 3, 10)

        /** The day Central Europe moves its clocks forward. */
        private val DST_DAY: LocalDate = LocalDate.of(2026, 3, 29)
        private val PICKED_BIRTHDAY: LocalDate = LocalDate.of(2026, 3, 15)
    }
}
