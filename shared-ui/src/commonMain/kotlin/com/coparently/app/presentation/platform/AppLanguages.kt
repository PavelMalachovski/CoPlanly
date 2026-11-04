package com.coparently.app.presentation.platform

import com.coparently.app.presentation.settings.AppLanguage

/**
 * The app's own language, chosen in Settings (CLAUDE.md "Localization"): which one is chosen, and
 * applying another.
 *
 * On Android it is AppCompat's per-app locale, which recreates the activity in the new language and
 * persists the choice (and, below Android 13, "System default" over a choice starts the app again,
 * `SystemDefaultRelaunch`). The iPhone sets an app's language in its own Settings app, which phase 3
 * links to; until then the row is not offered there.
 */
interface AppLanguages : PlatformCapability {

    /** The language currently chosen in the picker ([AppLanguage.SYSTEM] for none). */
    fun current(): AppLanguage

    /** Applies [language] as the app's own language, as the picker's choice. */
    fun apply(language: AppLanguage)
}
