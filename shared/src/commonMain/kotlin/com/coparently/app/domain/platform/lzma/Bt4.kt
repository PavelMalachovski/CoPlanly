package com.coparently.app.domain.platform.lzma

private const val HASHED_BYTES = 4
private const val DEPTH_BASE = 16
private const val LEN_AFTER_HASHES = 3

/**
 * The binary-tree match finder of xz-java's `BT4` (org.tukaani:xz 1.10): two-, three- and
 * four-byte hashes, then a binary tree of the dictionary's positions searched to [depthLimit]
 * nodes (16 plus half the nice length when 0 is given, the library's default).
 */
internal class Bt4(
    dictSize: Int,
    extraSizeBefore: Int,
    extraSizeAfter: Int,
    niceLen: Int,
    matchLenMax: Int,
    depthLimit: Int
) : LzWindow(dictSize, extraSizeBefore, extraSizeAfter, niceLen, matchLenMax) {
    private val cyclicSize = dictSize + 1
    private var cyclicPos = -1
    private var lzPos = cyclicSize
    private val hash = Hash234(dictSize)
    private val tree = IntArray(cyclicSize * 2)
    private val matches = Matches(niceLen - 1)
    private val depthLimit = if (depthLimit > 0) depthLimit else DEPTH_BASE + niceLen / 2

    private fun movePos(): Int {
        val available = movePos(niceLen, HASHED_BYTES)
        if (available != 0) {
            if (++lzPos == Int.MAX_VALUE) {
                val offset = Int.MAX_VALUE - cyclicSize
                hash.normalize(offset)
                normalize(tree, cyclicSize * 2, offset)
                lzPos -= offset
            }
            if (++cyclicPos == cyclicSize) cyclicPos = 0
        }
        return available
    }

    override fun getMatches(): Matches {
        matches.count = 0
        var matchLenLimit = matchLenMax
        var niceLenLimit = niceLen
        val available = movePos()
        if (available < matchLenLimit) {
            if (available == 0) return matches
            matchLenLimit = available
            if (niceLenLimit > available) niceLenLimit = available
        }
        hash.calcHashes(buf, pos)
        val delta2 = lzPos - hash.hash2Pos
        val delta3 = lzPos - hash.hash3Pos
        val currentMatch = hash.hash4Pos
        hash.updateTables(lzPos)
        val lenBest = hashMatches(delta2, delta3, matchLenLimit)
        if (lenBest >= niceLenLimit) {
            skipTree(niceLenLimit, currentMatch)
        } else {
            searchTree(maxOf(lenBest, LEN_AFTER_HASHES), currentMatch, matchLenLimit, niceLenLimit)
        }
        return matches
    }

    /** The matches the two- and three-byte hashes give, and the length of the longest. */
    private fun hashMatches(delta2In: Int, delta3: Int, matchLenLimit: Int): Int {
        var delta2 = delta2In
        var lenBest = 0
        if (delta2 < cyclicSize && buf[pos - delta2] == buf[pos]) {
            lenBest = 2
            matches.len[0] = 2
            matches.dist[0] = delta2 - 1
            matches.count = 1
        }
        if (delta2 != delta3 && delta3 < cyclicSize && buf[pos - delta3] == buf[pos]) {
            lenBest = LEN_AFTER_HASHES
            matches.dist[matches.count++] = delta3 - 1
            delta2 = delta3
        }
        if (matches.count > 0) {
            lenBest = matchLength(buf, pos, delta2, lenBest, matchLenLimit)
            matches.len[matches.count - 1] = lenBest
        }
        return lenBest
    }

    /** Walks the tree from [start], recording longer matches and inserting the current position. */
    private fun searchTree(lenBestIn: Int, start: Int, matchLenLimit: Int, niceLenLimit: Int) {
        var lenBest = lenBestIn
        val walk = TreeWalk(start)
        while (walk.next()) {
            if (walk.bytesEqual() && walk.extend(matchLenLimit) > lenBest) {
                lenBest = walk.len
                matches.len[matches.count] = walk.len
                matches.dist[matches.count] = walk.delta - 1
                ++matches.count
                if (walk.len >= niceLenLimit) {
                    walk.adoptChildren()
                    return
                }
            }
            walk.descend()
        }
    }

    /** Inserts the current position into the tree without recording matches. */
    private fun skipTree(niceLenLimit: Int, start: Int) {
        val walk = TreeWalk(start)
        while (walk.next()) {
            if (walk.bytesEqual() && walk.extend(niceLenLimit) == niceLenLimit) {
                walk.adoptChildren()
                return
            }
            walk.descend()
        }
    }

    override fun skip(len: Int) {
        repeat(len) {
            var niceLenLimit = niceLen
            val available = movePos()
            if (available != 0) {
                if (available < niceLenLimit) niceLenLimit = available
                hash.calcHashes(buf, pos)
                val currentMatch = hash.hash4Pos
                hash.updateTables(lzPos)
                skipTree(niceLenLimit, currentMatch)
            }
        }
    }

    /** One walk down the tree from the current position, the loop of `BT4`'s two searches. */
    private inner class TreeWalk(private var currentMatch: Int) {
        private var depth = depthLimit
        private var ptr0 = (cyclicPos shl 1) + 1
        private var ptr1 = cyclicPos shl 1
        private var len0 = 0
        private var len1 = 0
        private var pair = 0

        /** The distance to the node being compared. */
        var delta = 0
            private set

        /** The bytes known to match at that node. */
        var len = 0
            private set

        /** Moves to the next node, or ends the walk (closing both branches) and answers false. */
        fun next(): Boolean {
            delta = lzPos - currentMatch
            if (depth-- == 0 || delta >= cyclicSize) {
                tree[ptr0] = 0
                tree[ptr1] = 0
                return false
            }
            pair = (cyclicPos - delta + (if (delta > cyclicPos) cyclicSize else 0)) shl 1
            len = minOf(len0, len1)
            return true
        }

        /** Whether the node's byte after the known match equals the current one's. */
        fun bytesEqual(): Boolean = buf[pos + len - delta] == buf[pos + len]

        /** Measures the whole match at the node, up to [lenLimit], and answers its length. */
        fun extend(lenLimit: Int): Int {
            len = matchLength(buf, pos, delta, len + 1, lenLimit)
            return len
        }

        /** Ends the walk at a node as long as the nice length, taking over its children. */
        fun adoptChildren() {
            tree[ptr1] = tree[pair]
            tree[ptr0] = tree[pair + 1]
        }

        /** Goes to the smaller or the larger child, by the first differing byte. */
        fun descend() {
            if ((buf[pos + len - delta].toInt() and BYTE_MASK) < (buf[pos + len].toInt() and BYTE_MASK)) {
                tree[ptr1] = currentMatch
                ptr1 = pair + 1
                currentMatch = tree[ptr1]
                len1 = len
            } else {
                tree[ptr0] = currentMatch
                ptr0 = pair
                currentMatch = tree[ptr0]
                len0 = len
            }
        }
    }
}
