package com.coparently.app.presentation.settings

import com.coparently.app.resources.Res
import com.coparently.app.resources.data_sources_onest_description
import com.coparently.app.resources.data_sources_onest_title
import com.coparently.app.resources.data_sources_openholidays_description
import com.coparently.app.resources.data_sources_openholidays_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The data-sources screen's list: the one thing it must never lose is the OpenHolidays notice and
 * the link to the licence that notice names — that is the attribution ODbL 1.0 asks for.
 *
 * In `:shared-ui`'s `commonTest` since the screen moved (MON-24 phase 2, slice 11f), comparing the
 * `StringResource`s' keys where it compared `R.string` ids.
 */
class DataSourcesTest {

    @Test
    fun `the OpenHolidays data is attributed with its licence linked`() {
        val dataset = DataSources.notices.single { it.kind == DataSourceNotice.Kind.DATASET }
        val licence = DataSources.notices.single { it.kind == DataSourceNotice.Kind.LICENCE }

        assertEquals(Res.string.data_sources_openholidays_title.key, dataset.titleRes.key)
        assertEquals(Res.string.data_sources_openholidays_description.key, dataset.descriptionRes?.key)
        assertEquals("https://opendatacommons.org/licenses/odbl/1-0/", licence.url)
    }

    @Test
    fun `the licence row prints its address - since the address is the notice`() {
        val licence = DataSources.notices.single { it.kind == DataSourceNotice.Kind.LICENCE }

        assertEquals(null, licence.descriptionRes)
    }

    @Test
    fun `the printed address breaks only after a slash and keeps every visible character`() {
        val printed = DataSources.displayAddress(DataSources.ODBL_URL)
        val zeroWidth = setOf('\u200B', '\u2060')

        assertEquals("opendatacommons.org/licenses/odbl/1-0", printed.filterNot { it in zeroWidth })
        assertEquals("opendatacommons.org/\u200Blicenses/\u200Bodbl/\u200B1\u2060-\u20600", printed)
    }

    @Test
    fun `every link is https`() {
        assertTrue((DataSources.notices + DataSources.typefaceNotices).all { it.url.startsWith("https://") })
    }

    @Test
    fun `the bundled typeface is credited with the licence it is distributed under`() {
        val typeface = DataSources.typefaceNotices.single()

        assertEquals(Res.string.data_sources_onest_title.key, typeface.titleRes.key)
        assertEquals(Res.string.data_sources_onest_description.key, typeface.descriptionRes?.key)
        assertEquals("https://openfontlicense.org", typeface.url)
    }
}
