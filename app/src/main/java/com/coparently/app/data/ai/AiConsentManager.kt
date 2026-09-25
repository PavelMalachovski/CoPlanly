package com.coparently.app.data.ai

import android.util.Log
import com.coparently.app.domain.ai.AI_CONSENT_VERSION
import com.coparently.app.domain.ai.AiConsent
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The AI-assist consent: whether this parent has it, recording it, and withdrawing it.
 *
 * One place for all three, shared by the reply suggestion, the month summary and the Settings row,
 * so the gate cannot mean one thing on one screen and another on the next — the shape
 * `HealthConsentManager` gives the child-health consent. [UserRepository.setAiConsent] is the only
 * writer of `users/{uid}.aiConsent`, and every write goes through here.
 *
 * The stored copy lives in Firestore alone (no Room column, so no schema change), and this class
 * keeps the last answer per account in memory so a second request does not read the profile again.
 * The copy only decides whether to **ask** before calling: the server checks the document on every
 * request and answers `ai-consent-required` when it disagrees, and [forget] then drops the copy so
 * the parent is asked again.
 *
 * @param userRepository Holds the consent on the signed-in parent's profile.
 */
@Singleton
class AiConsentManager @Inject constructor(
    private val userRepository: UserRepository
) {

    /** The last answer read or written, and whose it is. */
    private data class Known(val uid: String, val consent: AiConsent?)

    private val known = MutableStateFlow<Known?>(null)

    /**
     * The signed-in parent's consent, or null when never given, withdrawn or signed out. Reads the
     * profile once per account and then follows this device's own writes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<AiConsent?> = userRepository.observeCurrentUserId()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(null)
            } else {
                flow<AiConsent?> {
                    if (known.value?.uid != uid) load(uid)
                    emitAll(known.map { it?.takeIf { entry -> entry.uid == uid }?.consent })
                }
            }
        }

    /** The signed-in parent's consent now, read from the profile unless already known. */
    suspend fun current(): AiConsent? {
        val uid = userRepository.getCurrentUserId() ?: return null
        known.value?.takeIf { it.uid == uid }?.let { return it.consent }
        return load(uid)
    }

    /**
     * Records the consent at [AI_CONSENT_VERSION]. True once the server has it; false when signed
     * out, when the write failed, or when it did not land within [WRITE_TIMEOUT_MS] — then the
     * parent is told it was not saved, and the next request asks again.
     */
    suspend fun grant(): Boolean = write(AI_CONSENT_VERSION)

    /** Deletes the consent. True once the server has the deletion. */
    suspend fun withdraw(): Boolean = write(null)

    /**
     * Drops the remembered answer for the signed-in account — the server said it holds no current
     * consent, so the next request reads the profile (and, finding none, asks).
     */
    fun forget() {
        known.value = null
    }

    private suspend fun write(version: Int?): Boolean {
        val uid = userRepository.getCurrentUserId() ?: return false
        val saved = try {
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { userRepository.setAiConsent(version) } == true
        } catch (e: CancellationException) {
            throw e
        } catch (
            // The repository reports a failed write as false; anything thrown past it is the same
            // answer, and the caller words it.
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.e(TAG, "Writing the AI consent failed", e)
            false
        }
        if (saved) {
            // The server's timestamp is not known until the profile is read again; the version is.
            known.value = Known(uid, version?.let { AiConsent(it, grantedAtMillis = null) })
        }
        return saved
    }

    private suspend fun load(uid: String): AiConsent? {
        val consent = try {
            userRepository.getAiConsent()
        } catch (e: CancellationException) {
            throw e
        } catch (
            // An unreadable profile is "not known": the parent is asked, and the server decides.
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "Reading the AI consent failed", e)
            null
        }
        known.value = Known(uid, consent)
        return consent
    }

    private companion object {
        const val TAG = "AiConsentManager"

        /** Long enough for a slow network; offline, a consent write would otherwise wait forever. */
        const val WRITE_TIMEOUT_MS = 15_000L
    }
}
