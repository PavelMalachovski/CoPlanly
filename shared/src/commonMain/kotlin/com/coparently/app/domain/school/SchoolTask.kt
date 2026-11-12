package com.coparently.app.domain.school

import kotlinx.datetime.LocalDate

/**
 * One homework or assignment from a school's timeline (EduPage), due on a day.
 *
 * @property id The school system's id for the timeline entry; with the system it makes the
 *   imported event's identity.
 * @property date The day it is due.
 * @property title What the school called it.
 * @property description The longer text, or null.
 */
data class SchoolTask(
    val id: String,
    val date: LocalDate,
    val title: String,
    val description: String?
)
