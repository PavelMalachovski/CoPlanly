package com.coparently.app.di.koin

import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.ai.AiAssistResult
import com.coparently.app.domain.dictation.DictationFailure
import com.coparently.app.domain.dictation.DictationListener
import com.coparently.app.domain.dictation.SpeechDictation
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.repository.RecordPhotoStorage
import kotlinx.datetime.YearMonth
import kotlinx.io.IOException

/*
 * What the ViewModels in `uiModules` ask of the platform that the iPhone does not have yet (MON-24
 * phase 2, Task 10), beside `:shared-data`'s `IosUnavailable.kt`: each answers as a phone with
 * nothing of the kind would, so `platformUiModule` resolves every ViewModel's definition without
 * promising a feature (design item 8). Each is replaced as the iPhone app gains it (phase 3 and 4).
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
 * No Cloud Storage for a record's photos yet (phase 3), as `:shared-data`'s `NoStorageBucket` has
 * none for a shared file: an upload fails, so nothing is stored that names a photo, and a delete
 * fails too, so a caller that keeps a list keeps the reference rather than dropping one to an object
 * it could not reach (`RecordPhotoStorage.delete`'s contract). The event form's photo is the first
 * caller (`EventViewModel`, slice 10b).
 */
internal object NoRecordPhotoStorage : RecordPhotoStorage {
    override suspend fun upload(
        kind: RecordPhotoKind,
        recordId: String,
        recordFamilyId: String?,
        localUri: String
    ): String = throw IOException(NO_PHOTO_STORAGE)

    override suspend fun delete(reference: String, recordFamilyId: String?): Unit =
        throw IOException(NO_PHOTO_STORAGE)
}

private const val NO_PHOTO_STORAGE = "Not available in this iPhone build yet"
