package com.coparently.app.domain.platform.lzma

private const val CRC32_POLY = 0xEDB88320.toInt()
private const val HASH_2_SIZE = 1 shl 10
private const val HASH_2_MASK = HASH_2_SIZE - 1
private const val HASH_3_SIZE = 1 shl 16
private const val HASH_3_MASK = HASH_3_SIZE - 1
private const val HASH_4_MIN_MASK = 0xFFFF
private const val HASH_4_MAX = 1 shl 24
private const val HASH_4_SHIFT = 5
private const val THIRD_BYTE = 2
private const val FOURTH_BYTE = 3
private const val TABLE_SIZE = 256
private const val SMEAR_4 = 4
private const val SMEAR_8 = 8

/** The CRC-32 table xz-java's `CRC32Hash` hashes with. */
private val crcTable = IntArray(TABLE_SIZE) { index ->
    var r = index
    repeat(BYTE_BITS) { r = if ((r and 1) != 0) (r ushr 1) xor CRC32_POLY else r ushr 1 }
    r
}

/** The size of the four-byte hash table for [dictSize], as xz-java's `Hash234.getHash4Size`. */
private fun hash4Size(dictSize: Int): Int {
    var h = dictSize - 1
    h = h or (h ushr 1)
    h = h or (h ushr 2)
    h = h or (h ushr SMEAR_4)
    h = h or (h ushr SMEAR_8)
    h = h ushr 1
    h = h or HASH_4_MIN_MASK
    if (h > HASH_4_MAX) h = h ushr 1
    return h + 1
}

/** The two-, three- and four-byte hash tables of xz-java's `Hash234`: the last position of each hash. */
internal class Hash234(dictSize: Int) {
    private val hash4Size = hash4Size(dictSize)
    private val hash4Mask = hash4Size - 1
    private val hash2Table = IntArray(HASH_2_SIZE)
    private val hash3Table = IntArray(HASH_3_SIZE)
    private val hash4Table = IntArray(hash4Size)
    private var hash2Value = 0
    private var hash3Value = 0
    private var hash4Value = 0

    /** Hashes the four bytes of [buf] from [off]. */
    fun calcHashes(buf: ByteArray, off: Int) {
        var temp = crcTable[buf[off].toInt() and BYTE_MASK] xor (buf[off + 1].toInt() and BYTE_MASK)
        hash2Value = temp and HASH_2_MASK
        temp = temp xor ((buf[off + THIRD_BYTE].toInt() and BYTE_MASK) shl BYTE_BITS)
        hash3Value = temp and HASH_3_MASK
        temp = temp xor (crcTable[buf[off + FOURTH_BYTE].toInt() and BYTE_MASK] shl HASH_4_SHIFT)
        hash4Value = temp and hash4Mask
    }

    /** The last position with the same two-byte hash. */
    val hash2Pos: Int get() = hash2Table[hash2Value]

    /** The last position with the same three-byte hash. */
    val hash3Pos: Int get() = hash3Table[hash3Value]

    /** The last position with the same four-byte hash. */
    val hash4Pos: Int get() = hash4Table[hash4Value]

    /** Records [pos] under the current hashes. */
    fun updateTables(pos: Int) {
        hash2Table[hash2Value] = pos
        hash3Table[hash3Value] = pos
        hash4Table[hash4Value] = pos
    }

    /** Lowers every recorded position by [offset]. */
    fun normalize(offset: Int) {
        LzWindow.normalize(hash2Table, HASH_2_SIZE, offset)
        LzWindow.normalize(hash3Table, HASH_3_SIZE, offset)
        LzWindow.normalize(hash4Table, hash4Size, offset)
    }
}
