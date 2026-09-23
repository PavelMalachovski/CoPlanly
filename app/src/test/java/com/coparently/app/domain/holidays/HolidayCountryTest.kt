package com.coparently.app.domain.holidays

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The country a parent lives in, and the holiday calendar that follows (MON-13).
 *
 * MVP 1 asked for "holidays and vacations by country" and shipped one country with no field
 * anywhere to say otherwise, so a German or Ukrainian family got Czech public holidays. The two
 * properties that matter here are that an upgrade changes nothing, and that a country the app has
 * no table for draws **no** holidays rather than somebody else's.
 */
class HolidayCountryTest {

    @Test
    fun `an account that predates the field reads as Czechia`() {
        // Which is what the v32-33 migration stamps, and what those accounts were already being
        // shown. An upgrade must not move anybody's holidays.
        assertEquals(HolidayCountry.CZECHIA, HolidayCountry.Default)
        assertEquals(HolidayCountry.CZECHIA, HolidayCountry.fromCode("CZ"))
    }

    @Test
    fun `a stored code is read whatever case or padding it arrives in`() {
        assertEquals(HolidayCountry.GERMANY, HolidayCountry.fromCode("de"))
        assertEquals(HolidayCountry.UKRAINE, HolidayCountry.fromCode(" UA "))
    }

    @Test
    fun `an unknown code falls back rather than failing`() {
        // A newer build's country read by an older one, or a blank row. Every call site draws a
        // calendar and has to draw something.
        assertEquals(HolidayCountry.Default, HolidayCountry.fromCode("XX"))
        assertEquals(HolidayCountry.Default, HolidayCountry.fromCode(""))
        assertEquals(HolidayCountry.Default, HolidayCountry.fromCode(null))
    }

    @Test
    fun `every country has a distinct stored code`() {
        // The code is a stored value two devices compare; a duplicate would make two countries
        // indistinguishable on the wire.
        val codes = HolidayCountry.entries.map { it.code }

        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `each country states exactly what it draws`() {
        // Not an aspiration: `coverage` is what the picker renders its note from, so a change here
        // is a change in what the app tells the user. Only Czechia has school vacations; Ukraine
        // has no provider *because* its holidays are suspended, not because one is missing.
        val expected = mapOf(
            HolidayCountry.CZECHIA to HolidayCoverage.PUBLIC_AND_SCHOOL,
            HolidayCountry.SLOVAKIA to HolidayCoverage.PUBLIC_ONLY,
            HolidayCountry.GERMANY to HolidayCoverage.PUBLIC_ONLY,
            HolidayCountry.AUSTRIA to HolidayCoverage.PUBLIC_ONLY,
            HolidayCountry.UKRAINE to HolidayCoverage.SUSPENDED,
            HolidayCountry.RUSSIA to HolidayCoverage.PUBLIC_ONLY,
            HolidayCountry.OTHER to HolidayCoverage.NONE
        )

        assertEquals(HolidayCountry.entries.toSet(), expected.keys)
        expected.forEach { (country, coverage) ->
            assertEquals(coverage, country.coverage, country.code)
        }
    }

    @Test
    fun `a provider that says it has no school vacations returns none`() {
        // The picker's "public holidays only" sentence is derived from the flag, so the flag must
        // not disagree with the list it describes.
        HolidayCountry.entries.mapNotNull { it.provider }.forEach { provider ->
            (2024..2030).forEach { year ->
                assertEquals(
                    provider.hasSchoolVacations,
                    provider.schoolVacations(year).isNotEmpty(),
                    "${provider.localLanguage} $year"
                )
            }
        }
    }

    @Test
    fun `a country with no table draws no holidays at all`() {
        // The whole point. Drawing another country's was the defect; drawing none is honest, and
        // the picker says so on the row — with the reason, for Ukraine.
        assertNull(HolidayCountry.UKRAINE.provider)
        assertNull(HolidayCountry.OTHER.provider)
        assertFalse(HolidayCountry.UKRAINE.hasHolidays)
    }

    @Test
    fun `each provider names its holidays in its own country's language`() {
        // The grid shows `nameLocal` when the device language equals `localLanguage`; a German
        // provider tagged "cs" would show German names to Czech readers and English to Germans.
        assertEquals("sk", HolidayCountry.SLOVAKIA.provider!!.localLanguage)
        assertEquals("de", HolidayCountry.GERMANY.provider!!.localLanguage)
        assertEquals("de", HolidayCountry.AUSTRIA.provider!!.localLanguage)
        assertEquals("ru", HolidayCountry.RUSSIA.provider!!.localLanguage)
        assertNotNull(HolidayCountry.CZECHIA.provider)
    }

    @Test
    fun `the Czech provider still answers through the shared interface`() {
        val holidays = HolidayCountry.CZECHIA.provider!!
            .holidaysInRange(LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 26))

        assertEquals(3, holidays.size)
        assertEquals("cs", holidays.values.first().localLanguage)
    }

    @Test
    fun `only Germany offers regions`() {
        // Austria's public holidays are nationwide; its patron-saint days are bank holidays. A
        // region picker there would change nothing on the grid.
        assertEquals(16, HolidayCountry.GERMANY.regions.size)
        HolidayCountry.entries.filter { it != HolidayCountry.GERMANY }.forEach { country ->
            assertEquals(emptyList(), country.regions, country.code)
        }
    }

    @Test
    fun `a region that does not belong to the country is dropped`() {
        // A parent who moved from Germany to Austria keeps a stale `regionCode` until the next
        // save; it must not select anything, least of all another country's table.
        assertEquals("BY", HolidayCountry.GERMANY.regionOrNull(" by "))
        assertNull(HolidayCountry.AUSTRIA.regionOrNull("BY"))
        assertNull(HolidayCountry.GERMANY.regionOrNull("XX"))
        assertNull(HolidayCountry.GERMANY.regionOrNull(null))

        val location = HolidayLocation.of("AT", "BY")
        assertEquals(HolidayCountry.AUSTRIA, location.country)
        assertNull(location.regionCode)
        assertEquals(AustrianHolidays, location.provider)
    }

    @Test
    fun `a location with no region draws the nationwide calendar`() {
        assertEquals(GermanHolidays, HolidayLocation.of("DE", null).provider)
        assertEquals(HolidayLocation(HolidayCountry.CZECHIA), HolidayLocation.Default)
        assertNull(HolidayLocation.of("UA", null).provider)
    }

    @Test
    fun `a public holiday wins over a school vacation covering the same day`() {
        // 24-26 December are both, and a parent looking at the grid wants to be told it is
        // Christmas rather than that school is out. The precedence lives on the interface now,
        // so no future provider can get it subtly different.
        val christmas = HolidayCountry.CZECHIA.provider!!.holidayFor(LocalDate.of(2026, 12, 25))

        assertNotNull(christmas)
        assertEquals(false, christmas.isSchoolVacation)
    }
}
