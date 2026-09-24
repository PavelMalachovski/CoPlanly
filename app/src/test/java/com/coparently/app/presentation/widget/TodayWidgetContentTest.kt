package com.coparently.app.presentation.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTextEqualTo
import com.coparently.app.presentation.theme.ParentColorChoice
import org.junit.Test

/**
 * The widget's layout, composed on the JVM: every line [TodayWidgetText] words is drawn, and
 * nothing it left out is.
 */
class TodayWidgetContentTest {

    private val tall = TodayWidgetLines(
        title = "Today · Wed, Sep 2",
        custody = WidgetLine("Today with Alex", ParentColorChoice.PINK),
        windows = listOf(WidgetLine("15:00–19:00 · contact with Sam", ParentColorChoice.BLUE)),
        handover = WidgetLine("Handover to Sam in 5 days", ParentColorChoice.BLUE),
        events = listOf(
            WidgetEventLine("09:00–10:00", "Dentist", ParentColorChoice.PINK),
            WidgetEventLine("17:30", "Football", ParentColorChoice.BLUE)
        ),
        footer = "+1 more"
    )

    @Test
    fun `the tall widget draws every line it was given`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(TODAY_WIDGET_TALL)
        provideComposable { TodayWidgetContent(tall) }

        listOf(
            "Today · Wed, Sep 2",
            "Today with Alex",
            "15:00–19:00 · contact with Sam",
            "Handover to Sam in 5 days",
            "09:00–10:00",
            "Dentist",
            "17:30",
            "Football",
            "+1 more"
        ).forEach { onNode(hasTextEqualTo(it)).assertExists() }
    }

    @Test
    fun `a signed-out widget says so and names nobody`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            TodayWidgetContent(
                TodayWidgetLines(title = "Today · Wed, Sep 2", footer = "Sign in to CoPlanly to see your day.")
            )
        }

        onNode(hasTextEqualTo("Sign in to CoPlanly to see your day.")).assertExists()
        onNode(hasTextEqualTo("Today with Alex")).assertDoesNotExist()
        onNode(hasTextEqualTo("Dentist")).assertDoesNotExist()
    }
}
