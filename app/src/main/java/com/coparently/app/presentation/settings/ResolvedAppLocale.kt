package com.coparently.app.presentation.settings

import android.content.Context
import android.os.LocaleList
import java.util.Locale

/**
 * Makes the process's default locale the language Android's resources resolved, so text read
 * through Compose Multiplatform's resources speaks the language `R.string` does (MON-24 phase 2,
 * owner decision of 4 November 2026, option (a) of ROADMAP MON-24).
 *
 * The two readers fall back differently. Android's resources take the first locale of the
 * configuration's list that the app translates; Compose Multiplatform 1.9.3 takes the first
 * locale's language alone (Compose's `Locale.current`, which follows the process's default) and
 * otherwise reads `values/`. On a phone in Slovak then Czech with no per-app choice, `R.string`
 * was Czech and the moved labels English. [follow] puts the language Android chose first in the
 * default list, keeping the device's region, so the currency hint and number formats still see
 * the phone's country (`deviceRegionCode`). It also moves `java.time`'s formatting language to it,
 * which is the point of the option: one language on the whole screen.
 *
 * It changes nothing when the first locale is one the app translates (a per-app choice always
 * is), and nothing when no locale is: both readers then show English.
 */
internal object ResolvedAppLocale {

    /** The languages the app ships, the picker's own list ([AppLanguage]) without "System". */
    val SHIPPED: Set<String> = AppLanguage.entries.map { it.tag }.filter { it.isNotEmpty() }.toSet()

    /**
     * The default locale Compose Multiplatform must see for [configured], given the process's
     * [current] default, or null when it already sees the right language.
     *
     * @param configured the configuration's locales, in order (the device's list, or the per-app
     *   choice)
     * @param current the process's default locale now
     * @param shipped the languages the app translates
     */
    fun target(configured: List<Locale>, current: Locale, shipped: Set<String> = SHIPPED): Locale? {
        val resolved = configured.firstOrNull { it.language in shipped } ?: return null
        if (resolved.language == current.language) return null
        return runCatching {
            Locale.Builder().setLanguage(resolved.language).setRegion(current.country).build()
        }.getOrElse { Locale(resolved.language) }
    }

    /**
     * Applies [target] to the process for [context]'s configuration. Called by `MainActivity`
     * after AppCompat has applied a per-app choice and before anything is composed; a change of
     * language recreates the activity, which calls it again.
     */
    fun follow(context: Context) {
        val list = context.resources.configuration.locales
        val configured = (0 until list.size()).map { list[it] }
        val locale = target(configured, Locale.getDefault()) ?: return
        LocaleList.setDefault(LocaleList(locale, *configured.filter { it != locale }.toTypedArray()))
    }
}
