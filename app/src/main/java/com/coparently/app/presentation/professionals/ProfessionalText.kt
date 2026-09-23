package com.coparently.app.presentation.professionals

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.professionals.ProfessionalAccessDuration
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalGrantPolicy
import com.coparently.app.domain.professionals.ProfessionalGrantStatus
import com.coparently.app.domain.professionals.ProfessionalRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The localized name of a profession. */
@StringRes
fun ProfessionalRole.labelRes(): Int = when (this) {
    ProfessionalRole.MEDIATOR -> R.string.professional_role_mediator
    ProfessionalRole.LAWYER -> R.string.professional_role_lawyer
    ProfessionalRole.GUARDIAN_AD_LITEM -> R.string.professional_role_guardian_ad_litem
    ProfessionalRole.THERAPIST -> R.string.professional_role_therapist
    ProfessionalRole.OTHER -> R.string.professional_role_other
}

/** The localized name of a grant length. */
@StringRes
fun ProfessionalAccessDuration.labelRes(): Int = when (this) {
    ProfessionalAccessDuration.MONTH -> R.string.professional_duration_month
    ProfessionalAccessDuration.QUARTER -> R.string.professional_duration_quarter
    ProfessionalAccessDuration.HALF_YEAR -> R.string.professional_duration_half_year
}

/** [millis] as a medium date in the reader's locale and zone. */
@Composable
fun rememberAccessDate(millis: Long): String = remember(millis) {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    )
}

/**
 * Where [grant] stands for [viewerUid], in one line. Every status names the end date or what it is
 * waiting for — an access with no visible end is the failure this feature exists to prevent.
 */
@Composable
fun grantStatusText(grant: ProfessionalGrant, viewerUid: String?): String {
    val until = rememberAccessDate(grant.expiresAtMillis)
    val status = ProfessionalGrantPolicy.statusFor(grant, viewerUid, System.currentTimeMillis())
    val isParent = viewerUid != null && viewerUid in grant.familyParents
    return when (status) {
        ProfessionalGrantStatus.EXPIRED -> stringResource(R.string.professional_status_expired, until)
        ProfessionalGrantStatus.ACTIVE -> stringResource(R.string.professional_status_active_until, until)
        ProfessionalGrantStatus.WAITING_FOR_YOU -> stringResource(R.string.professional_status_waiting_for_you)
        ProfessionalGrantStatus.WAITING_FOR_CO_PARENT -> if (isParent) {
            stringResource(R.string.professional_status_waiting_for_co_parent)
        } else {
            stringResource(R.string.professional_status_waiting_for_parents)
        }
    }
}

/** The family a professional's grant names, as "Alice and Bob", from the names copied into it. */
@Composable
fun professionalFamilyLabel(grant: ProfessionalGrant): String {
    val fallback = stringResource(R.string.professional_parent_fallback)
    val names = grant.familyParents.map { uid ->
        grant.parentNames[uid]?.takeIf { it.isNotBlank() } ?: fallback
    }
    return stringResource(R.string.professional_family_label, names.first(), names.last())
}

/** The name of whoever holds [slot] in [grant]'s family, or the generic fallback. */
@Composable
fun parentNameForSlot(grant: ProfessionalGrant, slot: String?): String =
    slot?.let { grant.parentNameForSlot(it) } ?: stringResource(R.string.professional_parent_fallback)
