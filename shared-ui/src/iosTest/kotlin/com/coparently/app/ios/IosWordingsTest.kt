package com.coparently.app.ios

import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.utils.readerLocale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import platform.Foundation.languageCode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The iPhone's wordings (MON-24 phase 3, Task 6): the keys the Android classes read, in the
 * language `readerLocale()` resolves on this simulator. Each expected text is the shipped
 * translation of that language, so the test holds Compose Multiplatform's choice of `values-*`
 * folder to the owner's rule whatever language the simulator runs in.
 */
class IosWordingsTest {

    private val language = readerLocale().languageCode

    @Test
    fun `a nameless co-parent's thread is titled in the reader's language`() = runBlocking {
        assertEquals(PARTNER.getValue(language), IosPairingWording().defaultPartnerName())
    }

    @Test
    fun `a school import names the child as the family named them`() = runBlocking {
        val wording = IosSchoolImportWordings(OneChild("child-1", "Anna")).forChild("child-1", "Anička")

        assertEquals(HOURS.getValue(language).replace("%1\$s", "Anna"), wording.schoolHours)
        assertEquals("Anna: Ředitelské volno", wording.dayOff("Ředitelské volno", SchoolDayType.DIRECTOR_DAY))
    }

    @Test
    fun `a school import falls back to the school's name for a child record that is gone`() = runBlocking {
        val wording = IosSchoolImportWordings(OneChild("child-1", "Anna")).forChild("child-2", "Anička")

        assertEquals(HOURS.getValue(language).replace("%1\$s", "Anička"), wording.schoolHours)
    }

    /** A [ChildInfoRepository] that holds one child. */
    private class OneChild(private val id: String, private val name: String) : ChildInfoRepository {
        override fun getAllChildInfo(): Flow<List<ChildInfo>> = emptyFlow()
        override suspend fun getChildInfoById(id: String): ChildInfo? =
            if (id == this.id) ChildInfo(id = id, childName = name, dateOfBirth = null, createdAt = STAMP, updatedAt = STAMP) else null
        override fun observeChildInfoById(id: String): Flow<ChildInfo?> = flowOf(null)
        override suspend fun upsertChildInfo(childInfo: ChildInfo) = Unit
        override suspend fun deleteChildInfo(childInfo: ChildInfo) = Unit
        override suspend fun pullOnce() = Unit
    }

    private companion object {
        /** When the test's child record was written; nothing reads it. */
        val STAMP = LocalDateTime(2026, 11, 10, 9, 0)

        /** `pairing_default_partner_name` in the five shipped languages. */
        val PARTNER = mapOf(
            "en" to "Co-parent",
            "cs" to "Spolurodič",
            "de" to "Co-Elternteil",
            "ru" to "Второй родитель",
            "uk" to "Другий із батьків",
        )

        /** `school_import_hours_title` in the five shipped languages. */
        val HOURS = mapOf(
            "en" to "%1\$s at school",
            "cs" to "%1\$s ve škole",
            "de" to "%1\$s in der Schule",
            "ru" to "%1\$s в школе",
            "uk" to "%1\$s у школі",
        )
    }
}
