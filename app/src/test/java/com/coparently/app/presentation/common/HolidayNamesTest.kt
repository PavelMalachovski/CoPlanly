package com.coparently.app.presentation.common

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.coparently.app.domain.holidays.CzechHolidays
import com.coparently.app.domain.holidays.CzechSchoolBreak
import com.coparently.app.domain.holidays.GermanHolidays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * L-8: a Czech school break is named in the reader's language from the app's own resources.
 *
 * Robolectric, because the point is the resources: every kind has one, the English and Czech ones
 * say exactly what the table says (so a Czech or English reader sees no change), and the German,
 * Russian and Ukrainian ones are real words rather than the English fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HolidayNamesTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun inLanguage(language: String): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }

    @Test
    fun `every Czech break maps to its own resource`() {
        val resources = CzechSchoolBreak.entries.map { it.nameRes() }
        assertEquals(CzechSchoolBreak.entries.size, resources.toSet().size)
    }

    @Test
    fun `English and Czech resources repeat the table's names`() {
        val english = inLanguage("en")
        val czech = inLanguage("cs")
        CzechSchoolBreak.entries.forEach {
            assertEquals(it.nameEn, english.getString(it.nameRes()))
            assertEquals(it.nameCs, czech.getString(it.nameRes()))
        }
    }

    @Test
    fun `German, Russian and Ukrainian readers get their own words`() {
        assertEquals("Weihnachtsferien", inLanguage("de").getString(CzechSchoolBreak.CHRISTMAS.nameRes()))
        assertEquals("Рождественские каникулы", inLanguage("ru").getString(CzechSchoolBreak.CHRISTMAS.nameRes()))
        assertEquals("Різдвяні канікули", inLanguage("uk").getString(CzechSchoolBreak.CHRISTMAS.nameRes()))
        listOf("de", "ru", "uk").forEach { language ->
            val localized = inLanguage(language)
            CzechSchoolBreak.entries.forEach {
                val name = localized.getString(it.nameRes())
                assertNotEquals("$language falls back to English for $it", it.nameEn, name)
            }
        }
    }

    @Test
    fun `every vacation the Czech table produces is a known kind`() {
        (2020..2035).forEach { year ->
            CzechHolidays.schoolVacations(year).forEach { (_, names) ->
                val kind = CzechSchoolBreak.of(CzechHolidays.localLanguage, names.second)
                assertNotNull("$year: ${names.first} has no kind", kind)
                assertEquals(names.first, kind?.nameEn)
            }
        }
    }

    @Test
    fun `another country's vacation is not a Czech break`() {
        assertNull(CzechSchoolBreak.of("de", "Vánoční prázdniny"))
        val bavaria = GermanHolidays.forRegion("BY")
        (2026..2027).flatMap { bavaria.schoolVacations(it) }.forEach { (_, names) ->
            assertNull(CzechSchoolBreak.of(bavaria.localLanguage, names.second))
        }
    }
}
