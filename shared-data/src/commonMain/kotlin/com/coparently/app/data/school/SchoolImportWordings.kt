package com.coparently.app.data.school

import com.coparently.app.domain.school.SchoolImportWording

/**
 * The titles an import writes, in the importing parent's language (MON-8): the platform's string
 * resources, which the app's `SchoolImportWordingSource` reads on Android.
 */
interface SchoolImportWordings {

    /**
     * The wording for the CoPlanly child [childId], named as the family named them; [fallbackName]
     * — the school's name for the child — when the child record is gone.
     */
    suspend fun forChild(childId: String, fallbackName: String): SchoolImportWording
}
