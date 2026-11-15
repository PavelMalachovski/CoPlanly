package com.coparently.app.data.export

import platform.Foundation.NSBundle

/**
 * The verification page's address on the iPhone (MON-16; MON-24 phase 3, part C, Task 12): the app's
 * `Info.plist` key [KEY], which the Xcode build setting `COPLANLY_EXPORT_VERIFY_URL` fills, blank
 * by default as Android's `BuildConfig.EXPORT_VERIFY_URL` is while `web/verify/` is not hosted. Blank
 * (or missing, or a setting Xcode left unexpanded) leaves the line off the file
 * (`ExportReceipts.verifyUrl`).
 */
object InfoPlistExportVerifyAddress : ExportVerifyAddress {

    /** The `Info.plist` key. */
    const val KEY = "CoPlanlyExportVerifyUrl"

    override fun configured(): String = addressFrom(NSBundle.mainBundle.objectForInfoDictionaryKey(KEY) as? String)

    /** [value] as the address: blank when missing or when it is still a `$(…)` build-setting reference. */
    fun addressFrom(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        return if (trimmed.startsWith("$(")) "" else trimmed
    }
}
