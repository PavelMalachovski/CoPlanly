package com.coparently.app.presentation.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp

/**
 * An invitation code, drawn the same wherever one is shown (docs/AUDIT-2026-10-design.md D-15):
 * pairing, a guest, a calendar friend and a professional each had their own size and tracking.
 *
 * Codes are read out over the phone, so what matters is that each character stands apart: the
 * alphabet already leaves out O, 0, I, 1 and L (`InviteCodeGenerator`), and this sets the digits
 * tabular and spaces every character by the tracking the code-entry field uses, so the code a
 * parent reads and the one the other parent types look alike. TalkBack reads it character by
 * character — as a word, "K7P2QX" is not something a listener can copy down. It stays on one
 * line at every font size, stepping its size down rather than wrapping.
 *
 * @param code The code, as `InviteCodeGenerator` produced it
 * @param modifier Modifier for the text
 * @param color The code's colour; the surrounding content colour when unspecified
 */
@Composable
fun InviteCodeText(code: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val style = MaterialTheme.typography.displaySmall
    // One line, always: a code split over two lines reads as two codes. Where the display size
    // does not fit (a large font), the code steps its size down instead of wrapping.
    BasicText(
        text = code,
        modifier = modifier.semantics { contentDescription = code.toList().joinToString(" ") },
        style = style.copy(
            color = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } },
            fontFeatureSettings = "tnum",
            letterSpacing = CODE_TRACKING
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_CODE_SIZE, maxFontSize = style.fontSize)
    )
}

/** Space between a code's characters; the code-entry field (`CodeEntryField`) uses the same. */
private val CODE_TRACKING = 6.sp

/** The smallest the code steps down to on a narrow line: still a headline, still readable aloud. */
private val MIN_CODE_SIZE = 20.sp
