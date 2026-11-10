package com.coparently.app.data.repository

import android.content.Context
import com.coparently.app.R
import com.coparently.app.utils.inAppLanguage

/**
 * [PairingWording] from the app's string resources, as `PairingRepositoryImpl` resolved them itself
 * before it moved to `:shared-data` (MON-24 phase 2, Task 8f), in the language the app speaks when
 * it is asked ([inAppLanguage]).
 */
class AndroidPairingWording(private val context: Context) : PairingWording {

    override fun defaultPartnerName(): String = context.inAppLanguage().getString(R.string.pairing_default_partner_name)
}
