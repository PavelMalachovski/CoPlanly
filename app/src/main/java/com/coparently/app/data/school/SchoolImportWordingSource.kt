package com.coparently.app.data.school

import android.content.Context
import com.coparently.app.R
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.domain.school.SchoolImportWording
import com.coparently.app.utils.inAppLanguage

/**
 * The titles an import writes, in the importing parent's language (MON-8).
 *
 * A title is data once written, like one a parent typed: the co-parent reads it as written. It
 * names the child — "Anna at school" — because a family with two children at school would
 * otherwise read two identical rows, and a member is a name, never a colour (FAM-2).
 *
 * Resolved in the language the app speaks when the import runs ([inAppLanguage]: the per-app
 * choice, else the phone's first language when the app ships it, else English), because the import
 * runs in a worker with no activity, as a push does (CLAUDE.md item 15).
 */
class SchoolImportWordingSource(
    private val context: Context,
    private val childInfoRepository: ChildInfoRepository
) : SchoolImportWordings {

    override suspend fun forChild(childId: String, fallbackName: String): SchoolImportWording {
        val name = childInfoRepository.getChildInfoById(childId)?.childName?.takeIf { it.isNotBlank() }
            ?: fallbackName
        return ResourceWording(context.inAppLanguage(), name)
    }

    private class ResourceWording(private val context: Context, private val child: String) : SchoolImportWording {
        override val schoolHours: String = context.getString(R.string.school_import_hours_title, child)

        override fun dayOff(description: String, type: SchoolDayType): String = when {
            description.isNotBlank() -> context.getString(R.string.school_import_day_off_named, child, description)
            type == SchoolDayType.DIRECTOR_DAY -> context.getString(R.string.school_import_director_day, child)
            else -> context.getString(R.string.school_import_day_off, child)
        }
    }
}
