package com.coparently.app.presentation.common

import java.io.File

/** A checked local copy of a shared file, ready to hand to a viewer app (MON-23). */
data class OpenedFile(val file: File, val contentType: String)
