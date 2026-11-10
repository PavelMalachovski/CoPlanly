package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable

/**
 * Voice typing in the chat's composer, on the phone only (CLAUDE.md item 28): [available] is false
 * where the phone has no on-device recognizer, and the composer then draws no microphone.
 *
 * The recognition itself is the domain's `SpeechDictation`, which the chat's ViewModel holds; this
 * is what the screen needs beside it.
 */
interface Dictation : PlatformCapability {

    /**
     * The language to dictate in, as a BCP 47 tag: the parent's own words are in the language they
     * chose for the app, else the phone's first language, never the app's English fallback.
     */
    @Composable
    fun rememberLanguageTag(): String
}
