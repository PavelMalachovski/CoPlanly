package com.coparently.app.presentation.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.coparently.app.domain.chat.ToneNudge
import com.coparently.app.domain.dictation.DictationFailure
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper

/*
 * The chat composables' previews. They stayed in the app when the composables moved to
 * `:shared-ui` (MON-24 phase 2, Task 11, slice 11b), as `DesignSystemPreviews.kt` did: the preview
 * annotations and `PreviewWrapper` are Android Studio's.
 */

@LightDarkPreviews
@Composable
private fun DictationPreview() {
    PreviewWrapper {
        Column {
            Row {
                DictationMicButton(listening = false, onClick = {})
                DictationMicButton(listening = true, onClick = {})
            }
            DictationFailureNotice(DictationFailure.PERMISSION)
        }
    }
}

@LightDarkPreviews
@Composable
private fun ComposerNoticesPreview() {
    PreviewWrapper {
        Column {
            ToneNudgeHint(ToneNudge(shouting = true, raisedPunctuation = true, words = listOf("never")))
            PendingSendNotice(PendingSend("c", "See you at five.", secondsLeft = 3), onUndo = {})
        }
    }
}

@LightDarkPreviews
@Composable
private fun ChatThreadHeaderPreview() {
    PreviewWrapper {
        ChatThreadHeader(title = "Pavel", messages = emptyList(), currentUserId = "u1")
    }
}
