package com.coparently.app.ios

import com.coparently.app.data.repository.PairingWording
import com.coparently.app.data.school.SchoolImportWordings
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.domain.school.SchoolImportWording
import com.coparently.app.resources.Res
import com.coparently.app.resources.pairing_default_partner_name
import com.coparently.app.resources.school_import_day_off
import com.coparently.app.resources.school_import_day_off_named
import com.coparently.app.resources.school_import_director_day
import com.coparently.app.resources.school_import_hours_title
import org.jetbrains.compose.resources.getString

/*
 * The words the data layer writes into stored data, on the iPhone (MON-24 phase 3, Task 6): the
 * twins of the app's `AndroidPairingWording` and `SchoolImportWordingSource`, reading the same keys
 * through Compose Multiplatform's `getString`.
 *
 * **In the language `readerLocale()` resolves.** Outside composition Compose Multiplatform reads its
 * strings in the language of the phone's first preferred language (`NSLocale.preferredLanguages`,
 * which also carries iOS's per-app choice first), from that language's `values-<language>` folder
 * when the app ships one and from `values/`, English, otherwise. That is the owner's rule
 * (`AppLanguageRule`: the phone's first language when shipped, else English), which `readerLocale()`
 * applies to the same list, and the string folders carry no region, so the two always agree
 * (`IosWordingsTest` holds them to it on the simulator). A text written once is data: the co-parent
 * reads it as written, as on Android.
 */

/**
 * The title of the thread with a co-parent whose profile carries no name yet, as
 * `AndroidPairingWording` words it: `pairing_default_partner_name`.
 */
class IosPairingWording : PairingWording {

    override suspend fun defaultPartnerName(): String = getString(Res.string.pairing_default_partner_name)
}

/**
 * The titles a school import writes (MON-8), as `SchoolImportWordingSource` words them: named for the
 * child as the family named them, or [SchoolImportWordings.forChild]'s fallback when the record is
 * gone.
 *
 * @property childInfo Where the child's name is read.
 */
class IosSchoolImportWordings(private val childInfo: ChildInfoRepository) : SchoolImportWordings {

    override suspend fun forChild(childId: String, fallbackName: String): SchoolImportWording {
        val name = childInfo.getChildInfoById(childId)?.childName?.takeIf { it.isNotBlank() } ?: fallbackName
        // `dayOff` is not suspending, so every text is read here, once per import.
        return ResolvedWording(
            schoolHours = getString(Res.string.school_import_hours_title, name),
            namedDayOff = getString(Res.string.school_import_day_off_named),
            directorDay = getString(Res.string.school_import_director_day, name),
            noSchool = getString(Res.string.school_import_day_off, name),
            child = name,
        )
    }

    /**
     * The import's texts, read. [namedDayOff] is the template itself (`%1$s: %2$s`), filled at
     * [dayOff] with the child and the school's own name for the day, as Compose Multiplatform's
     * `getString(resource, args)` fills one.
     */
    private class ResolvedWording(
        override val schoolHours: String,
        private val namedDayOff: String,
        private val directorDay: String,
        private val noSchool: String,
        private val child: String,
    ) : SchoolImportWording {
        override fun dayOff(description: String, type: SchoolDayType): String = when {
            description.isNotBlank() -> fill(namedDayOff, listOf(child, description))
            type == SchoolDayType.DIRECTOR_DAY -> directorDay
            else -> noSchool
        }
    }

    private companion object {
        /** The placeholders Compose Multiplatform's formatter replaces: `%<n>$s` and `%<n>$d`. */
        val PLACEHOLDER = Regex("""%(\d+)\$[ds]""")

        /** [template] with each placeholder replaced by its argument, in one pass. */
        fun fill(template: String, args: List<String>): String =
            PLACEHOLDER.replace(template) { args[it.groupValues[1].toInt() - 1] }
    }
}
