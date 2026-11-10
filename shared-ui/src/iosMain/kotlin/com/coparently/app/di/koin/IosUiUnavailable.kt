package com.coparently.app.di.koin

import androidx.compose.ui.graphics.ImageBitmap
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.ai.AiAssistResult
import com.coparently.app.domain.dictation.DictationFailure
import com.coparently.app.domain.dictation.DictationListener
import com.coparently.app.domain.dictation.SpeechDictation
import com.coparently.app.presentation.pairing.InviteQrCodes
import kotlinx.datetime.YearMonth

/*
 * What the ViewModels in `uiModules` ask of the platform that the iPhone does not have yet (MON-24
 * phase 2, Task 10), beside `:shared-data`'s `IosUnavailable.kt`: each answers as a phone with
 * nothing of the kind would, so `platformUiModule` resolves every ViewModel's definition without
 * promising a feature (design item 8). Each is replaced as the iPhone app gains it (phase 3 and 4);
 * `NoAuthErrors` left in phase 3's Task 5, for `IosAuthErrors`.
 */

/**
 * No on-device dictation yet (phase 4): [isAvailable] is false, so the composer draws no microphone
 * (CLAUDE.md item 28), and a start that came anyway is told so rather than left waiting.
 */
internal object NoSpeechDictation : SpeechDictation {
    override fun isAvailable(): Boolean = false

    override fun start(languageTag: String?, listener: DictationListener) {
        listener.onFailure(DictationFailure.UNAVAILABLE)
    }

    override fun stop() = Unit

    override fun cancel() = Unit
}

/**
 * No AI assist: the iPhone build offers none (`AiAssistAvailability(enabled = false)` beside this in
 * [platformUiModule]), so nothing calls this; if anything did, it is told the assist is off, the
 * answer the server gives a build without it (CLAUDE.md item 36), and nothing leaves the phone.
 */
internal object NoAiAssist : AiAssistRepository {
    override suspend fun suggestReply(
        conversationId: String,
        locale: String,
        draftHint: String?
    ): AiAssistResult = AiAssistResult.Disabled

    override suspend fun summarizeMonth(
        month: YearMonth,
        locale: String,
        stats: Map<String, Any>
    ): AiAssistResult = AiAssistResult.Disabled
}

/**
 * No QR code drawn yet (slice 10e: the pairing ViewModel asks for one): the iPhone has no QR encoder
 * yet, so an invitation is handed over by its code and link alone, and the pairing screen draws no
 * QR frame while this is bound ([InviteQrCodes.available], design item 8).
 */
internal val NoInviteQrCodes: InviteQrCodes = object : InviteQrCodes {
    override val available: Boolean = false

    override fun forInvite(code: String): ImageBitmap? = null
}
