package com.coparently.app.presentation.professionals

import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.resources.Res
import com.coparently.app.resources.professional_role_guardian_ad_litem
import com.coparently.app.resources.professional_role_lawyer
import com.coparently.app.resources.professional_role_mediator
import com.coparently.app.resources.professional_role_other
import com.coparently.app.resources.professional_role_therapist
import org.jetbrains.compose.resources.StringResource

/**
 * The localized name of a profession. In `:shared-ui` since MON-24 phase 2, slice 11d, for the
 * Requests inbox's professional row; the Professionals screens, still in the app, read it through
 * Compose Multiplatform's `stringResource`.
 */
fun ProfessionalRole.labelRes(): StringResource = when (this) {
    ProfessionalRole.MEDIATOR -> Res.string.professional_role_mediator
    ProfessionalRole.LAWYER -> Res.string.professional_role_lawyer
    ProfessionalRole.GUARDIAN_AD_LITEM -> Res.string.professional_role_guardian_ad_litem
    ProfessionalRole.THERAPIST -> Res.string.professional_role_therapist
    ProfessionalRole.OTHER -> Res.string.professional_role_other
}
