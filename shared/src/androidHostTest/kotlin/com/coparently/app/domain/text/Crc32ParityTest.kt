package com.coparently.app.domain.text

import org.junit.Test
import java.util.zip.CRC32
import kotlin.random.Random
import kotlin.test.assertEquals

/** [Crc32] against `java.util.zip.CRC32`, which `PaymentQr` calls today, over 10 000 random inputs. */
class Crc32ParityTest {

    @Test
    fun matchesTheJdkOnRandomBytes() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val bytes = random.nextBytes(random.nextInt(0, MAX_LENGTH))
            val expected = CRC32().apply { update(bytes) }.value
            assertEquals(expected, Crc32.of(bytes), "length ${bytes.size}")
        }
    }

    private companion object {
        const val SEED = 20261101
        const val RANDOM_CASES = 10_000
        const val MAX_LENGTH = 512
    }
}
