package com.coparently.app.presentation.settings

import com.coparently.app.R
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The data-sources screen's list: the one thing it must never lose is the OpenHolidays notice and
 * the link to the licence that notice names — that is the attribution ODbL 1.0 asks for.
 */
class DataSourcesTest {

    @Test
    fun `the OpenHolidays data is attributed, with its licence linked`() {
        val dataset = DataSources.notices.single { it.kind == DataSourceNotice.Kind.DATASET }
        val licence = DataSources.notices.single { it.kind == DataSourceNotice.Kind.LICENCE }

        assertEquals(R.string.data_sources_openholidays_title, dataset.titleRes)
        assertEquals(R.string.data_sources_openholidays_description, dataset.descriptionRes)
        assertEquals("https://opendatacommons.org/licenses/odbl/1-0/", licence.url)
    }

    @Test
    fun `the licence row prints its address, since the address is the notice`() {
        val licence = DataSources.notices.single { it.kind == DataSourceNotice.Kind.LICENCE }

        assertEquals(null, licence.descriptionRes)
    }

    @Test
    fun `every link is https`() {
        assertTrue(DataSources.notices.all { it.url.startsWith("https://") })
    }
}
