package com.coparently.app.presentation.chat

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.coparently.app.utils.AppLocale

/*
 * The Android half of the composer's dictation language (`Dictation.rememberLanguageTag`, which
 * `presentation/platform/AndroidPlatform.kt` answers with it). It stayed in the app when chat's
 * screens moved to `:shared-ui` (MON-24 phase 2, Task 11, slice 11b), because it reads AppCompat's
 * per-app choice and the phone's own locale list.
 */

/**
 * The language to dictate in, as a BCP 47 tag: the per-app choice in Settings → Language when one
 * was made, else the phone's first language ([AppLocale.deviceFirst]) — never the English the app
 * falls back to for a language it does not ship (the owner's language rule, MON-24 phase 2). The
 * words are the parent's own, so a Slovak phone dictates Slovak while the app reads English.
 */
@Composable
fun rememberDictationLanguageTag(): String {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return remember(configuration) {
        val chosen = AppCompatDelegate.getApplicationLocales().get(0)
        (chosen ?: AppLocale.deviceFirst(context)).toLanguageTag()
    }
}
