package com.coparently.app.presentation.settings

import com.coparently.app.resources.Res
import com.coparently.app.resources.settings_language_czech
import com.coparently.app.resources.settings_language_english
import com.coparently.app.resources.settings_language_german
import com.coparently.app.resources.settings_language_russian
import com.coparently.app.resources.settings_language_system
import com.coparently.app.resources.settings_language_ukrainian
import org.jetbrains.compose.resources.StringResource

/**
 * Languages offered by the in-app language picker in Settings.
 *
 * [SYSTEM] means "no per-app override": the UI follows the device locale, falling back
 * to English (the base resources) for unsupported device languages. The list must stay
 * in sync with `res/xml/locales_config.xml` and the `values-*` string folders in
 * `shared-ui/src/commonMain/composeResources/` (MON-24; the app's Android copy is generated from
 * them).
 *
 * Which language is chosen, and applying a new one, are the phone's (`AppLanguages` in
 * `presentation/platform/`, MON-24 phase 2, slice 11f): on Android AppCompat's per-app locale, as
 * the picker set it before it moved to `:shared-ui`.
 *
 * @property tag BCP-47 language tag persisted by AppCompat (empty = follow the system).
 * @property labelRes Display label; language endonyms are intentionally not translated.
 */
enum class AppLanguage(val tag: String, val labelRes: StringResource) {
    SYSTEM("", Res.string.settings_language_system),
    ENGLISH("en", Res.string.settings_language_english),
    CZECH("cs", Res.string.settings_language_czech),
    GERMAN("de", Res.string.settings_language_german),
    RUSSIAN("ru", Res.string.settings_language_russian),
    UKRAINIAN("uk", Res.string.settings_language_ukrainian),
}
