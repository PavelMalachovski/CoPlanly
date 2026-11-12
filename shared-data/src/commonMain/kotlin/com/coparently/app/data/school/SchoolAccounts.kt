package com.coparently.app.data.school

import com.coparently.app.data.school.bakalari.BakalariAccount
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.BakalariException
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.data.school.bakalari.user
import com.coparently.app.data.school.edupage.EdupageAccount
import com.coparently.app.data.school.edupage.EdupageAccountParser
import com.coparently.app.data.school.edupage.EdupageChild
import com.coparently.app.data.school.edupage.EdupageClient
import com.coparently.app.data.school.edupage.EdupageException
import com.coparently.app.data.school.edupage.EdupageSession
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionId
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolStudent
import com.coparently.app.domain.school.SchoolSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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
 * An EduPage sign-in that has not been saved yet: the connect flow holds it while the parent
 * chooses the child. Unlike [SchoolSignIn] it holds the password, because EduPage's session cannot
 * be renewed without it; [toString] prints the host alone.
 *
 * @property session The signed-in session.
 * @property username The account.
 * @property password What the account signed in with, kept only to be stored sealed by
 *   [SchoolAccounts.connectEdupage].
 * @property account Who the login belongs to and which pupils it reads.
 */
data class EdupageSignIn(
    val session: EdupageSession,
    val username: String,
    val password: String,
    val account: EdupageAccount
) {
    override fun toString() = "EdupageSignIn(${session.host})"
}

/**
 * Connecting, reconnecting and disconnecting school accounts (MON-8).
 *
 * The phone signs in to the school itself; the password is passed to [signIn] or [reconnect] and
 * nowhere else, and neither keeps it.
 */
class SchoolAccounts(
    private val client: BakalariClient,
    private val edupageClient: EdupageClient,
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
     * Signs in to EduPage and reads who the login belongs to. Nothing is stored yet.
     *
     * @param schoolAddress The school's EduPage address as typed (`zsx`, `zsx.edupage.org`, a link),
     *   or blank to sign in at `login1.edupage.org`, which finds the school.
     * @throws EdupageException.NotEdupage when the address is not under `edupage.org`.
     * @throws EdupageException.BadCredentials on a wrong username or password.
     */
    suspend fun signInEdupage(schoolAddress: String?, username: String, password: String): EdupageSignIn {
        val host = normaliseEdupageAddress(schoolAddress)
        val session = edupageClient.signIn(host, username, password)
        return EdupageSignIn(session, username, password, EdupageAccountParser.parse(session.userhome))
    }

    /**
     * Saves [signIn] as the connection for the pupil [child] and the CoPlanly child [childId] in the
     * family [familyId], and starts the daily import. The password and the session are stored sealed.
     *
     * Signing the same account and pupil in again replaces the connection but keeps its record of
     * what it imported.
     *
     * @return The saved connection, or null when nobody is signed in.
     */
    suspend fun connectEdupage(
        signIn: EdupageSignIn,
        child: EdupageChild,
        childId: String,
        familyId: String?
    ): SchoolConnection? {
        val uid = userRepository.getCurrentUserId() ?: return null
        val baseUrl = "https://${signIn.session.host}"
        val id = SchoolConnectionId.of(uid, baseUrl, "${signIn.username}|${child.studentId}")
        val studentName = if (child.className.isBlank()) child.name else "${child.name}, ${child.className}"
        val stored = StoredSchoolConnection(
            connection = SchoolConnection(
                id = id,
                baseUrl = baseUrl,
                username = signIn.username,
                schoolName = signIn.account.schoolName,
                studentName = studentName,
                childId = childId,
                familyId = familyId,
                status = SchoolConnectionStatus.OK,
                lastSuccessAtMillis = null,
                system = SchoolSystem.EDUPAGE
            ),
            ownerUid = uid,
            student = SchoolStudent(child.studentId, studentName, child.className),
            tokens = BakalariTokens("", "", 0L),
            ledger = store.get(uid, id)?.ledger.orEmpty(),
            edupage = EdupageSecrets(signIn.password, signIn.session.cookies, child.studentId)
        )
        store.put(stored)
        scheduler.schedule()
        return stored.connection
    }

    /**
     * Signs the EduPage connection [connectionId] in again with the password, after EduPage refused
     * the stored one. The child, the family and every imported event stay as they were.
     *
     * @return The connection, ready again, or null when it is not an EduPage one of this account.
     * @throws EdupageException.BadCredentials on a wrong password.
     */
    suspend fun reconnectEdupage(connectionId: String, password: String): SchoolConnection? {
        val uid = userRepository.getCurrentUserId() ?: return null
        val stored = store.get(uid, connectionId) ?: return null
        if (stored.edupage == null) return null
        val host = stored.connection.baseUrl.removePrefix("https://")
        val session = edupageClient.signIn(host, stored.connection.username, password)
        return store.update(uid, connectionId) { current ->
            current.copy(
                edupage = current.edupage?.copy(password = password, cookies = session.cookies),
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
        SchoolConnectionId.of(uid, baseUrl, username)
}

/** Where EduPage's own sign-in finds the school when none is typed. */
private const val EDUPAGE_LOGIN_HOST = "login1.edupage.org"
private const val EDUPAGE_DOMAIN = "edupage.org"

/**
 * The EduPage host an [address] names: blank is `login1.edupage.org`; `zsx`, `zsx.edupage.org` and
 * `https://zsx.edupage.org/user` are `zsx.edupage.org`.
 *
 * @throws EdupageException.NotEdupage for anything outside `edupage.org`, so a typed address can
 *   never send the password elsewhere.
 */
internal fun normaliseEdupageAddress(address: String?): String {
    val typed = address?.trim()?.lowercase().orEmpty()
    if (typed.isEmpty()) return EDUPAGE_LOGIN_HOST
    val host = typed.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
    val valid = host.isNotEmpty() && host.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '.' }
    val full = if ('.' in host) host else "$host.$EDUPAGE_DOMAIN"
    val inside = full.endsWith(".$EDUPAGE_DOMAIN") && !full.startsWith(".") && ".." !in full
    if (!valid || !inside) throw EdupageException.NotEdupage("Not an EduPage address")
    return full
}
