package com.coparently.app.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.common.nativeControls
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.auth_sign_in_with_apple
import org.jetbrains.compose.resources.stringResource

/**
 * "Sign in with Apple" on the sign-in screen (MON-24 phase 4, part A, boards 1 and 4).
 *
 * On the iPhone it is the system's own button (`NativeControls.AppleSignInButton`,
 * `ASAuthorizationAppleIDButton`), which Apple words and draws itself. Elsewhere (Android, and every
 * test that provides no native controls) it is drawn as Apple's guidelines ask of a custom button:
 * black, the white Apple logo before the words "Sign in with Apple", at the height [modifier] gives
 * it (the Google button's), and in the shape the screen's other buttons take.
 *
 * @param onClick Starts Apple's sign-in
 * @param enabled Whether it can be tapped now (not while another attempt is in flight)
 * @param background The colour of what the button sits on: the native button's host is painted
 *   with it, and its style chosen by it
 * @param modifier Sets the button's size
 */
@Composable
fun AppleSignInButton(onClick: () -> Unit, enabled: Boolean, background: Color, modifier: Modifier = Modifier) {
    val native = nativeControls()
    if (native != null) {
        native.AppleSignInButton(onClick = onClick, enabled = enabled, background = background, modifier = modifier)
        return
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White,
            disabledContainerColor = Color.Black.copy(alpha = DISABLED_ALPHA),
            disabledContentColor = Color.White.copy(alpha = DISABLED_ALPHA)
        )
    ) {
        Image(
            imageVector = AppleLogo,
            contentDescription = null,
            modifier = Modifier.size(IconSizes.Small)
        )
        Spacer(Modifier.width(Spacing.S))
        Text(
            text = stringResource(Res.string.auth_sign_in_with_apple),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** How strongly a disabled button keeps its colours, as Material dims its own. */
private const val DISABLED_ALPHA = 0.38f

/**
 * Apple's logo, in white, for the button Android draws. The outline is the one the Simple Icons
 * project publishes (CC0) on a 24-unit grid; the mark itself is Apple's, used as Apple's guidelines
 * allow on a "Sign in with Apple" button. Drawn through [Image], not `AppIcon`: it is a brand mark,
 * not an icon of the app's set, and has no SF Symbol to stand for it.
 */
internal val AppleLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "AppleLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(APPLE_LOGO_PATH),
        fill = SolidColor(Color.White)
    ).build()
}

/** The logo's outline, in SVG path syntax on a 24 × 24 viewport. */
private const val APPLE_LOGO_PATH =
    "M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 " +
        "9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 " +
        "2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-" +
        ".013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-" +
        "2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-" +
        "3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701"
