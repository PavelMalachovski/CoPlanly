package com.coparently.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.coparently.app.R
import com.coparently.app.presentation.common.PrivacyPolicyLink
import com.coparently.app.presentation.common.TermsOfServiceLink
import com.coparently.app.presentation.theme.Spacing

/**
 * What a person agrees to by signing in or creating an account: that they are an adult, and —
 * once the terms are hosted — the terms of service, with links to them and to the privacy policy.
 *
 * Shown in both modes, because a first Google sign-in creates an account as surely as the sign-up
 * form does. The age line is always there: the terms admit adults only, and a service that never
 * says so has no answer for a teenager's account. The terms clause appears only with its link, so
 * the screen never asks anyone to accept a text they cannot open (`LEGAL-REVIEW-2026-09.md` L-12).
 *
 * @param modifier Applied to the column
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AuthLegalNotice(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val termsUrl = TermsOfServiceLink.url
    val privacyUrl = PrivacyPolicyLink.url
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        Text(
            text = stringResource(
                if (termsUrl != null) R.string.auth_legal_notice_with_terms else R.string.auth_legal_notice_age
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (termsUrl != null || privacyUrl != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.CenterHorizontally)) {
                if (termsUrl != null) {
                    TextButton(onClick = { TermsOfServiceLink.open(uriHandler) }) {
                        Text(stringResource(R.string.terms_of_service_title))
                    }
                }
                if (privacyUrl != null) {
                    TextButton(onClick = { PrivacyPolicyLink.open(uriHandler) }) {
                        Text(stringResource(R.string.privacy_policy_title))
                    }
                }
            }
        }
    }
}
