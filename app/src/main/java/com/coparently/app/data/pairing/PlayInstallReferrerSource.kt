package com.coparently.app.data.pairing

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import com.coparently.app.domain.pairing.InstallReferrer
import com.coparently.app.domain.pairing.InstallReferrerSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [InstallReferrerSource] over Google Play's Install Referrer service.
 *
 * The "already read" mark is a plain preference file, not `EncryptedPreferences`, on purpose: it
 * is not secret (the code itself is never stored), and `EncryptedPreferences.clear()` runs at
 * sign-out, which would re-arm the referrer for the next account on the phone. A definite answer
 * from Play — a referrer, "not supported", "unavailable" — marks it read; a timeout or an error
 * does not, so the next launch asks again.
 */
@Singleton
class PlayInstallReferrerSource @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallReferrerSource {

    private val marks by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override suspend fun pairingCodeOnce(): String? {
        if (marks.getBoolean(KEY_READ, false)) return null
        val answer = withTimeoutOrNull(TIMEOUT_MILLIS) { readReferrer() } ?: return null
        marks.edit().putBoolean(KEY_READ, true).apply()
        return InstallReferrer.pairingCode(answer.referrer)
    }

    /** Play's answer: [referrer] is null when the install carried none or Play could not say. */
    private class Answer(val referrer: String?)

    private suspend fun readReferrer(): Answer? = suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(context).build()
        continuation.invokeOnCancellation { runCatching { client.endConnection() } }
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val answer = when (responseCode) {
                        InstallReferrerResponse.OK ->
                            runCatching { Answer(client.installReferrer.installReferrer) }
                                .onFailure { Log.w(TAG, "Install referrer could not be read", it) }
                                .getOrNull()
                        InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                        InstallReferrerResponse.SERVICE_UNAVAILABLE -> Answer(null)
                        else -> null
                    }
                    runCatching { client.endConnection() }
                    if (continuation.isActive) continuation.resume(answer)
                }

                override fun onInstallReferrerServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "Install referrer service could not be reached", e)
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private companion object {
        const val TAG = "InstallReferrer"
        const val PREFS = "install_referrer"
        const val KEY_READ = "read"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
