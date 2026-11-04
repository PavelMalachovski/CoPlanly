package com.coparently.app.presentation.professionals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coparently.app.domain.professionals.ProfessionalAccessDuration
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalGrantPolicy
import com.coparently.app.domain.professionals.ProfessionalGrantStatus
import com.coparently.app.resources.Res
import com.coparently.app.resources.professional_duration_half_year
import com.coparently.app.resources.professional_duration_month
import com.coparently.app.resources.professional_duration_quarter
import com.coparently.app.resources.professional_family_label
import com.coparently.app.resources.professional_parent_fallback
import com.coparently.app.resources.professional_status_active_until
import com.coparently.app.resources.professional_status_expired
import com.coparently.app.resources.professional_status_waiting_for_co_parent
import com.coparently.app.resources.professional_status_waiting_for_parents
import com.coparently.app.resources.professional_status_waiting_for_you
import com.coparently.app.utils.readerMediumDateAt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** The localized name of a grant length. */
fun ProfessionalAccessDuration.labelRes(): StringResource = when (this) {
    ProfessionalAccessDuration.MONTH -> Res.string.professional_duration_month
    ProfessionalAccessDuration.QUARTER -> Res.string.professional_duration_quarter
    ProfessionalAccessDuration.HALF_YEAR -> Res.string.professional_duration_half_year
}

/** [millis] as a medium date in the reader's locale and zone. */
@Composable
fun rememberAccessDate(millis: Long): String = remember(millis) { readerMediumDateAt(millis) }

/**
 * Where [grant] stands for [viewerUid], in one line. Every status names the end date or what it is
 * waiting for — an access with no visible end is the failure this feature exists to prevent.
 */
@Composable
fun grantStatusText(grant: ProfessionalGrant, viewerUid: String?): String {
    val until = rememberAccessDate(grant.expiresAtMillis)
    val status = ProfessionalGrantPolicy.statusFor(grant, viewerUid, Clock.System.now().toEpochMilliseconds())
    val isParent = viewerUid != null && viewerUid in grant.familyParents
    return when (status) {
        ProfessionalGrantStatus.EXPIRED -> stringResource(Res.string.professional_status_expired, until)
        ProfessionalGrantStatus.ACTIVE -> stringResource(Res.string.professional_status_active_until, until)
        ProfessionalGrantStatus.WAITING_FOR_YOU -> stringResource(Res.string.professional_status_waiting_for_you)
        ProfessionalGrantStatus.WAITING_FOR_CO_PARENT -> if (isParent) {
            stringResource(Res.string.professional_status_waiting_for_co_parent)
        } else {
            stringResource(Res.string.professional_status_waiting_for_parents)
        }
    }
}

/** The family a professional's grant names, as "Alice and Bob", from the names copied into it. */
@Composable
fun professionalFamilyLabel(grant: ProfessionalGrant): String {
    val fallback = stringResource(Res.string.professional_parent_fallback)
    val names = grant.familyParents.map { uid ->
        grant.parentNames[uid]?.takeIf { it.isNotBlank() } ?: fallback
    }
    return stringResource(Res.string.professional_family_label, names.first(), names.last())
}

/** The name of whoever holds [slot] in [grant]'s family, or the generic fallback. */
@Composable
fun parentNameForSlot(grant: ProfessionalGrant, slot: String?): String =
    slot?.let { grant.parentNameForSlot(it) } ?: stringResource(Res.string.professional_parent_fallback)
