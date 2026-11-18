package com.coparently.app.ios

import com.coparently.app.presentation.platform.AppBuildValues

/** The build values a test's graph is given where `IosApp.start` would build them from the app's config. */
internal val TEST_BUILD_VALUES: AppBuildValues = AppBuildValues(
    versionName = "",
    privacyPolicyUrl = null,
    termsUrl = null,
    supportEmail = null,
    pairingWebLink = false,
    edupageImport = false,
    appleSignIn = false,
)
