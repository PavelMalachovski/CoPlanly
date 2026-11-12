package com.coparently.app.data.school

import com.coparently.app.data.school.edupage.EdupageAccount
import com.coparently.app.data.school.edupage.EdupageSession
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse

/** What a log line may show of an EduPage sign-in: never the password, a cookie value or the hash. */
class EdupageSecretsToStringTest {

    private val session = EdupageSession(
        host = "zsx.edupage.org",
        cookies = mapOf("PHPSESSID" to "cookie-value-77"),
        gsecHash = "gsec-hash-88",
        userhome = JsonObject(emptyMap())
    )

    @Test
    fun `the secrets omit the password and the cookies`() {
        val text = EdupageSecrets("hunter2", mapOf("PHPSESSID" to "cookie-value-77"), "-1234").toString()
        assertFalse("hunter2" in text)
        assertFalse("cookie-value-77" in text)
    }

    @Test
    fun `the sign-in omits the password and the session's values`() {
        val account = EdupageAccount("School", "Parent", true, emptyList(), 2026)
        val text = EdupageSignIn(session, "novak", "hunter2", account).toString()
        assertFalse("hunter2" in text)
        assertFalse("cookie-value-77" in text)
        assertFalse("gsec-hash-88" in text)
    }

    @Test
    fun `the session omits its cookie values and its hash`() {
        val text = session.toString()
        assertFalse("cookie-value-77" in text)
        assertFalse("gsec-hash-88" in text)
    }
}
