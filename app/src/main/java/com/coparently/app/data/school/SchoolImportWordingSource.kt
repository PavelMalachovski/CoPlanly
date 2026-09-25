package com.coparently.app.data.school

import android.content.Context
import com.coparently.app.R
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.domain.school.SchoolImportWording
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The titles an import writes, in the importing parent's language (MON-8).
 *
 * A title is data once written, like one a parent typed: the co-parent reads it as written. It
 * names the child — "Anna at school" — because a family with two children at school would
 * otherwise read two identical rows, and a member is a name, never a colour (FAM-2).
 *
 * Resolved from the application's resources, because the import runs in a worker with no
 * activity: on Android 12L and below that follows the device's language rather than the app's
 * own choice, as a push does (CLAUDE.md item 15).
 */
@Singleton
class SchoolImportWordingSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val childInfoRepository: ChildInfoRepository
) {

    /**
     * The wording for the CoPlanly child [childId], named as the family named them; [fallbackName]
     * — the school's name for the child — when the child record is gone.
     */
    suspend fun forChild(childId: String, fallbackName: String): SchoolImportWording {
        val name = childInfoRepository.getChildInfoById(childId)?.childName?.takeIf { it.isNotBlank() }
            ?: fallbackName
        return ResourceWording(context, name)
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
