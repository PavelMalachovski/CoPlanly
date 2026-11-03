package com.coparently.app.data.repository

import android.content.Context
import com.coparently.app.R

/**
 * [PairingWording] from the app's string resources, through the application context, as
 * `PairingRepositoryImpl` resolved them itself before it moved to `:shared-data` (MON-24 phase 2,
 * Task 8f).
 */
class AndroidPairingWording(private val context: Context) : PairingWording {

    override fun defaultPartnerName(): String = context.getString(R.string.pairing_default_partner_name)
}
