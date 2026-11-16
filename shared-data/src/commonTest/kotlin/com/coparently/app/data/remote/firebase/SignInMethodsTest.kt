package com.coparently.app.data.remote.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Firebase's provider ids read as the app's [SignInMethod]s, and nothing else. */
class SignInMethodsTest {

    @Test
    fun `each provider id names its method`() {
        assertEquals(SignInMethod.APPLE, SignInMethod.fromProviderId("apple.com"))
        assertEquals(SignInMethod.GOOGLE, SignInMethod.fromProviderId("google.com"))
        assertEquals(SignInMethod.EMAIL, SignInMethod.fromProviderId("password"))
    }

    @Test
    fun `an unknown provider is no method`() {
        assertNull(SignInMethod.fromProviderId("firebase"))
        assertNull(SignInMethod.fromProviderId("phone"))
        assertNull(SignInMethod.fromProviderId(""))
    }

    @Test
    fun `the last method is never unlinked`() {
        assertEquals(LinkFailure.LastMethod, unlinkRefusal(setOf(SignInMethod.EMAIL), SignInMethod.EMAIL))
    }

    @Test
    fun `a method the account does not hold or a signed-out account is refused`() {
        assertEquals(LinkFailure.Other, unlinkRefusal(setOf(SignInMethod.EMAIL), SignInMethod.APPLE))
        assertEquals(LinkFailure.Other, unlinkRefusal(null, SignInMethod.EMAIL))
    }

    @Test
    fun `one of two methods may go`() {
        assertNull(unlinkRefusal(setOf(SignInMethod.EMAIL, SignInMethod.GOOGLE), SignInMethod.GOOGLE))
    }
}
