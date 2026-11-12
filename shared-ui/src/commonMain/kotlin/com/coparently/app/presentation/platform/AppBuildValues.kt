package com.coparently.app.presentation.platform

/**
 * What a screen reads from the build rather than from the data: on Android `BuildConfig`.
 *
 * A link that is null renders nothing, because a row that opens a dead page is design item 8's
 * defect: each address stays blank in the build until its page is hosted.
 *
 * @property versionName The version Settings shows
 * @property privacyPolicyUrl The hosted privacy policy, or null while none is hosted (REL-4)
 * @property termsUrl The hosted terms of service, or null while none is hosted
 * @property supportEmail The chat report's address, or null, which hides the action (F-10)
 * @property pairingWebLink Whether invitations carry the web link rather than the app's own scheme
 *   (CLAUDE.md item 37)
 * @property edupageImport Whether the school import offers EduPage beside Bakaláři: on in debug builds,
 *   off in release unless the build says otherwise (`-PCOPLANLY_EDUPAGE_IMPORT=true`)
 */
data class AppBuildValues(
    val versionName: String,
    val privacyPolicyUrl: String?,
    val termsUrl: String?,
    val supportEmail: String?,
    val pairingWebLink: Boolean,
    val edupageImport: Boolean,
)
