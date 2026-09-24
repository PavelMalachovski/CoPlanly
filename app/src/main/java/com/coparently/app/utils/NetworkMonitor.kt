package com.coparently.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network connectivity monitor using ConnectivityManager.
 * Provides real-time network status updates.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Checks if the device is currently online.
     */
    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Whether the device has validated internet, as it changes.
     *
     * Tracks every network that currently has it rather than reacting to single callbacks: a
     * device holds two networks at once while it hands over from Wi-Fi to mobile data, and the
     * old version answered `onLost(wifi)` with "offline" after mobile data had already come up —
     * which the offline banner (docs/AUDIT-2026-10-design.md D-16) would then have shown for as
     * long as nothing else changed. Callbacks arrive on one thread, so the set needs no lock.
     */
    val networkStatus: Flow<Boolean> = callbackFlow {
        val validated = mutableSetOf<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                ) && networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
                if (hasInternet) validated += network else validated -= network
                trySend(validated.isNotEmpty())
            }

            override fun onLost(network: Network) {
                validated -= network
                trySend(validated.isNotEmpty())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        // The state before the first callback; registering reports every matching network
        // through onCapabilitiesChanged straight after.
        trySend(isOnline())
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
