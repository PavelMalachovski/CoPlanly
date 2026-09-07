package com.coparently.app.presentation.custody

import androidx.annotation.StringRes
import com.coparently.app.R
import com.coparently.app.domain.model.CustodyModelType

/**
 * The translated name of a custody pattern.
 *
 * One mapping for every screen that names a pattern — the setup screen's picker and the
 * onboarding wizard's custody step — so a new [CustodyModelType] cannot be labelled in one place
 * and fall back to its English `displayName` in another.
 */
@StringRes
fun CustodyModelType.labelRes(): Int = when (this) {
    CustodyModelType.WEEK_ON_WEEK_OFF -> R.string.custody_model_week_on_week_off
    CustodyModelType.EVERY_OTHER_WEEKEND -> R.string.custody_model_every_other_weekend
    CustodyModelType.TWO_TWO_THREE -> R.string.custody_model_two_two_three
    CustodyModelType.THREE_FOUR_FOUR_THREE -> R.string.custody_model_three_four_four_three
    CustodyModelType.CUSTOM -> R.string.custody_model_custom
}
