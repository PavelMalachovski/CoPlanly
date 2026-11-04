package com.coparently.app.presentation.settings

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.coparently.app.R
import com.coparently.app.utils.AppLocale

/**
 * Languages offered by the in-app language picker in Settings.
 *
 * [SYSTEM] means "no per-app override": the UI follows the device locale, falling back
 * to English (the base resources) for unsupported device languages. The list must stay
 * in sync with `res/xml/locales_config.xml` and the `values-*` string folders in
 * `shared-ui/src/commonMain/composeResources/` (MON-24; the app's Android copy is generated from
 * them).
 *
 * @property tag BCP-47 language tag persisted by AppCompat (empty = follow the system).
 * @property labelRes Display label; language endonyms are intentionally not translated.
 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.settings_language_system),
    ENGLISH("en", R.string.settings_language_english),
    CZECH("cs", R.string.settings_language_czech),
    GERMAN("de", R.string.settings_language_german),
    RUSSIAN("ru", R.string.settings_language_russian),
    UKRAINIAN("uk", R.string.settings_language_ukrainian);

    companion object {

        /** The language currently selected in the picker (persisted by AppCompat). */
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val language = locales.get(0)?.language ?: return SYSTEM
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }

        /**
         * Applies [language] as the per-app locale. AppCompat recreates started activities
         * and (thanks to the `autoStoreLocales` manifest service) persists the choice across
         * process restarts; on Android 13+ it is also mirrored to the system per-app
         * language setting. Below Android 13 the choice is mirrored for the pushes and workers
         * at once (`AppLocale.rememberChoice`, so the mirror never lags it), and "System
         * default" over a choice starts the app again in a fresh process
         * ([SystemDefaultRelaunch]), so the owner's rule speaks there rather than Android's own
         * resolution. [context] is the screen's.
         */
        fun apply(language: AppLanguage, context: Context) {
            val hadChoice = !AppCompatDelegate.getApplicationLocales().isEmpty
            val plan = SystemDefaultRelaunch.plan(Build.VERSION.SDK_INT, language, hadChoice)
            if (plan == SystemDefaultRelaunch.Plan.NOTHING) return
            val locales = if (language == SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
            AppLocale.rememberChoice(context)
            if (plan == SystemDefaultRelaunch.Plan.SET_AND_RELAUNCH) SystemDefaultRelaunch.relaunch(context)
        }
    }
}
