package com.coparently.app.presentation.home

/**
 * What Home's Requests block counts (phase 2, spec §6.1; November 2026, board HomeG1): "N for you"
 * is [waitingOnMe], the Home tab's badge.
 *
 * @property waitingOnMe What waits on this parent's answer, the custody proposal included.
 * @property waitingOnThem What this parent sent that the co-parent has not answered.
 */
data class RequestsRowState(
    val waitingOnMe: Int = 0,
    val waitingOnThem: Int = 0
)
