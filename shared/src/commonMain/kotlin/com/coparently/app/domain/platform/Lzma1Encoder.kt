package com.coparently.app.domain.platform

import com.coparently.app.domain.platform.lzma.Bt4
import com.coparently.app.domain.platform.lzma.LzmaEncoderNormal
import com.coparently.app.domain.platform.lzma.MATCH_LEN_MAX
import com.coparently.app.domain.platform.lzma.OPTS
import com.coparently.app.domain.platform.lzma.RangeEncoder

/**
 * Raw LZMA1 in common Kotlin, byte for byte what `org.tukaani:xz` writes with the settings PAY by
 * square uses (CLAUDE.md item 45), so the iPhone draws the code an Android phone draws.
 *
 * It is a port of the encoder of xz-java 1.10 (`org.tukaani:xz`, the version the build resolves),
 * by Lasse Collin and Igor Pavlov, public domain (0BSD since 1.10): `LZMAOutputStream` without a
 * header and with the end marker, `LZMAEncoder`, `LZMAEncoderNormal`, the `BT4` match finder with
 * `Hash234`, `LZEncoder`'s window and `RangeEncoder`, in `domain/platform/lzma/`. Only the paths
 * these fixed settings reach are ported: the normal mode, the BT4 match finder, nice length 64,
 * the library's default depth limit (16 plus half the nice length), lc 3, lp 0, pb 2 and a
 * 128 KiB dictionary; no LZMA2, no fast mode, no preset dictionary. The integer arithmetic is the
 * library's bit for bit, and `Lzma1EncoderParityTest` holds the two to the same bytes.
 */
internal object Lzma1Encoder {

    private const val DICTIONARY = 1 shl 17
    private const val LITERAL_CONTEXT_BITS = 3
    private const val LITERAL_POSITION_BITS = 0
    private const val POSITION_BITS = 2
    private const val NICE_LENGTH = 64
    private const val DEFAULT_DEPTH_LIMIT = 0

    /** [input] compressed as raw LZMA1: no header, with the end marker. */
    fun encode(input: ByteArray): ByteArray {
        val rc = RangeEncoder()
        val lz = Bt4(
            dictSize = DICTIONARY,
            extraSizeBefore = OPTS,
            extraSizeAfter = OPTS,
            niceLen = NICE_LENGTH,
            matchLenMax = MATCH_LEN_MAX,
            depthLimit = DEFAULT_DEPTH_LIMIT
        )
        val lzma = LzmaEncoderNormal(
            rc = rc,
            lz = lz,
            lc = LITERAL_CONTEXT_BITS,
            lp = LITERAL_POSITION_BITS,
            pb = POSITION_BITS,
            dictSize = DICTIONARY
        )
        var off = 0
        var len = input.size
        while (len > 0) {
            val used = lz.fillWindow(input, off, len)
            off += used
            len -= used
            lzma.encodeForLzma1()
        }
        lz.setFinishing()
        lzma.encodeForLzma1()
        lzma.encodeEndMarker()
        return rc.finish()
    }
}
