package com.coparently.app.domain.platform

/**
 * This text in Unicode canonical decomposition (NFD): `Normalizer.Form.NFD` on Android,
 * `decomposedStringWithCanonicalMapping` on iOS.
 *
 * Chat search and the school import fold accents by decomposing and then dropping the combining
 * marks ("č" becomes "c" and a caron), so both platforms have to split a character into the same
 * pieces, or a search would find a message on one parent's phone and not on the other's.
 */
expect fun String.canonicalDecomposition(): String
