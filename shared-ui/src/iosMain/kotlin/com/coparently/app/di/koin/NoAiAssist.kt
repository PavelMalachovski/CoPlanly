package com.coparently.app.di.koin

import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.ai.AiAssistResult
import kotlinx.datetime.YearMonth

/*
 * What the ViewModels in `uiModules` ask of the platform that the iPhone does not have yet (MON-24
 * phase 2, Task 10), beside `:shared-data`'s `NoRequestClockJobs.kt`: it answers as a phone with
 * nothing of the kind would, so `platformUiModule` resolves every ViewModel's definition without
 * promising a feature (design item 8). The file was `IosUiUnavailable.kt` while it held several
 * stand-ins; each left as the iPhone app gained its feature: `NoAuthErrors` in phase 3's Task 5, for
 * `IosAuthErrors`, `NoInviteQrCodes` and `NoSpeechDictation` in phase 4 part E1, for the invitation's
 * QR and `IosSpeechDictation`. The data layer's wordings (`IosPairingWording`,
 * `IosSchoolImportWordings`) are bound beside it in `platformUiModule`, because they read this
 * module's strings.
 */

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
