package com.coparently.app.data.platform

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList

/**
 * The phone's languages as the system lists them — never as an app resolved them, nor the
 * process's default, which the app sets to the language it speaks (`AppLocale.follow`).
 *
 * On Android 13 and later `LocaleManager.getSystemLocales()`: the global configuration, in the
 * order the person set it. Below that `Resources.getSystem()`'s, which the framework orders by what
 * it can display itself, so a first language the phone cannot show (it then shows the next one)
 * reads as the one it shows.
 */
object PhoneLocales {

    /** The phone's languages, first first. */
    fun of(context: Context): LocaleList {
        val system = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.systemLocales
        } else {
            null
        }
        return system ?: Resources.getSystem().configuration.locales
    }
}

/** [DeviceRegion] on Android: the region of the phone's first language ([PhoneLocales]). */
class AndroidDeviceRegion(private val context: Context) : DeviceRegion {
    override fun code(): String = PhoneLocales.of(context)[0]?.country.orEmpty()
}
