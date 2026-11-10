package com.coparently.app.presentation.common

import kotlinx.io.files.Path

/**
 * A checked local copy of a shared file, ready to hand to a viewer app (MON-23).
 *
 * A kotlinx-io [Path] since MON-24 phase 2 (Task 10), as the data layer's copies are (Task 8j): the
 * screen that hands it to Android opens `File(path.toString())`.
 */
data class OpenedFile(val path: Path, val contentType: String)
