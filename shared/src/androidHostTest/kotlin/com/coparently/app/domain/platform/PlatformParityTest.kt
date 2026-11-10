package com.coparently.app.domain.platform

import org.junit.Test
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The Android actuals of [Sha256] and [canonicalDecomposition] against the JDK calls they stand
 * for, over 10 000 seeded random inputs each.
 */
class PlatformParityTest {

    @Test
    fun sha256MatchesMessageDigest() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val bytes = random.nextBytes(random.nextInt(0, MAX_LENGTH))
            assertContentEquals(MessageDigest.getInstance("SHA-256").digest(bytes), Sha256.digest(bytes))
        }
    }

    @Test
    fun canonicalDecompositionMatchesNormalizerNfd() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val text = buildString {
                repeat(random.nextInt(0, MAX_LENGTH)) {
                    val range = RANGES[random.nextInt(RANGES.size)]
                    append(String(Character.toChars(random.nextInt(range.first, range.last + 1))))
                }
            }
            assertEquals(Normalizer.normalize(text, Normalizer.Form.NFD), text.canonicalDecomposition())
        }
    }

    private companion object {
        const val SEED = 20261101
        const val RANDOM_CASES = 10_000
        const val MAX_LENGTH = 40
        val RANGES = listOf(0x20..0x7E, 0x00C0..0x017F, 0x0400..0x04FF, 0x1E00..0x1EFF, 0x1F300..0x1FAFF)
    }
}
