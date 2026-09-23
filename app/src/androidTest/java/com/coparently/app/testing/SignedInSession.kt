package com.coparently.app.testing

import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.telemetry.TelemetryConsent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Puts the app into the state a returning parent opens it in — signed in, through onboarding, the
 * telemetry question answered — so a test that launches `MainActivity` lands on Home rather than
 * on the consent or sign-in screen, and undoes it afterwards.
 *
 * Everything here goes through the test's own Hilt graph: [FirebaseAuth] is the relaxed mock
 * `FakeFirebaseModule` installs, so "signed in" is a stubbed `currentUser`, and the Room database
 * is the real (SQLCipher) one, holding a real `users` row for that uid. Nothing reaches a server.
 * The start-destination decision in `NavGraph` reads exactly these three things: the consent, the
 * Firebase user, and whether that user's Room row says onboarding is done.
 *
 * Call [signIn] after `hiltRule.inject()` and before launching the activity, and [signOut] in
 * `@After`: the database and the encrypted preferences are files on the emulator, and a test that
 * left a signed-in row or an answered consent behind would change what every later test sees.
 */
class SignedInSession @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val userDao: UserDao,
    private val encryptedPreferences: EncryptedPreferences,
    private val preferencesRepository: PreferencesRepository
) {

    private var savedConsent: String? = null

    /** Signs [UID] in, marks it onboarded, and answers the telemetry question with "no". */
    fun signIn() {
        savedConsent = encryptedPreferences.getString(PreferenceKeys.TELEMETRY_CONSENT, null)
        val user = mockk<FirebaseUser>(relaxed = true) {
            every { uid } returns UID
            every { email } returns EMAIL
            every { displayName } returns NAME
            every { photoUrl } returns null
            every { isAnonymous } returns false
            every { providerData } returns emptyList()
        }
        every { firebaseAuth.currentUser } returns user
        // A real FirebaseAuth calls a listener with the current state as soon as it is added.
        every { firebaseAuth.addAuthStateListener(any()) } answers {
            firstArg<FirebaseAuth.AuthStateListener>().onAuthStateChanged(firebaseAuth)
        }
        runBlocking {
            userDao.insertUser(
                UserEntity(
                    id = UID,
                    email = EMAIL,
                    name = NAME,
                    role = "mom",
                    colorCode = "#FF4081",
                    onboardingCompletedAt = "2026-09-01T00:00:00"
                )
            )
            // "No" rather than "yes": nothing in a test should switch collection on, even a mock.
            preferencesRepository.setTelemetryConsent(TelemetryConsent.DENIED)
        }
    }

    /** Removes the row and puts the consent back as it was — unanswered, if it was. */
    fun signOut() {
        runBlocking { userDao.deleteUserById(UID) }
        encryptedPreferences.putString(PreferenceKeys.TELEMETRY_CONSENT, savedConsent.orEmpty())
    }

    companion object {
        /** A uid no real account has, so the row this writes cannot be mistaken for one. */
        const val UID = "instrumented-test-uid"
        const val EMAIL = "instrumented-test@example.invalid"
        const val NAME = "Alex"
    }
}
