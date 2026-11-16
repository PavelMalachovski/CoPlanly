package com.coparently.app.ios

import platform.Foundation.NSNumber
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [appleSignInFrom]: how the bundle's `CoplanlyAppleSignIn` value reads as the flag. */
class AppleSignInFlagTest {

    @Test
    fun aMissingKeyIsOff() {
        assertFalse(appleSignInFrom(null))
    }

    @Test
    fun aPlistBooleanReadsAsItself() {
        assertTrue(appleSignInFrom(NSNumber(bool = true)))
        assertFalse(appleSignInFrom(NSNumber(bool = false)))
        assertTrue(appleSignInFrom(true))
    }

    @Test
    fun aSubstitutedBuildSettingReadsYesOrTrue() {
        assertTrue(appleSignInFrom("YES"))
        assertTrue(appleSignInFrom("true"))
        assertFalse(appleSignInFrom("NO"))
        assertFalse(appleSignInFrom(""))
    }
}
