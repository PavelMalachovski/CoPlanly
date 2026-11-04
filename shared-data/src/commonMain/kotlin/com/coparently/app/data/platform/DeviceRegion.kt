package com.coparently.app.data.platform

/**
 * The phone's own region, as an ISO 3166 country code ("CZ"), or "" when it names none: the last
 * guess at a new expense's currency (`PreferencesRepositoryImpl`) and the onboarding's guess at the
 * parent's country.
 *
 * Never the region of the locale the app speaks. That one is the owner's language rule's
 * (`AppLanguageRule`), and its English fallback is European English, `en-150`, whose region is
 * "150" (Europe) on every phone: a Slovak phone reads English but is still in Slovakia, and the
 * euro is still what it is offered. On Android it is the region of the phone's first language as
 * the system lists it (`AndroidDeviceRegion`, the list `AppLocale.deviceFirst` reads); on the
 * iPhone the current locale's country code, the region the person set (`IosDeviceRegion`).
 */
fun interface DeviceRegion {

    /** The region's code now; read afresh on each call. */
    fun code(): String
}
