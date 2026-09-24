package com.coparently.app.presentation.custody

/**
 * The child the custody editor is scoped to (FAM-4), or none while it edits the family schedule.
 *
 * @property childId `ChildInfo.id`.
 * @property name What the parents call the child, for the title and the hint.
 */
data class ChildScope(val childId: String, val name: String)
