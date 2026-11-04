package com.coparently.app.domain.custody

import com.coparently.app.domain.model.CustodyModel

/**
 * What saving a first schedule from onboarding did (new-user review, October 2026).
 *
 * The onboarding step can only *activate* a schedule, never propose one: an invited parent whose
 * Next quietly sent a proposal against the pair's agreed schedule is the one thing that step must
 * not do. So the save answers with what it found instead of falling back to a proposal.
 */
sealed interface FirstSchedule {

    /** The schedule is this family's now: there was no pair, or the pair had none. */
    data object Activated : FirstSchedule

    /**
     * The pair already shares a schedule, so nothing was written; the step shows [model] instead.
     *
     * @property model The schedule the pair agreed.
     */
    data class SharedExists(val model: CustodyModel) : FirstSchedule

    /** Whether the pair shares a schedule could not be read, so nothing was written. */
    data object Unavailable : FirstSchedule
}
