package com.coparently.app.domain.changerequests

/**
 * Where a request stands against its clock (phase 3a, spec §4.1): exactly one at any moment,
 * derived by [RequestClock.stateOf] and never stored.
 *
 * A request that was answered or withdrawn is decided by its own record, before the clock is asked.
 */
enum class ClockState {
    /** Sent, not answered, before the deadline: the answering parent can answer. */
    OPEN,

    /**
     * The deadline has passed with no answer, and the proposer can still give a last chance. The
     * next move is the proposer's, so the answering parent has no buttons.
     */
    NO_ANSWER_LAST_CHANCE_POSSIBLE,

    /** The proposer gave a last chance and it has not run out: the answering parent can answer again. */
    LAST_CHANCE,

    /** Nobody answered, and there is no more time to: the request is over. */
    NO_ANSWER
}

/**
 * The one decision behind every card, banner and background step that has a deadline (phase 3a,
 * spec §4.1 and §4.4): which [ClockState] a request is in, and what a last chance would give.
 *
 * Nothing here reads the time. It is always passed in, so every screen showing the same moment
 * shows the same state, and a test can stand at any moment it likes.
 *
 * **The arithmetic.** At the deadline the answering parent's time runs out. The proposer can then
 * give one last chance, once: the button is offered for a day after the deadline
 * ([BUTTON_MILLIS]), and what it gives is a day to answer ([LAST_CHANCE_MILLIS]). A request may
 * also have a cap, the first moment it would change something, and a last chance must not outlive
 * the thing it decides:
 * - the button is offered while `answerBy <= now < buttonUntil`, where
 *   `buttonUntil = min(answerBy + 24 h, cap - 2 h)`. Two hours is the margin before the cap
 *   ([CAP_MARGIN_MILLIS]) plus the shortest last chance worth offering ([MIN_LAST_CHANCE_MILLIS]);
 * - pressing it gives `min(24 h, cap - 1 h - now)`, which, because the button ends two hours before
 *   the cap, is always more than an hour;
 * - the request ends where the last chance ends, or, when none was given, at the later of the
 *   deadline and the button's end. A cap under two hours past the deadline therefore offers no
 *   button and ends the request at the deadline.
 *
 * The end is at most two days after the deadline. That bound matters for a custody proposal: while
 * it is pending it blocks the other parent's own proposals, so the proposer's decision must not
 * hang either.
 */
object RequestClock {

    /** How long a last chance runs when the cap does not shorten it: 24 hours. */
    const val LAST_CHANCE_MILLIS: Long = 86_400_000

    /** How long after the deadline the proposer can still give a last chance: 24 hours. */
    const val BUTTON_MILLIS: Long = 86_400_000

    /** A last chance ends this long before the cap: one hour. */
    const val CAP_MARGIN_MILLIS: Long = 3_600_000

    /** A last chance shorter than this is not offered: one hour. */
    const val MIN_LAST_CHANCE_MILLIS: Long = 3_600_000

    /**
     * The state of the request [clock] times, at [nowMillis]. The first check that applies decides:
     * 1. a stored outcome is final, whatever the time;
     * 2. before the deadline, the request is open;
     * 3. a last chance that was given runs to its end, and then the request is over;
     * 4. otherwise the proposer's button is offered while it still can be, and the request is over
     *    when it cannot.
     */
    fun stateOf(clock: RequestDeadline, nowMillis: Long): ClockState {
        val lastChanceUntil = clock.lastChanceUntilMillis
        return when {
            clock.outcome != null -> ClockState.NO_ANSWER
            nowMillis < clock.answerByMillis -> ClockState.OPEN
            lastChanceUntil != null ->
                if (nowMillis < lastChanceUntil) ClockState.LAST_CHANCE else ClockState.NO_ANSWER
            lastChanceWindowMillis(clock, nowMillis) != null -> ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE
            else -> ClockState.NO_ANSWER
        }
    }

    /**
     * What pressing the last-chance button at [nowMillis] would give the answering parent, or null
     * when the button is not offered: before the deadline, after [buttonUntilMillis], once a last
     * chance was given (the button never comes back), or once the clock was closed. Otherwise a day
     * or what is left up to an hour before the cap, and never less than an hour.
     */
    fun lastChanceWindowMillis(clock: RequestDeadline, nowMillis: Long): Long? {
        val unused = clock.outcome == null && clock.lastChanceUntilMillis == null
        val inButtonTime = nowMillis in clock.answerByMillis until buttonUntilMillis(clock)
        if (!unused || !inButtonTime) return null
        val cap = clock.capMillis ?: return LAST_CHANCE_MILLIS
        return minOf(LAST_CHANCE_MILLIS, cap - CAP_MARGIN_MILLIS - nowMillis)
    }

    /**
     * The moment the button stops being offered: a day after the deadline, or two hours before the
     * cap if that is sooner. A cap under two hours past the deadline puts it before the deadline
     * itself, which means the button was never offered.
     */
    fun buttonUntilMillis(clock: RequestDeadline): Long {
        val byTime = clock.answerByMillis + BUTTON_MILLIS
        val cap = clock.capMillis ?: return byTime
        return minOf(byTime, cap - CAP_MARGIN_MILLIS - MIN_LAST_CHANCE_MILLIS)
    }

    /**
     * The moment the request is over if nobody answers: where the last chance ends when one was
     * given, otherwise the later of the deadline and [buttonUntilMillis].
     */
    fun endMillis(clock: RequestDeadline): Long =
        clock.lastChanceUntilMillis ?: maxOf(clock.answerByMillis, buttonUntilMillis(clock))

    /**
     * Whether [clock] is the clock of a record written by [authorUid] to [otherUid].
     *
     * A phone acts on a clock only when it names the record's author as its proposer and the other
     * parent as its responder (spec §5.1). A clock that does not is ignored, as if the request had
     * none: the other parent can read these documents, and must never be able to set the author's
     * deadline or close it early.
     */
    fun governs(clock: RequestDeadline, authorUid: String, otherUid: String): Boolean =
        clock.proposerUid == authorUid && clock.responderUid == otherUid
}

/**
 * A clock seen at one moment: what every card and every background step reads, so that all of them
 * answer for the same instant.
 *
 * @property clock The request's clock.
 * @property nowMillis The moment it is seen at.
 */
data class TimedRequest(val clock: RequestDeadline, val nowMillis: Long) {

    /** The request's state at [nowMillis]. */
    val state: ClockState get() = RequestClock.stateOf(clock, nowMillis)

    /** What pressing the last-chance button now would give, or null when it is not offered. */
    val lastChanceWindowMillis: Long? get() = RequestClock.lastChanceWindowMillis(clock, nowMillis)

    /** The first planned reminder still ahead of [nowMillis], or null when none is. */
    val nextReminderMillis: Long? get() = clock.reminderTimesMillis.filter { it > nowMillis }.minOrNull()

    /**
     * Whether the request is waiting on the answering parent: it is open, or in its last chance.
     * In the other two states the next move is the proposer's, or nobody's.
     */
    val waitsOnResponder: Boolean get() = state == ClockState.OPEN || state == ClockState.LAST_CHANCE
}

/**
 * The clocks of one family at one moment, by id: one value for a screen's cards to read, so none of
 * them asks the time itself and they all agree. The default holds no clock, which is how a request
 * without one looks.
 *
 * @property byId The clocks, by their document id.
 * @property nowMillis The moment they were read.
 */
data class ClocksAt(
    val byId: Map<String, RequestDeadline> = emptyMap(),
    val nowMillis: Long = 0L
) {

    /**
     * The clock with this id, seen at [nowMillis]; null when there is none, and when there is no id
     * — which is what a request that cannot name its clock has ([RequestDeadlineId.forSwap] and
     * [RequestDeadlineId.forEvent] answer null for one).
     */
    fun timed(id: String?): TimedRequest? =
        id?.let { byId[it] }?.let { TimedRequest(it, nowMillis) }

    companion object {
        /** The clocks of [clocks], by id, seen at [nowMillis]. */
        fun of(clocks: List<RequestDeadline>, nowMillis: Long): ClocksAt =
            ClocksAt(clocks.associateBy { it.id }, nowMillis)
    }
}
