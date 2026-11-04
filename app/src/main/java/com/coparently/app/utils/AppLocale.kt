package com.coparently.app.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import com.coparently.app.domain.language.AppLanguageRule
import java.util.Locale

/**
 * Android's half of [AppLanguageRule] (the owner's language rule, 4 November 2026): which locale
 * the app speaks on this phone now, and the contexts and process defaults that speak it.
 *
 * - **An activity** is given its base context already in that locale (`attachBaseContext`, before
 *   AppCompat's delegate, which carries it on as a configuration overlay and puts a per-app choice
 *   below Android 13 on top), so `R.string`, `LocalConfiguration` and the layout direction follow.
 * - **Every other reader** — a push, a notice, a worker, the widget, a wording the data layer asks
 *   for — words through [inAppLanguage], a configuration context made afresh for each use from
 *   what the rule says at that moment, never one wrapped once and kept.
 * - **The process's defaults** (`java.util.Locale` and `android.os.LocaleList`, which `java.time`,
 *   `NumberFormat` and Compose's `Locale.current` — and so Compose Multiplatform's strings — read)
 *   are set by [follow] at the application's start, on each configuration change and in each
 *   activity, after AppCompat, so one screen and one notification speak one language, dates
 *   included. The framework resets them on a configuration change, before the application hears of
 *   it, and that is why [follow] runs again there.
 *
 * Where the inputs come from:
 * - **the per-app choice**: Android 13 and later answer it themselves ([LocaleManager]); below
 *   that AppCompat holds it, but only once an activity has attached, so [rememberChoice] mirrors it
 *   for a push or a worker in a process no activity has started;
 * - **the phone's first language**: the system's own list, never one an app resolved or a default
 *   this class has set — `LocaleManager.getSystemLocales()` on Android 13 and later, the global
 *   configuration in the order the person set it; below that `Resources.getSystem()`'s, which the
 *   framework orders by what it can display itself, so a first language the phone cannot show (it
 *   then shows the next one) reads as the one it shows.
 */
object AppLocale {

    /** The plain preference file the per-app choice is mirrored in below Android 13. */
    private const val CHOICE_FILE = "app_language"

    /** The choice as BCP 47 tags, `LocaleListCompat.toLanguageTags()`; empty for none. */
    private const val CHOICE_KEY = "per_app_choice"

    /** The locale the app speaks for [context]'s app now. */
    fun effective(context: Context): Locale = effective(perAppChoice(context), deviceFirst(context))

    /**
     * [AppLanguageRule.effective] over [Locale]s: [perAppChoice] or [deviceFirst] itself when the
     * rule keeps one of them (with any Unicode extension the system added), else the English
     * fallback the rule names.
     */
    internal fun effective(perAppChoice: Locale?, deviceFirst: Locale): Locale {
        val choiceTag = perAppChoice?.toLanguageTag()
        val deviceTag = deviceFirst.toLanguageTag()
        return when (val tag = AppLanguageRule.effective(choiceTag, deviceTag)) {
            choiceTag -> perAppChoice
            deviceTag -> deviceFirst
            else -> Locale.forLanguageTag(tag)
        } ?: deviceFirst
    }

    /** The phone's first language, as the system lists it (see the class's KDoc). */
    fun deviceFirst(context: Context): Locale {
        val system = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.systemLocales
        } else {
            null
        }
        return (system ?: Resources.getSystem().configuration.locales)[0] ?: Locale.getDefault()
    }

    /**
     * A context like [base] in [locale] and nothing else: the override configuration names only the
     * locale and its layout direction, so the night mode, font scale and density stay [base]'s.
     */
    fun contextIn(base: Context, locale: Locale): Context {
        val override = Configuration().apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(override)
    }

    /**
     * Sets the process's default locale list to the effective locale alone, so `java.time`, number
     * formats and Compose's `Locale.current` speak what `R.string` does. Nothing happens when they
     * already do.
     */
    fun follow(context: Context) {
        val locale = effective(context)
        val defaults = LocaleList.getDefault()
        if (defaults.size() == 1 && defaults[0] == locale && Locale.getDefault() == locale) return
        LocaleList.setDefault(LocaleList(locale))
    }

    /**
     * Mirrors AppCompat's per-app choice below Android 13, where nothing outside an activity can
     * read it. `MainActivity` calls it after AppCompat has applied the choice; a change of choice
     * recreates the activity, which calls it again.
     */
    fun rememberChoice(activity: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val preferences = activity.applicationContext.getSharedPreferences(CHOICE_FILE, Context.MODE_PRIVATE)
        if (preferences.getString(CHOICE_KEY, "") != tags) {
            preferences.edit().putString(CHOICE_KEY, tags).apply()
        }
    }

    /** The per-app choice's first locale, or null when the app follows the phone. */
    internal fun perAppChoice(context: Context): Locale? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val chosen = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return chosen?.takeUnless { it.isEmpty }?.get(0)
        }
        val requested = AppCompatDelegate.getApplicationLocales()
        if (!requested.isEmpty) return requested[0]
        val remembered = context.applicationContext
            .getSharedPreferences(CHOICE_FILE, Context.MODE_PRIVATE)
            .getString(CHOICE_KEY, "")
            .orEmpty()
        return remembered.takeIf { it.isNotBlank() }?.let { LocaleList.forLanguageTags(it)[0] }
    }
}

/**
 * This context in the language the app speaks now ([AppLocale]): what a reader outside an activity
 * words through. Made afresh on each call, so a change of language or of per-app choice is read
 * the next time, and nothing else about the context changes.
 */
fun Context.inAppLanguage(): Context = AppLocale.contextIn(this, AppLocale.effective(this))
