package com.coparently.app.domain.platform.lzma

/** Positions an optimal parse looks ahead, and the window's room on each side for it. */
internal const val OPTS = 4096

/**
 * xz-java's `LZMAEncoderNormal` (org.tukaani:xz 1.10): chooses symbols by an optimal parse over up
 * to [OPTS] positions, pricing literals, short and long repeated matches, new matches, and a match
 * or literal followed by a repeated match.
 */
internal class LzmaEncoderNormal(
    rc: RangeEncoder,
    lz: LzWindow,
    lc: Int,
    lp: Int,
    pb: Int,
    dictSize: Int
) : LzmaEncoder(rc, lz, lc, lp, pb, dictSize) {
    private val opts = Array(OPTS) { Optimum() }
    private var optCur = 0
    private var optEnd = 0
    private var matches = Matches(0)
    private val repLens = IntArray(REPS)
    private val nextState = LzmaState()
    private var mainLen = 0

    override fun nextSymbol(): Int = if (optCur < optEnd) nextPlannedSymbol() else planSymbols()

    private fun nextPlannedSymbol(): Int {
        val len = opts[optCur].optPrev - optCur
        optCur = opts[optCur].optPrev
        back = opts[optCur].backPrev
        return len
    }

    private fun planSymbols(): Int {
        optCur = 0
        optEnd = 0
        back = -1
        if (readAhead == -1) matches = getMatches()
        val avail = minOf(lz.avail, MATCH_LEN_MAX)
        if (avail < MATCH_LEN_MIN) return 1
        val repBest = measureReps(avail)
        return takeLongMatch(repBest) ?: planFromHere(repBest)
    }

    /** Fills [repLens] (0 for shorter than two) and answers the repeated distance with the longest. */
    private fun measureReps(avail: Int): Int {
        var repBest = 0
        for (rep in 0 until REPS) {
            repLens[rep] = lz.getMatchLen(reps[rep], avail)
            if (repLens[rep] < MATCH_LEN_MIN) {
                repLens[rep] = 0
            } else if (repLens[rep] > repLens[repBest]) {
                repBest = rep
            }
        }
        return repBest
    }

    /** A match as long as the nice length, taken without a parse, or null. Sets [mainLen]. */
    private fun takeLongMatch(repBest: Int): Int? {
        if (repLens[repBest] >= niceLen) {
            back = repBest
            skip(repLens[repBest] - 1)
            return repLens[repBest]
        }
        mainLen = 0
        if (matches.count > 0) {
            mainLen = matches.len[matches.count - 1]
            if (mainLen >= niceLen) {
                back = matches.dist[matches.count - 1] + REPS
                skip(mainLen - 1)
                return mainLen
            }
        }
        return null
    }

    private fun planFromHere(repBest: Int): Int {
        val curByte = lz.getByte(0)
        val matchByte = lz.getByte(reps[0] + 1)
        if (mainLen < MATCH_LEN_MIN && curByte != matchByte && repLens[repBest] < MATCH_LEN_MIN) return 1
        val pos = lz.pos
        val posState = pos and posMask
        opts[1].set1(literalEncoder.getPrice(curByte, matchByte, lz.getByte(1), pos, state), 0, -1)
        val anyMatchPrice = anyMatchPrice(state, posState)
        val anyRepPrice = anyRepPrice(anyMatchPrice, state)
        if (matchByte == curByte) {
            val shortRepPrice = shortRepPrice(anyRepPrice, state, posState)
            if (shortRepPrice < opts[1].price) opts[1].set1(shortRepPrice, 0, 0)
        }
        optEnd = maxOf(mainLen, repLens[repBest])
        if (optEnd < MATCH_LEN_MIN) {
            back = opts[1].backPrev
            return 1
        }
        updatePrices()
        opts[0].state.set(state)
        reps.copyInto(opts[0].reps)
        for (i in optEnd downTo MATCH_LEN_MIN) opts[i].reset()
        priceFirstReps(anyRepPrice, posState)
        priceFirstMatches(anyMatchPrice, posState)
        walkForward(pos)
        return convertOpts()
    }

    private fun priceFirstReps(anyRepPrice: Int, posState: Int) {
        for (rep in 0 until REPS) {
            var repLen = repLens[rep]
            if (repLen < MATCH_LEN_MIN) continue
            val longRepPrice = longRepPrice(anyRepPrice, rep, state, posState)
            do {
                val price = longRepPrice + repLenEncoder.getPrice(repLen, posState)
                if (price < opts[repLen].price) opts[repLen].set1(price, 0, rep)
            } while (--repLen >= MATCH_LEN_MIN)
        }
    }

    private fun priceFirstMatches(anyMatchPrice: Int, posState: Int) {
        var len = maxOf(repLens[0] + 1, MATCH_LEN_MIN)
        if (len > mainLen) return
        val normalMatchPrice = normalMatchPrice(anyMatchPrice, state)
        var i = 0
        while (len > matches.len[i]) ++i
        while (true) {
            val dist = matches.dist[i]
            val price = matchAndLenPrice(normalMatchPrice, dist, len, posState)
            if (price < opts[len].price) opts[len].set1(price, 0, dist + REPS)
            if (len == matches.len[i] && ++i == matches.count) break
            ++len
        }
    }

    private fun walkForward(startPos: Int) {
        var pos = startPos
        var avail = minOf(lz.avail, OPTS - 1)
        while (++optCur < optEnd) {
            matches = getMatches()
            if (matches.count > 0 && matches.len[matches.count - 1] >= niceLen) break
            --avail
            ++pos
            val posState = pos and posMask
            updateOptStateAndReps()
            val opt = opts[optCur]
            val anyMatchPrice = opt.price + anyMatchPrice(opt.state, posState)
            val anyRepPrice = anyRepPrice(anyMatchPrice, opt.state)
            calc1BytePrices(pos, posState, avail, anyRepPrice)
            if (avail >= MATCH_LEN_MIN) {
                val startLen = calcLongRepPrices(pos, posState, avail, anyRepPrice)
                if (matches.count > 0) calcNormalMatchPrices(pos, posState, avail, anyMatchPrice, startLen)
            }
        }
    }

    private fun convertOpts(): Int {
        optEnd = optCur
        var optPrev = opts[optCur].optPrev
        do {
            val opt = opts[optCur]
            if (opt.prev1IsLiteral) {
                opts[optPrev].optPrev = optCur
                opts[optPrev].backPrev = -1
                optCur = optPrev
                optPrev--
                if (opt.hasPrev2) {
                    opts[optPrev].optPrev = optPrev + 1
                    opts[optPrev].backPrev = opt.backPrev2
                    optCur = optPrev
                    optPrev = opt.optPrev2
                }
            }
            val temp = opts[optPrev].optPrev
            opts[optPrev].optPrev = optCur
            optCur = optPrev
            optPrev = temp
        } while (optCur > 0)
        optCur = opts[0].optPrev
        back = opts[optCur].backPrev
        return optCur
    }

    private fun updateOptStateAndReps() {
        val opt = opts[optCur]
        var optPrev = opt.optPrev
        if (opt.prev1IsLiteral) {
            --optPrev
            if (opt.hasPrev2) {
                opt.state.set(opts[opt.optPrev2].state)
                if (opt.backPrev2 < REPS) opt.state.updateLongRep() else opt.state.updateMatch()
            } else {
                opt.state.set(opts[optPrev].state)
            }
            opt.state.updateLiteral()
        } else {
            opt.state.set(opts[optPrev].state)
        }
        if (optPrev == optCur - 1) {
            if (opt.backPrev == 0) opt.state.updateShortRep() else opt.state.updateLiteral()
            opts[optPrev].reps.copyInto(opt.reps)
        } else {
            updateRepsAfterMatch(opt, optPrev)
        }
    }

    private fun updateRepsAfterMatch(opt: Optimum, optPrevIn: Int) {
        var optPrev = optPrevIn
        val back: Int
        if (opt.prev1IsLiteral && opt.hasPrev2) {
            optPrev = opt.optPrev2
            back = opt.backPrev2
            opt.state.updateLongRep()
        } else {
            back = opt.backPrev
            if (back < REPS) opt.state.updateLongRep() else opt.state.updateMatch()
        }
        val prevReps = opts[optPrev].reps
        if (back < REPS) {
            opt.reps[0] = prevReps[back]
            for (rep in 1..back) opt.reps[rep] = prevReps[rep - 1]
            for (rep in back + 1 until REPS) opt.reps[rep] = prevReps[rep]
        } else {
            opt.reps[0] = back - REPS
            prevReps.copyInto(opt.reps, 1, 0, REPS - 1)
        }
    }

    private fun calc1BytePrices(pos: Int, posState: Int, avail: Int, anyRepPrice: Int) {
        var nextIsByte = false
        val opt = opts[optCur]
        val next = opts[optCur + 1]
        val curByte = lz.getByte(0)
        val matchByte = lz.getByte(opt.reps[0] + 1)
        val literalPrice = opt.price + literalEncoder.getPrice(curByte, matchByte, lz.getByte(1), pos, opt.state)
        if (literalPrice < next.price) {
            next.set1(literalPrice, optCur, -1)
            nextIsByte = true
        }
        if (matchByte == curByte && (next.optPrev == optCur || next.backPrev != 0)) {
            val shortRepPrice = shortRepPrice(anyRepPrice, opt.state, posState)
            if (shortRepPrice <= next.price) {
                next.set1(shortRepPrice, optCur, 0)
                nextIsByte = true
            }
        }
        if (!nextIsByte && matchByte != curByte && avail > MATCH_LEN_MIN) {
            priceLiteralThenRep0(pos, avail, literalPrice)
        }
    }

    /** A literal, then a match at the newest distance. */
    private fun priceLiteralThenRep0(pos: Int, avail: Int, literalPrice: Int) {
        val opt = opts[optCur]
        val len = lz.getMatchLen(1, opt.reps[0], minOf(niceLen, avail - 1))
        if (len < MATCH_LEN_MIN) return
        nextState.set(opt.state)
        nextState.updateLiteral()
        val price = literalPrice + longRepAndLenPrice(0, len, nextState, (pos + 1) and posMask)
        val i = optCur + 1 + len
        while (optEnd < i) opts[++optEnd].reset()
        if (price < opts[i].price) opts[i].set2(price, optCur, 0)
    }

    private fun calcLongRepPrices(pos: Int, posState: Int, avail: Int, anyRepPrice: Int): Int {
        var startLen = MATCH_LEN_MIN
        val lenLimit = minOf(avail, niceLen)
        for (rep in 0 until REPS) {
            val len = lz.getMatchLen(opts[optCur].reps[rep], lenLimit)
            if (len < MATCH_LEN_MIN) continue
            while (optEnd < optCur + len) opts[++optEnd].reset()
            val longRepPrice = longRepPrice(anyRepPrice, rep, opts[optCur].state, posState)
            for (i in len downTo MATCH_LEN_MIN) {
                val price = longRepPrice + repLenEncoder.getPrice(i, posState)
                if (price < opts[optCur + i].price) opts[optCur + i].set1(price, optCur, rep)
            }
            if (rep == 0) startLen = len + 1
            priceRepLiteralRep0(rep, len, pos, avail, longRepPrice + repLenEncoder.getPrice(len, posState))
        }
        return startLen
    }

    /** The repeated match [rep] of [len] costing [repPrice], a literal, then a match at that distance. */
    private fun priceRepLiteralRep0(rep: Int, len: Int, pos: Int, avail: Int, repPrice: Int) {
        val len2Limit = minOf(avail - len - 1, niceLen)
        if (len2Limit < MATCH_LEN_MIN) return
        val len2 = lz.getMatchLen(len + 1, opts[optCur].reps[rep], len2Limit)
        if (len2 < MATCH_LEN_MIN) return
        nextState.set(opts[optCur].state)
        nextState.updateLongRep()
        val price = repPrice + priceLiteralThenRep0At(pos, len, len2)
        val i = optCur + len + 1 + len2
        while (optEnd < i) opts[++optEnd].reset()
        if (price < opts[i].price) opts[i].set3(price, optCur, rep, len, 0)
    }

    /**
     * The literal [len] bytes ahead in [nextState], then a match of [len2] at the newest distance.
     * [nextState] is left as after the literal.
     */
    private fun priceLiteralThenRep0At(pos: Int, len: Int, len2: Int): Int {
        val curByte = lz.getByte(len, 0)
        val matchByte = lz.getByte(0)
        val prevByte = lz.getByte(len, 1)
        var price = literalEncoder.getPrice(curByte, matchByte, prevByte, pos + len, nextState)
        nextState.updateLiteral()
        price += longRepAndLenPrice(0, len2, nextState, (pos + len + 1) and posMask)
        return price
    }

    private fun calcNormalMatchPrices(pos: Int, posState: Int, avail: Int, anyMatchPrice: Int, startLen: Int) {
        if (matches.len[matches.count - 1] > avail) {
            matches.count = 0
            while (matches.len[matches.count] < avail) ++matches.count
            matches.len[matches.count++] = avail
        }
        if (matches.len[matches.count - 1] < startLen) return
        while (optEnd < optCur + matches.len[matches.count - 1]) opts[++optEnd].reset()
        val normalMatchPrice = normalMatchPrice(anyMatchPrice, opts[optCur].state)
        var match = 0
        while (startLen > matches.len[match]) ++match
        var len = startLen
        while (true) {
            val dist = matches.dist[match]
            val matchAndLenPrice = matchAndLenPrice(normalMatchPrice, dist, len, posState)
            if (matchAndLenPrice < opts[optCur + len].price) {
                opts[optCur + len].set1(matchAndLenPrice, optCur, dist + REPS)
            }
            if (len == matches.len[match]) {
                priceMatchLiteralRep0(dist, len, pos, avail, matchAndLenPrice)
                if (++match == matches.count) break
            }
            ++len
        }
    }

    /** The match at [dist] of [len] costing [matchPrice], a literal, then a match at that distance. */
    private fun priceMatchLiteralRep0(dist: Int, len: Int, pos: Int, avail: Int, matchPrice: Int) {
        val len2Limit = minOf(avail - len - 1, niceLen)
        if (len2Limit < MATCH_LEN_MIN) return
        val len2 = lz.getMatchLen(len + 1, dist, len2Limit)
        if (len2 < MATCH_LEN_MIN) return
        nextState.set(opts[optCur].state)
        nextState.updateMatch()
        val price = matchPrice + priceLiteralThenRep0At(pos, len, len2)
        val i = optCur + len + 1 + len2
        while (optEnd < i) opts[++optEnd].reset()
        if (price < opts[i].price) opts[i].set3(price, optCur, dist + REPS, len, 0)
    }
}
