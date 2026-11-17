package com.coparently.app.pushwording

/** `%<n>$s` and `%<n>$d`, the only arguments Compose Multiplatform 1.9.3's resources replace. */
private val ARGUMENT = Regex("""%(\d+)\$[ds]""")

/**
 * [template] with each `%<n>$s` and `%<n>$d` replaced by the `toString()` of argument `n` (counted
 * from 1), exactly as Compose Multiplatform 1.9.3 formats a string resource, and nothing else: no
 * `%%`, no width, no locale. Check 7 of `tools/check-invariants.js` holds every resource text to
 * what this and Android's `String.format` read alike, so a text formatted here reads as Android's.
 *
 * An argument the template names but [args] does not hold throws, as Compose Multiplatform's does.
 */
fun formatArgs(template: String, args: Array<out Any>): String {
    val texts = args.map { it.toString() }
    return ARGUMENT.replace(template) { match -> texts[match.groupValues[1].toInt() - 1] }
}
