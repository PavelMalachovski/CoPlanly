package com.coparently.app.domain.pairing

import com.coparently.app.domain.platform.secureRandomBytes
import kotlin.random.Random

/**
 * Generates and validates the short codes a parent reads out or types to pair.
 *
 * The alphabet deliberately omits `O`, `0`, `I`, `1` and `L` so a code stays
 * unambiguous when it is dictated over the phone or copied by hand.
 */
object InviteCodeGenerator {

    /** The 31 characters a code may contain. */
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** Number of characters in every code. */
    const val LENGTH = 6

    /**
     * Cryptographically secure default source of randomness.
     *
     * An invite code is a bearer credential: whoever presents it gains lasting read/write
     * access to a family's calendar, chat and finances. `Random.Default` is a linear
     * congruential-style PRNG whose internal state is recoverable from a handful of
     * observed outputs, which would let an attacker who has seen a few codes predict
     * others and undo the entropy the 31^6 alphabet is supposed to provide.
     * The platform's secure generator ([secureRandomBytes]: `SecureRandom` on Android) has no
     * such structure.
     */
    private val secureRandom: Random = SecureBitsRandom

    /** A [Random] whose every bit comes from [secureRandomBytes]. */
    private object SecureBitsRandom : Random() {
        private const val BYTE_MASK = 0xFF

        override fun nextBits(bitCount: Int): Int {
            var bits = 0
            for (byte in secureRandomBytes(Int.SIZE_BYTES)) {
                bits = (bits shl Byte.SIZE_BITS) or (byte.toInt() and BYTE_MASK)
            }
            // The upper [bitCount] bits, as Random.nextBits promises; none for a count of zero.
            return if (bitCount == 0) 0 else bits ushr (Int.SIZE_BITS - bitCount)
        }
    }

    /**
     * Returns a new random code.
     *
     * @param random Source of randomness; defaults to a [secureRandomBytes]-backed source and is
     *   injectable so tests can seed it deterministically.
     */
    fun generate(random: Random = secureRandom): String =
        buildString(LENGTH) {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    /** Whether [code] could have been produced by [generate]. */
    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }
}
