package com.coparently.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.headlineSmallEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.brand_wordmark
import com.coparently.app.resources.calendar_settings
import com.coparently.app.resources.children_avatars_description
import com.coparently.app.resources.children_avatars_more
import org.jetbrains.compose.resources.stringResource

/**
 * The top row of the start screen: the "coplanly." wordmark on the left, the children's
 * avatars overlapping and then the settings gear on the right.
 *
 * Draws no avatars for a family without children, and at most [AvatarStack.MAX_VISIBLE] of
 * them otherwise, followed by a "+n" disc for the rest.
 *
 * @param children The family's children, in the order they are shown
 * @param onSettingsClick Opens Settings
 * @param modifier Modifier for the row
 */
@Composable
fun BrandHeader(
    children: List<ChildInfo>,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Wordmark()
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChildAvatarStack(children)
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(Res.string.calendar_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Wordmark() {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = ink)) { append(stringResource(Res.string.brand_wordmark)) }
            withStyle(SpanStyle(color = accent)) { append(".") }
        },
        style = MaterialTheme.typography.headlineSmallEmphasized
    )
}

@Composable
private fun ChildAvatarStack(children: List<ChildInfo>) {
    val shown = AvatarStack.visible(children.size)
    if (shown == 0) return
    val overflow = AvatarStack.overflow(children.size)
    val description = stringResource(
        Res.string.children_avatars_description,
        children.joinToString(", ") { it.childName }
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(-OVERLAP),
        modifier = Modifier
            .padding(end = Spacing.S)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        for (index in 0 until shown) {
            Ringed { ChildAvatar(children[index], AVATAR_SIZE) }
        }
        if (overflow > 0) {
            Ringed {
                Box(
                    modifier = Modifier
                        .size(AVATAR_SIZE)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.children_avatars_more, overflow),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/** Draws [content] in a [RING_WIDTH] ring of `surface`. */
@Composable
private fun Ringed(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(RING_WIDTH)
    ) { content() }
}

/** Which avatars of a family's children the header shows. */
internal object AvatarStack {
    /** At most this many avatars are drawn; the rest become a "+n" disc. */
    const val MAX_VISIBLE = 3

    /** How many of [count] children get an avatar of their own. */
    fun visible(count: Int): Int = count.coerceIn(0, MAX_VISIBLE)

    /** How many of [count] children the "+n" disc stands for. */
    fun overflow(count: Int): Int = (count - MAX_VISIBLE).coerceAtLeast(0)
}

private val AVATAR_SIZE = 38.dp
private val OVERLAP = 12.dp
private val RING_WIDTH = 2.dp
