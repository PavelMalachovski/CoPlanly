package com.coparently.app.data.school

import com.coparently.app.data.school.bakalari.BakalariAccount
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.BakalariException
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A sign-in that has not been saved yet: the connect flow holds it while the parent chooses the
 * child. The password is already gone — only the tokens it bought are here.
 *
 * @property baseUrl The school's server.
 * @property username The account.
 * @property tokens The session the password bought.
 * @property account Who the login belongs to.
 */
data class SchoolSignIn(
    val baseUrl: String,
    val username: String,
    val tokens: BakalariTokens,
    val account: BakalariAccount
)

/**
 * Connecting, reconnecting and disconnecting school accounts (MON-8).
 *
 * The phone signs in to the school itself; the password is passed to [signIn] or [reconnect] and
 * nowhere else, and neither keeps it.
 */
@Singleton
class SchoolAccounts @Inject constructor(
    private val client: BakalariClient,
    private val store: SchoolConnectionStore,
    private val userRepository: UserRepository,
    private val scheduler: SchoolImportScheduler
) {

    /** The signed-in account's connections, following sign-in, sign-out and every write. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<List<SchoolConnection>> =
        userRepository.observeCurrentUserId().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else store.observe(uid).map { list -> list.map { it.connection } }
        }

    /** The connection [connectionId] of the signed-in account, or null. */
    suspend fun connection(connectionId: String): SchoolConnection? {
        val uid = userRepository.getCurrentUserId() ?: return null
        return store.get(uid, connectionId)?.connection
    }

    /**
     * Whether [baseUrl] is a Bakaláři server, for an address typed by hand.
     *
     * @throws BakalariException when it is not, or cannot be reached.
     */
    suspend fun checkServer(baseUrl: String) {
        client.checkServer(baseUrl)
    }

    /**
     * Signs in and reads who the login belongs to. Nothing is stored yet.
     *
     * @throws BakalariException.InvalidGrant on a wrong username or password.
     */
    suspend fun signIn(baseUrl: String, username: String, password: String): SchoolSignIn {
        val tokens = client.signIn(baseUrl, username, password)
        return SchoolSignIn(baseUrl, username, tokens, client.user(baseUrl, tokens.accessToken))
    }

    /**
     * Saves [signIn] as the connection for the CoPlanly child [childId] in the family [familyId],
     * and starts the daily import.
     *
     * Signing the same account in again replaces its connection but keeps its record of what it
     * imported, so nothing it created before — or that the parent deleted since — comes back.
     *
     * @return The saved connection, or null when nobody is signed in.
     */
    suspend fun connect(
        signIn: SchoolSignIn,
        schoolName: String,
        childId: String,
        familyId: String?
    ): SchoolConnection? {
        val uid = userRepository.getCurrentUserId() ?: return null
        val id = connectionId(uid, signIn.baseUrl, signIn.username)
        val stored = StoredSchoolConnection(
            connection = SchoolConnection(
                id = id,
                baseUrl = signIn.baseUrl,
                username = signIn.username,
                schoolName = schoolName.ifBlank { signIn.account.schoolName },
                studentName = signIn.account.displayName,
                childId = childId,
                familyId = familyId,
                status = SchoolConnectionStatus.OK,
                lastSuccessAtMillis = null
            ),
            ownerUid = uid,
            student = signIn.account.student,
            tokens = signIn.tokens,
            ledger = store.get(uid, id)?.ledger.orEmpty()
        )
        store.put(stored)
        scheduler.schedule()
        return stored.connection
    }

    /**
     * Signs the connection [connectionId] in again with the password, after the school refused its
     * stored sign-in. The child, the family and every imported event stay as they were.
     *
     * @return The connection, ready again, or null when it is not stored for this account.
     * @throws BakalariException.InvalidGrant on a wrong password.
     */
    suspend fun reconnect(connectionId: String, password: String): SchoolConnection? {
        val uid = userRepository.getCurrentUserId() ?: return null
        val stored = store.get(uid, connectionId) ?: return null
        val tokens = client.signIn(stored.connection.baseUrl, stored.connection.username, password)
        return store.update(uid, connectionId) { current ->
            current.copy(
                tokens = tokens,
                connection = current.connection.copy(status = SchoolConnectionStatus.OK)
            )
        }?.connection
    }

    /**
     * Forgets the connection [connectionId]: its tokens leave the phone and the daily import stops
     * once no connection is left. The events it imported stay in the calendar as ordinary events.
     */
    suspend fun disconnect(connectionId: String) {
        val uid = userRepository.getCurrentUserId() ?: return
        store.remove(uid, connectionId)
        if (store.all(uid).isEmpty()) scheduler.cancel()
    }

    private fun connectionId(uid: String, baseUrl: String, username: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$uid|$baseUrl|$username".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
            .take(CONNECTION_ID_LENGTH)

    private companion object {
        const val CONNECTION_ID_LENGTH = 16
    }
}
