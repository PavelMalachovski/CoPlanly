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
import androidx.compose.ui.text.style.TextAlign
import com.coparently.app.presentation.platform.LocalAppBuildValues
import com.coparently.app.presentation.platform.LocalOpenUrl
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.auth_legal_notice_age
import com.coparently.app.resources.auth_legal_notice_with_terms
import com.coparently.app.resources.privacy_policy_title
import com.coparently.app.resources.terms_of_service_title
import org.jetbrains.compose.resources.stringResource

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
    // The build's hosted addresses and the phone's browser (MON-24 phase 2, slice 11f): a link is
    // drawn only while its page is hosted and the phone can open it, and the terms clause only
    // with its link, so the screen never asks anyone to accept a text they cannot open.
    val buildValues = LocalAppBuildValues.current
    val openUrl = LocalOpenUrl.current
    val termsLink = buildValues.termsUrl?.takeIf { openUrl.available }
    val privacyLink = buildValues.privacyPolicyUrl?.takeIf { openUrl.available }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        Text(
            text = stringResource(
                if (termsLink != null) Res.string.auth_legal_notice_with_terms else Res.string.auth_legal_notice_age
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (termsLink != null || privacyLink != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.CenterHorizontally)) {
                if (termsLink != null) {
                    TextButton(onClick = { openUrl.open(termsLink) }) {
                        Text(stringResource(Res.string.terms_of_service_title))
                    }
                }
                if (privacyLink != null) {
                    TextButton(onClick = { openUrl.open(privacyLink) }) {
                        Text(stringResource(Res.string.privacy_policy_title))
                    }
                }
            }
        }
    }
}
