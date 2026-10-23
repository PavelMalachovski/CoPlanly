package com.coparently.app.data.pairing

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import com.coparently.app.domain.pairing.InstallReferrer
import com.coparently.app.domain.pairing.InstallReferrerSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What one of Play's answers means for asking again. */
internal enum class ReferrerOutcome {
    /** Play gave the referrer (which may carry no code). */
    ANSWERED,

    /** This Play Store has no referrer service; asking again will not change that. */
    NEVER,

    /** Busy, updating or disconnected: ask again on the next launch. */
    RETRY
}

/** Maps Play's response code to [ReferrerOutcome]. Only a definite answer settles the referrer. */
internal fun referrerOutcome(responseCode: Int): ReferrerOutcome = when (responseCode) {
    InstallReferrerResponse.OK -> ReferrerOutcome.ANSWERED
    InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> ReferrerOutcome.NEVER
    else -> ReferrerOutcome.RETRY
}

/**
 * [InstallReferrerSource] over Google Play's Install Referrer service.
 *
 * The "done" mark is a plain preference file, not `EncryptedPreferences`, on purpose: it is not
 * secret (the code itself is never stored), and `EncryptedPreferences.clear()` runs at sign-out,
 * which would offer the code again to the next account on the phone. It is set when the code has
 * been handed off ([markHandedOff]), or when Play has said for certain that there is no code — an
 * install from the plain listing, or a Play Store without the service — so an ordinary install
 * stops asking after its first launch.
 */
@Singleton
class PlayInstallReferrerSource @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallReferrerSource {

    private val marks by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override suspend fun pendingPairingCode(): String? {
        if (withContext(Dispatchers.IO) { marks.getBoolean(KEY_DONE, false) }) return null
        val answer = withTimeoutOrNull(TIMEOUT_MILLIS) { readReferrer() } ?: return null
        val code = answer.referrer?.let(InstallReferrer::pairingCode)
        if (code == null) markDone()
        return code
    }

    override fun markHandedOff() = markDone()

    private fun markDone() {
        marks.edit().putBoolean(KEY_DONE, true).apply()
    }

    /** A definite answer from Play: [referrer] is null when there is none and never will be. */
    private class Answer(val referrer: String?)

    private suspend fun readReferrer(): Answer? = suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(context).build()
        continuation.invokeOnCancellation { runCatching { client.endConnection() } }
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val answer = when (referrerOutcome(responseCode)) {
                        ReferrerOutcome.ANSWERED ->
                            runCatching { Answer(client.installReferrer.installReferrer) }
                                .onFailure { Log.w(TAG, "Install referrer could not be read", it) }
                                .getOrNull()
                        ReferrerOutcome.NEVER -> Answer(null)
                        ReferrerOutcome.RETRY -> null
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
        const val KEY_DONE = "handed_off"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
