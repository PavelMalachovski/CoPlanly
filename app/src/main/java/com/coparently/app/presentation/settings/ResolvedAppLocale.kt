package com.coparently.app.presentation.settings

import android.content.Context
import android.os.LocaleList
import java.util.Locale

/**
 * Puts the locale Android's resources resolved first in the process's default locale list, so text
 * read through Compose Multiplatform's resources speaks the language `R.string` does (MON-24 phase
 * 2, owner decision of 4 November 2026, option (a) of ROADMAP MON-24).
 *
 * Android resolves a locale from what the whole APK translates, libraries included: its resources
 * move the best match of the device's list to the front of their configuration's locales, and the
 * framework makes that one `java.util.Locale.getDefault()` (`LocaleList.setDefault(list, index)`).
 * It leaves `android.os.LocaleList.getDefault()` as the device's own list, and that list's first
 * entry is what Compose's `Locale.current`, and so Compose Multiplatform's resource environment,
 * reads. Whenever the two differ — the device's first language is one the APK does not translate
 * — `R.string` and the text read through Compose Multiplatform could be in two languages on one
 * screen. [follow] makes them one, without touching `java.util.Locale.getDefault()` (already the
 * resolved locale), so `java.time`'s formatting and the device region the currency hint reads
 * stay as Android set them.
 *
 * It changes nothing when the device's first locale is the resolved one, which a per-app choice
 * always makes it (AppCompat sets the default list itself below Android 13).
 */
internal object ResolvedAppLocale {

    /**
     * The default list Compose must see, given the [defaults] the process holds and the locale
     * Android's resources [resolved], or null when its first entry is that locale already.
     */
    fun adjusted(defaults: List<Locale>, resolved: Locale): List<Locale>? {
        if (defaults.firstOrNull() == resolved) return null
        return listOf(resolved) + defaults.filter { it != resolved }
    }

    /**
     * Applies [adjusted] for [context]'s resources. Called by `MainActivity` after AppCompat has
     * applied a per-app choice and before anything is composed; a change of language recreates the
     * activity, which calls it again.
     */
    fun follow(context: Context) {
        val resolved = context.resources.configuration.locales[0] ?: return
        val defaults = LocaleList.getDefault().let { list -> (0 until list.size()).map { list[it] } }
        val locales = adjusted(defaults, resolved) ?: return
        LocaleList.setDefault(LocaleList(*locales.toTypedArray()))
    }
}
