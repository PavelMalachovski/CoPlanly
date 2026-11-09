package com.coparently.app.presentation

import com.coparently.app.presentation.navigation.NavGraph
import com.coparently.app.presentation.navigation.PendingChatOpen
import com.coparently.app.presentation.navigation.PendingDestinationOpen
import com.coparently.app.presentation.navigation.PendingInviteCodes

/**
 * What a launch hands the app: the deep links, invitation codes and the tapped push's screen that
 * wait for the navigation graph, each with the callback that clears it once acted on (MON-24 phase
 * 2, Task 12).
 *
 * Plain values, beside the platform's capabilities rather than inside
 * [PlatformServices][com.coparently.app.presentation.platform.PlatformServices]: the screenshot
 * frame and the QR scanner's activity build those too, and launch nothing. One constructor with
 * no defaults, as the platform's, so a shell cannot leave a hand-off out.
 *
 * The shell reads the launch and arms these. On Android `MainActivity.readLaunchIntent` switches to
 * the family a push names **before** it arms any of them (CLAUDE.md, item 15 of "Things that are
 * easy to get wrong"), because [NavGraph] navigates the moment one appears.
 *
 * @property inviteCodes The `coplanly://pair` and `coplanly://guest` codes.
 * @property chatOpen A `coplanly://chat` link.
 * @property destinationOpen The screen a tapped push names.
 */
class AppLaunch(
    val inviteCodes: PendingInviteCodes,
    val chatOpen: PendingChatOpen,
    val destinationOpen: PendingDestinationOpen,
)
