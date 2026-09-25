package com.coparently.app.data.school

import android.content.Context
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.InMemorySharedPreferences
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolStudent
import io.mockk.every
import io.mockk.mockk
import java.io.File

/** Shared fixtures for the school import's JVM tests (MON-8). */
internal object SchoolTestSupport {

    /** A Bakaláři sample from `src/test/resources/school/bakalari/`. */
    fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/school/bakalari/$name")) { "Missing fixture $name" }.readText()

    /**
     * The real [EncryptedPreferences] over [dir], sealed with a stand-in cipher: the Keystore does
     * not exist on a JVM, and what is under test is what the store keeps, not the Keystore.
     */
    fun preferences(dir: File): EncryptedPreferences {
        val context = mockk<Context>(relaxed = true)
        every { context.noBackupFilesDir } returns dir
        every { context.getSharedPreferences(any(), any()) } returns InMemorySharedPreferences()
        return EncryptedPreferences(context, ReversingCipher)
    }

    /** A stored connection for [uid], with the tokens given. */
    fun stored(
        uid: String = "alice",
        id: String = "conn1",
        baseUrl: String = "https://skola.bakalari.cz",
        tokens: BakalariTokens = BakalariTokens("access-1", "refresh-1", Long.MAX_VALUE),
        status: SchoolConnectionStatus = SchoolConnectionStatus.OK
    ) = StoredSchoolConnection(
        connection = SchoolConnection(
            id = id,
            baseUrl = baseUrl,
            username = "novak",
            schoolName = "ZŠ Beroun",
            studentName = "Nováková Anna, 5.A",
            childId = "child-1",
            familyId = "alice__bob",
            status = status,
            lastSuccessAtMillis = null
        ),
        ownerUid = uid,
        student = SchoolStudent(userUid = "1234/moje_id", fullName = "Nováková Anna, 5.A", classId = "XL"),
        tokens = tokens
    )

    private object ReversingCipher : EncryptedPreferences.StoreCipher {
        override fun seal(plain: String): String = plain.reversed()
        override fun open(sealed: String): String = sealed.reversed()
    }
}
