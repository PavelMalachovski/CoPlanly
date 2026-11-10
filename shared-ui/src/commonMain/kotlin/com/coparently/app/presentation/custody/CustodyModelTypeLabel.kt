package com.coparently.app.presentation.custody

import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.resources.Res
import com.coparently.app.resources.custody_model_custom
import com.coparently.app.resources.custody_model_every_other_weekend
import com.coparently.app.resources.custody_model_three_four_four_three
import com.coparently.app.resources.custody_model_two_two_three
import com.coparently.app.resources.custody_model_week_on_week_off
import org.jetbrains.compose.resources.StringResource

/**
 * The translated name of a custody pattern.
 *
 * One mapping for every screen that names a pattern — the setup screen's picker, the onboarding
 * wizard's custody step and the calendar's "waiting" line — so a new [CustodyModelType] cannot be
 * labelled in one place and fall back to its English `displayName` in another. A Compose
 * Multiplatform [StringResource] since the calendar moved to `:shared-ui` (MON-24 phase 2, slice
 * 11c); the screens still in the app read it with Compose Multiplatform's `stringResource`.
 */
fun CustodyModelType.labelRes(): StringResource = when (this) {
    CustodyModelType.WEEK_ON_WEEK_OFF -> Res.string.custody_model_week_on_week_off
    CustodyModelType.EVERY_OTHER_WEEKEND -> Res.string.custody_model_every_other_weekend
    CustodyModelType.TWO_TWO_THREE -> Res.string.custody_model_two_two_three
    CustodyModelType.THREE_FOUR_FOUR_THREE -> Res.string.custody_model_three_four_four_three
    CustodyModelType.CUSTOM -> Res.string.custody_model_custom
}
