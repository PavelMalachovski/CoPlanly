package com.coparently.app.utils

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import com.coparently.app.data.platform.PhoneLocales
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
 *   then shows the next one) reads as the one it shows. It is `shared-data`'s [PhoneLocales], whose
 *   first entry's region is also the phone's region the currency hint and the onboarding read
 *   (`AndroidDeviceRegion`), never the region of the locale the app speaks.
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
    fun deviceFirst(context: Context): Locale = PhoneLocales.of(context)[0] ?: Locale.getDefault()

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
     * Sets the process's default locale list to the effective locale first ([defaultLocalesFor]), so
     * `java.time`, number formats and Compose's `Locale.current` speak what `R.string` does. Nothing
     * happens when they already do.
     */
    fun follow(context: Context) {
        val locale = effective(context)
        val defaults = defaultLocalesFor(locale, PhoneLocales.of(context))
        if (LocaleList.getDefault() == defaults && Locale.getDefault() == locale) return
        LocaleList.setDefault(defaults)
    }

    /**
     * Mirrors AppCompat's per-app choice below Android 13, where nothing outside an activity can
     * read it. `AppLanguage.apply` calls it right after handing AppCompat a new choice, and
     * `MainActivity` after AppCompat has applied one (a cold start restores it from AppCompat's
     * own storage).
     */
    fun rememberChoice(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) storeChoice(context)
    }

    /** The per-app choice's first locale, or null when the app follows the phone. */
    internal fun perAppChoice(context: Context): Locale? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val chosen = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return chosen?.takeUnless { it.isEmpty }?.get(0)
        }
        return appCompatOrRememberedChoice(context)
    }

    /**
     * Below Android 13: AppCompat's choice once an activity has loaded it, else the one
     * [rememberChoice] last mirrored. (Internal and not gated on the API level, for the JVM tests.)
     */
    internal fun appCompatOrRememberedChoice(context: Context): Locale? {
        val requested = AppCompatDelegate.getApplicationLocales()
        if (!requested.isEmpty) return requested[0]
        val remembered = choicePreferences(context).getString(CHOICE_KEY, "").orEmpty()
        return remembered.takeIf { it.isNotBlank() }?.let { LocaleList.forLanguageTags(it)[0] }
    }

    /**
     * Writes AppCompat's choice into the mirror ([rememberChoice] below Android 13; the tests).
     * With `commit`, only when it changed: "System default" below 13 ends the process right after
     * (`SystemDefaultRelaunch`), and an `apply` still in flight would be lost with it.
     */
    @SuppressLint("ApplySharedPref")
    internal fun storeChoice(context: Context) {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val preferences = choicePreferences(context)
        if (preferences.getString(CHOICE_KEY, "") != tags) {
            preferences.edit().putString(CHOICE_KEY, tags).commit()
        }
    }

    private fun choicePreferences(context: Context) =
        context.applicationContext.getSharedPreferences(CHOICE_FILE, Context.MODE_PRIVATE)
}

/**
 * This context in the language the app speaks now ([AppLocale]): what a reader outside an activity
 * words through. Made afresh on each call, so a change of language or of per-app choice is read
 * the next time, and nothing else about the context changes.
 */
fun Context.inAppLanguage(): Context = AppLocale.contextIn(this, AppLocale.effective(this))

/**
 * The default locale list [AppLocale.follow] sets: [locale], then the phone's other languages in
 * their order. Only the first entry decides the language (`Locale.getDefault()`, Compose's
 * `Locale.current` and so Compose Multiplatform's strings read nothing else); the rest is what text
 * drawing reads beyond it — which glyphs a Han character takes, how a word hyphenates — so a phone
 * in Japanese still draws the co-parent's Japanese as Japanese under the English the app speaks.
 */
internal fun defaultLocalesFor(locale: Locale, phone: LocaleList): LocaleList {
    val others = (0 until phone.size()).mapNotNull { phone[it] }.filter { it != locale }.distinct()
    return LocaleList(locale, *others.toTypedArray())
}
