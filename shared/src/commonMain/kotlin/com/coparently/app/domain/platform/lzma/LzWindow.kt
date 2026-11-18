package com.coparently.app.domain.platform.lzma

private const val RESERVE_EXTRA = 256 shl 10
private const val RESERVE_MAX = 512 shl 20
private const val MOVE_ALIGNMENT_MASK = 15.inv()

/** Matches found at one position: [count] pairs of [len] and [dist], the longest last. */
internal class Matches(countMax: Int) {
    /** Lengths, increasing. */
    val len = IntArray(countMax)

    /** Distances minus one, as LZMA codes them. */
    val dist = IntArray(countMax)

    /** How many of [len] and [dist] are filled. */
    var count = 0
}

/**
 * How many bytes from `buf[off + len]` on equal those [delta] bytes back, up to [lenLimit] counted
 * from [off], as xz-java's basic `MatchLength.getLen`.
 */
internal fun matchLength(buf: ByteArray, off: Int, delta: Int, len: Int, lenLimit: Int): Int {
    val limit = lenLimit + off
    var i = off + len
    while (i < limit && buf[i] == buf[i - delta]) ++i
    return i - off
}

/**
 * The sliding window of xz-java's `LZEncoder` (org.tukaani:xz 1.10): the input buffered with the
 * dictionary behind the read position and room for look-ahead in front of it, and the read
 * position's bookkeeping. A match finder ([Bt4]) adds the search.
 */
internal abstract class LzWindow(
    dictSize: Int,
    extraSizeBefore: Int,
    extraSizeAfter: Int,
    /** The nice length: a match this long ends the search. */
    val niceLen: Int,
    /** The longest match reported. */
    val matchLenMax: Int
) {
    private val keepSizeBefore = extraSizeBefore + dictSize
    private val keepSizeAfter = extraSizeAfter + matchLenMax
    private val bufSize = keepSizeBefore + keepSizeAfter + minOf(dictSize / 2 + RESERVE_EXTRA, RESERVE_MAX)

    /** The window's bytes. */
    protected val buf = ByteArray(bufSize)

    /** The position being coded, -1 before the first. */
    var pos = -1
        protected set

    private var readLimit = -1
    private var finishing = false
    private var writePos = 0
    private var pendingSize = 0

    /** Matches at the next position, which becomes the current one. */
    abstract fun getMatches(): Matches

    /** Moves [len] positions on, still adding them to the search structure. */
    abstract fun skip(len: Int)

    /** Copies up to [len] bytes of [input] from [off] into the window and answers how many. */
    fun fillWindow(input: ByteArray, off: Int, len: Int): Int {
        if (pos >= bufSize - keepSizeAfter) moveWindow()
        val used = minOf(len, bufSize - writePos)
        input.copyInto(buf, writePos, off, off + used)
        writePos += used
        if (writePos >= keepSizeAfter) readLimit = writePos - keepSizeAfter
        processPendingBytes()
        return used
    }

    /** No more input: the last bytes may be coded. */
    fun setFinishing() {
        readLimit = writePos - 1
        finishing = true
        processPendingBytes()
    }

    /** Whether coding has begun. */
    val isStarted: Boolean get() = pos != -1

    /** Whether there is a byte to code past [alreadyReadLen] read ahead. */
    fun hasEnoughData(alreadyReadLen: Int): Boolean = pos - alreadyReadLen < readLimit

    /** Bytes available from the current position on. */
    val avail: Int get() = writePos - pos

    /** The byte [backward] positions behind the current one. */
    fun getByte(backward: Int): Int = buf[pos - backward].toInt() and BYTE_MASK

    /** The byte [forward] ahead of and then [backward] behind the current one. */
    fun getByte(forward: Int, backward: Int): Int = buf[pos + forward - backward].toInt() and BYTE_MASK

    /** The length of the match at distance [dist] (minus one), up to [lenLimit]. */
    fun getMatchLen(dist: Int, lenLimit: Int): Int = matchLength(buf, pos, dist + 1, 0, lenLimit)

    /** The length of the match [forward] bytes ahead at distance [dist] (minus one), up to [lenLimit]. */
    fun getMatchLen(forward: Int, dist: Int, lenLimit: Int): Int =
        matchLength(buf, pos + forward, dist + 1, 0, lenLimit)

    /**
     * Moves to the next position and answers the bytes available there, or 0 when fewer than
     * [requiredForFlushing] remain and the input is not finished, or fewer than
     * [requiredForFinishing] remain at all: such a position is left pending.
     */
    protected fun movePos(requiredForFlushing: Int, requiredForFinishing: Int): Int {
        ++pos
        var available = writePos - pos
        if (available < requiredForFlushing && (available < requiredForFinishing || !finishing)) {
            ++pendingSize
            available = 0
        }
        return available
    }

    private fun moveWindow() {
        val moveOffset = (pos + 1 - keepSizeBefore) and MOVE_ALIGNMENT_MASK
        val moveSize = writePos - moveOffset
        buf.copyInto(buf, 0, moveOffset, moveOffset + moveSize)
        pos -= moveOffset
        readLimit -= moveOffset
        writePos -= moveOffset
    }

    private fun processPendingBytes() {
        if (pendingSize > 0 && pos < readLimit) {
            pos -= pendingSize
            val oldPendingSize = pendingSize
            pendingSize = 0
            skip(oldPendingSize)
        }
    }

    internal companion object {
        /** Lowers every position in [positions] by [offset], to zero at least. */
        fun normalize(positions: IntArray, count: Int, offset: Int) {
            for (i in 0 until count) {
                positions[i] = if (positions[i] <= offset) 0 else positions[i] - offset
            }
        }
    }
}
