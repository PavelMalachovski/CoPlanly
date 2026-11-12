package com.coparently.app.presentation.home

/**
 * What Home's Open requests card counts (phase 2, spec §6.1; redesign, November 2026).
 *
 * @property waitingOnMe What waits on this parent's answer, the custody proposal included.
 * @property waitingOnThem What this parent sent that the co-parent has not answered.
 */
data class RequestsRowState(
    val waitingOnMe: Int = 0,
    val waitingOnThem: Int = 0
)
