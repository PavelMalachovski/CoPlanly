package com.coparently.app.data.pairing

import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which of Play's answers settle the referrer for good, and which are asked again next launch. */
class PlayInstallReferrerSourceTest {

    @Test
    fun `a referrer is an answer`() {
        assertEquals(ReferrerOutcome.ANSWERED, referrerOutcome(InstallReferrerResponse.OK))
    }

    @Test
    fun `a Play Store without the service will never have one`() {
        assertEquals(ReferrerOutcome.NEVER, referrerOutcome(InstallReferrerResponse.FEATURE_NOT_SUPPORTED))
    }

    @Test
    fun `a Play Store that is busy or updating is asked again`() {
        assertEquals(ReferrerOutcome.RETRY, referrerOutcome(InstallReferrerResponse.SERVICE_UNAVAILABLE))
        assertEquals(ReferrerOutcome.RETRY, referrerOutcome(InstallReferrerResponse.DEVELOPER_ERROR))
        assertEquals(ReferrerOutcome.RETRY, referrerOutcome(InstallReferrerResponse.SERVICE_DISCONNECTED))
    }
}
