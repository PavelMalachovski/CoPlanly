package com.coparently.app.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.coparently.app.presentation.common.HeroIcon
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.new_account_ask_body
import com.coparently.app.resources.new_account_ask_new
import com.coparently.app.resources.new_account_ask_retry
import com.coparently.app.resources.new_account_ask_title
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

/**
 * "Already use CoPlanly?" (MON-24 phase 4, part A, board 3), drawn before onboarding's first step
 * for an account a Google or Apple sign-in just created ([NewAccountAskViewModel.ask]).
 *
 * "This is a new account" answers and lets the wizard begin; "Sign out and try again" deletes the
 * empty account and signs out, then [onSignedOut] takes the parent back to sign-in. While that runs
 * both buttons wait; a failure is a snackbar and the question stays.
 *
 * @param viewModel The question's state and its two answers
 * @param onSignedOut Leaves for the sign-in screen once the new account is gone
 */
@Composable
fun NewAccountQuestion(viewModel: NewAccountAskViewModel, onSignedOut: () -> Unit) {
    val working by viewModel.working.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val textResolver = rememberUiTextResolver()
    val leave by rememberUpdatedState(onSignedOut)

    LaunchedEffect(viewModel) {
        viewModel.signedOut.first { it }
        leave()
    }
    LaunchedEffect(viewModel) {
        viewModel.errors.collect { message -> snackbarHostState.showSnackbar(message.asString(textResolver)) }
    }

    Scaffold(
        containerColor = ScreenContainerColor,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.L, Alignment.CenterVertically)
        ) {
            HeroIcon(Icons.Default.PersonAdd)
            Text(
                text = stringResource(Res.string.new_account_ask_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(Res.string.new_account_ask_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = viewModel::keepNewAccount,
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.new_account_ask_new))
            }
            OutlinedButton(
                onClick = viewModel::signOutAndRetry,
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.new_account_ask_retry))
            }
        }
    }
}
