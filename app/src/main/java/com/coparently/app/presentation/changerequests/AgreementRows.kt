package com.coparently.app.presentation.changerequests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.support.SupportProposal
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.professionals.labelRes
import com.coparently.app.presentation.support.supportAmount
import com.coparently.app.presentation.support.supportMonth

/**
 * A child support proposal as one Requests row: who proposes what, opening Child support. The
 * proposer's own row carries Withdraw; the co-parent's is answered on that screen.
 */
@Composable
internal fun SupportRequestRow(
    proposal: SupportProposal,
    mine: Boolean,
    parentNames: ParentNames,
    onOpen: () -> Unit,
    onWithdraw: () -> Unit
) {
    val terms = proposal.terms
    val supporting = if (mine) {
        stringResource(
            R.string.request_support_mine,
            supportAmount(terms),
            terms.dayOfMonth,
            supportMonth(terms.fromMonth)
        )
    } else {
        stringResource(
            R.string.request_support_theirs,
            parentNames.labelForUid(proposal.proposedBy),
            supportAmount(terms),
            terms.dayOfMonth,
            supportMonth(terms.fromMonth)
        )
    }
    AgreementRow(
        stringResource(R.string.support_title),
        supporting,
        Icons.Default.Payments,
        onOpen,
        onWithdraw.takeIf { mine }
    )
}

/**
 * A professional's access waiting for a consent as one Requests row, opening Professionals. While
 * this parent has consented and the co-parent has not, the row carries Withdraw, which ends it.
 */
@Composable
internal fun ProfessionalRequestRow(
    grant: ProfessionalGrant,
    mine: Boolean,
    parentNames: ParentNames,
    onOpen: () -> Unit,
    onWithdraw: () -> Unit
) {
    val role = stringResource(grant.role.labelRes())
    val supporting = if (mine) {
        val waitingOn = grant.familyParents.firstOrNull { it !in grant.consents }
        stringResource(R.string.request_professional_mine, role, grant.name, parentNames.labelForUid(waitingOn))
    } else {
        stringResource(R.string.request_professional_theirs, role, grant.name)
    }
    AgreementRow(
        stringResource(R.string.request_professional_title),
        supporting,
        Icons.Default.Gavel,
        onOpen,
        onWithdraw.takeIf { mine }
    )
}

/** The parenting plan questions waiting on this parent, as one Requests row opening the plan. */
@Composable
internal fun PlanRequestRow(questionCount: Int, coParentName: String, onOpen: () -> Unit) {
    AgreementRow(
        stringResource(R.string.parenting_plan_title),
        pluralStringResource(R.plurals.request_plan_answered, questionCount, coParentName, questionCount),
        Icons.Default.Description,
        onOpen,
        onWithdraw = null
    )
}

/** One compact row in its own group: a title, what waits, and at most the sender's Withdraw. */
@Composable
private fun AgreementRow(
    title: String,
    supporting: String,
    icon: ImageVector,
    onOpen: () -> Unit,
    onWithdraw: (() -> Unit)?
) {
    SectionGroup {
        SectionRow(
            title = title,
            icon = icon,
            supporting = supporting,
            onClick = onOpen,
            trailing = onWithdraw?.let { withdraw ->
                {
                    TextButton(onClick = withdraw) { Text(stringResource(R.string.change_request_withdraw)) }
                }
            },
            stackTrailingAtLargeFont = true
        )
    }
}
