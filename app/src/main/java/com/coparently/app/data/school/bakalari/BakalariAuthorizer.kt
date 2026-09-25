package com.coparently.app.data.school.bakalari

import com.coparently.app.data.school.SchoolConnectionStore
import com.coparently.app.data.school.StoredSchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a school connection signed in (MON-8).
 *
 * Bakaláři's refresh token **rotates**: every refresh returns a new one, and a server may refuse
 * an old one after a few redemptions. So three rules, each one a way a connection dies:
 * - **One refresh at a time per connection**, under a [Mutex], and the token is re-read inside
 *   the lock — the daily job and "Update now" can race, and a second refresh of the same token
 *   may burn it.
 * - **The new pair is written down before it is used.** If the process dies between the refresh
 *   and the first call made with it, the stored token is still the live one.
 * - **`invalid_grant` marks the connection as needing the password**, and nothing is deleted:
 *   not the connection, not its child, not a single imported event.
 *
 * A `401` on a resource call refreshes once and retries once; a second `401` is the answer.
 */
@Singleton
class BakalariAuthorizer internal constructor(
    private val client: BakalariClient,
    private val store: SchoolConnectionStore,
    private val now: () -> Long
) {

    /** The production authorizer, on the wall clock. */
    @Inject
    constructor(client: BakalariClient, store: SchoolConnectionStore) : this(client, store, System::currentTimeMillis)

    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Runs [call] with a live access token for the connection [connectionId] of [uid].
     *
     * @throws BakalariException.NeedsPassword when the connection already waits for the password,
     *   or the server has just refused its refresh token.
     * @throws BakalariException.ConnectionGone when the connection is not stored for [uid].
     */
    suspend fun <T> withAccess(
        uid: String,
        connectionId: String,
        call: suspend (baseUrl: String, accessToken: String) -> T
    ): T {
        val first = liveConnection(uid, connectionId, rejectedAccessToken = null)
        return try {
            call(first.connection.baseUrl, first.tokens.accessToken)
        } catch (e: BakalariException.Unauthorized) {
            val second = liveConnection(uid, connectionId, rejectedAccessToken = first.tokens.accessToken)
            call(second.connection.baseUrl, second.tokens.accessToken)
        }
    }

    /**
     * The connection with a token worth sending: the stored one while it has not expired and was
     * not just refused, otherwise a fresh pair, stored first.
     */
    private suspend fun liveConnection(
        uid: String,
        connectionId: String,
        rejectedAccessToken: String?
    ): StoredSchoolConnection = lockFor(connectionId).withLock {
        val stored = store.get(uid, connectionId) ?: throw BakalariException.ConnectionGone()
        if (stored.connection.status == SchoolConnectionStatus.NEEDS_PASSWORD) {
            throw BakalariException.NeedsPassword()
        }
        val tokens = stored.tokens
        val usable = if (rejectedAccessToken == null) {
            tokens.accessToken.isNotBlank() && tokens.expiresAtMillis - EXPIRY_MARGIN_MS > now()
        } else {
            // Another caller refreshed while this one waited for the lock: its pair is new.
            tokens.accessToken != rejectedAccessToken
        }
        if (usable) stored else refreshed(uid, stored)
    }

    private suspend fun refreshed(uid: String, stored: StoredSchoolConnection): StoredSchoolConnection {
        val rotated = try {
            client.refresh(stored.connection.baseUrl, stored.tokens.refreshToken)
        } catch (e: BakalariException.InvalidGrant) {
            store.update(uid, stored.id) { it.withStatus(SchoolConnectionStatus.NEEDS_PASSWORD) }
            throw BakalariException.NeedsPassword()
        }
        // Written before it is used — see the class KDoc.
        return store.update(uid, stored.id) { it.copy(tokens = rotated) }
            ?: throw BakalariException.ConnectionGone()
    }

    private fun lockFor(connectionId: String): Mutex = locks.getOrPut(connectionId) { Mutex() }

    private companion object {
        /** A token this close to its expiry is refreshed first rather than sent and refused. */
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}

/** [this] with its status set to [status]. */
internal fun StoredSchoolConnection.withStatus(status: SchoolConnectionStatus): StoredSchoolConnection =
    copy(connection = connection.copy(status = status))
