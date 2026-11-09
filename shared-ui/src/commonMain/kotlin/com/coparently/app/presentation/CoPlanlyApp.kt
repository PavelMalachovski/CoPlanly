package com.coparently.app.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.presentation.common.FamilyViewViewModel
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.LocalFamilyView
import com.coparently.app.presentation.common.LocalPhotoViewerUid
import com.coparently.app.presentation.common.ParentPaletteViewModel
import com.coparently.app.presentation.common.rememberAppMessages
import com.coparently.app.presentation.navigation.NavGraph
import com.coparently.app.presentation.sync.SyncViewModel
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.LocalParentPalette
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The whole app: the theme, the app-wide locals and the navigation graph, the one composable a
 * shell sets as its content (MON-24 phase 2, Task 12). `MainActivity`'s `setContent` calls it on
 * Android, and the iPhone's view controller will in phase 3.
 *
 * It is composed inside the shell's providers, never outside them: the platform's capabilities
 * (`ProvidePlatform`; on Android `ProvideAndroidPlatform`) and the two sign-in locals,
 * `LocalCredentialSignIn` and `LocalGoogleCalendarSignIn`, which only the shell can build (on the
 * iPhone `ProvideIosShell` provides all three). A screen that reads one outside them throws, as it
 * always has.
 *
 * The ViewModels it reads are the shell's own: in a shell's content the `ViewModelStoreOwner` is
 * the shell itself (the activity on Android), so `koinViewModel()` here answers the instances the
 * activity reads with `by viewModel()` — [SyncViewModel], whose Google sign-in answer the activity
 * hands back, and the splash's `AuthStateViewModel` and `TelemetryConsentViewModel`, which
 * [NavGraph] reads.
 *
 * @param launch What this launch hands the navigation graph.
 * @param darkTheme The parent's theme choice, or null to follow the system's.
 */
@Composable
fun CoPlanlyApp(launch: AppLaunch, darkTheme: Boolean?) {
    val systemDarkTheme = isSystemInDarkTheme()

    // Use saved preference or fall back to system default
    val useDarkTheme = darkTheme ?: systemDarkTheme

    val syncViewModel: SyncViewModel = koinViewModel()
    val parentPaletteViewModel: ParentPaletteViewModel = koinViewModel()
    val familyViewViewModel: FamilyViewViewModel = koinViewModel()
    val signedInUser: SignedInUser = koinInject()

    // The family's chosen parent colours, provided once for every ParentColors call in the app
    // (UX-15). Lifecycle-aware so the ParentsSource upstream can stop while the app is in the
    // background.
    val parentPalette by parentPaletteViewModel.palette.collectAsStateWithLifecycle()
    val familyView by familyViewViewModel.view.collectAsStateWithLifecycle()

    // The signed-in uid, for the record photos only the family's two parents may see (L-4).
    // Follows sign-in and sign-out, so a photo never draws for the wrong account.
    val photoViewerUid by remember(signedInUser) {
        signedInUser.observeCurrentUid()
    }.collectAsStateWithLifecycle(initialValue = signedInUser.getCurrentUid())

    CoPlanlyTheme(darkTheme = useDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            // Snackbars that outlive the screen that raised them (D-25), shown by the navigation
            // graph's root Scaffold.
            val appMessages = rememberAppMessages()

            CompositionLocalProvider(
                LocalParentPalette provides parentPalette,
                LocalFamilyView provides familyView,
                LocalAppMessages provides appMessages,
                LocalPhotoViewerUid provides photoViewerUid
            ) {
                NavGraph(
                    navController = navController,
                    syncViewModel = syncViewModel,
                    pendingInviteCodes = launch.inviteCodes,
                    pendingChatOpen = launch.chatOpen,
                    pendingDestinationOpen = launch.destinationOpen
                )
            }
        }
    }
}
