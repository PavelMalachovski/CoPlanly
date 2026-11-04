package com.coparently.app.domain.platform

import java.text.Normalizer

/** NFD through `java.text.Normalizer`, the call `TextFold` and the school import make today. */
actual fun String.canonicalDecomposition(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
