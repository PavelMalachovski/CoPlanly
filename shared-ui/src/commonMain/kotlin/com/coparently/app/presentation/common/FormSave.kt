package com.coparently.app.presentation.common

/**
 * A form's Save as the iPhone's bar carries it: the label and the state of the form's own Save
 * button (its `StickyActionBar` or the button at the form's end), which Android keeps drawing.
 *
 * @property label The button's label, already resolved, e.g. "Save event"
 * @property onClick The save
 * @property enabled Exactly when the form's own button can be pressed: `enabled && !busy`
 */
data class FormSave(val label: String, val onClick: () -> Unit, val enabled: Boolean)
