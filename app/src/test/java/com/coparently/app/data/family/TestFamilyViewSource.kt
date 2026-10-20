package com.coparently.app.data.family

import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A real [FamilyViewSource] over a stubbed sign-in and the signed-in parent's Room row.
 *
 * The real class rather than a mock, for the reason `testParentsSource` gives: what a screen does
 * with a switch is the point, and a mock would let the view and the row describe different
 * families. Signed out (no [signedInUid]) it sees everything, as the app does.
 *
 * @param signedInUid The signed-in parent, or null for nobody.
 * @param row The parent's Room row; set its value to switch families or change the co-parent set.
 */
fun testFamilyViewSource(
    signedInUid: String? = null,
    row: StateFlow<UserEntity?> = MutableStateFlow(null)
): FamilyViewSource {
    val user = signedInUid?.let { id -> mockk<FirebaseUser> { every { uid } returns id } }
    val auth = mockk<FirebaseAuthService> { every { getCurrentUser() } returns user }
    val userDao = mockk<UserDao> {
        every { observeUserById(any()) } returns row
        coEvery { getUserById(any()) } answers { row.value }
    }
    return FamilyViewSource(auth, userDao)
}

/**
 * [uid]'s Room row with the family with [onScreen] on screen and [partners] as every co-parent, in
 * the JSON form `partnerIdsJson` stores.
 */
fun familyRow(uid: String, onScreen: String?, partners: List<String>) = UserEntity(
    id = uid,
    email = "$uid@example.test",
    name = uid,
    role = "mom",
    colorCode = "#FF4081",
    partnerId = onScreen,
    partnerIdsJson = partners.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
)
