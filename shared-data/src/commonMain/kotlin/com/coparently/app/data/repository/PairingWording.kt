package com.coparently.app.data.repository

/**
 * The words [PairingRepositoryImpl] writes into data that only the platform's resources hold
 * (MON-24 phase 2, Task 8f): on Android the app's string resources, in the phone's language.
 */
interface PairingWording {

    /**
     * The title of the thread with a co-parent whose profile carries no name yet ("Co-parent"):
     * the app's `pairing_default_partner_name`.
     */
    fun defaultPartnerName(): String
}
