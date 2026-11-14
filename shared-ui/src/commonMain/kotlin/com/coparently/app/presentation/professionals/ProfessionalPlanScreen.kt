package com.coparently.app.presentation.professionals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.coparently.app.domain.parentingplan.ParentingPlanCatalogue
import com.coparently.app.domain.parentingplan.ParentingPlanComparison
import com.coparently.app.domain.parentingplan.PlanQuestionStatus
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.PushedTopBar
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.parentingplan.PlanStrings
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_back
import com.coparently.app.resources.parenting_plan_disclaimer
import com.coparently.app.resources.parenting_plan_status_agreed
import com.coparently.app.resources.parenting_plan_title
import com.coparently.app.resources.professional_parent_fallback
import com.coparently.app.resources.professional_plan_answer_by
import com.coparently.app.resources.professional_plan_not_answered
import com.coparently.app.resources.professional_unavailable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The parenting plan as a professional reads it (MON-18): every question, both parents' answers,
 * and where they already agree. No sheet, no edit field and no tick — there is nothing here a
 * professional could change, and the rules refuse the write regardless.
 *
 * The disclaimer that this is not the Ministry of Justice's form stays on screen (CLAUDE.md item
 * 21): a mediator is precisely the reader who must know which document they are holding.
 *
 * @param onNavigateUp Returns to the professionals list.
 * @param viewModel Screen state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalPlanScreen(
    onNavigateUp: () -> Unit,
    viewModel: ProfessionalPlanViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = ScreenContainerColor,
        topBar = {
            PushedTopBar(
                title = stringResource(Res.string.parenting_plan_title),
                backLabel = stringResource(Res.string.common_back),
                onBack = onNavigateUp
            )
        }
    ) { padding ->
        when (val state = uiState) {
            ProfessionalPlanUiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            ProfessionalPlanUiState.Unavailable -> EmptyState(
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = stringResource(Res.string.professional_unavailable),
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            is ProfessionalPlanUiState.Ready -> PlanList(state, Modifier.padding(padding))
        }
    }
}

@Composable
private fun PlanList(state: ProfessionalPlanUiState.Ready, modifier: Modifier = Modifier) {
    val fallback = stringResource(Res.string.professional_parent_fallback)
    val firstName = state.grant.parentNames[state.grant.familyParents.first()]?.takeIf { it.isNotBlank() }
        ?: fallback
    val secondName = state.grant.parentNames[state.grant.familyParents.last()]?.takeIf { it.isNotBlank() }
        ?: fallback
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(
                    text = professionalFamilyLabel(state.grant),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(Res.string.parenting_plan_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(ParentingPlanCatalogue.sections, key = { it.id }) { section ->
            Column {
                PlanStrings.sectionTitle(section.id)?.let { GroupLabel(stringResource(it)) }
                SectionGroup {
                    section.questions.forEachIndexed { index, question ->
                        if (index > 0) Divider()
                        QuestionRow(question.id, state, firstName, secondName)
                    }
                }
            }
        }
    }
}

/** One question, both answers under it, and "Agreed" when each parent ticked the other's wording. */
@Composable
private fun QuestionRow(
    questionId: String,
    state: ProfessionalPlanUiState.Ready,
    firstName: String,
    secondName: String
) {
    val prompt = PlanStrings.questionPrompt(questionId) ?: return
    val notAnswered = stringResource(Res.string.professional_plan_not_answered)
    val first = state.first.answerTo(questionId) ?: notAnswered
    val second = state.second?.answerTo(questionId) ?: notAnswered
    val agreed = ParentingPlanComparison.statusOf(questionId, state.first, state.second) ==
        PlanQuestionStatus.AGREED
    SectionRow(
        title = stringResource(prompt),
        supporting = stringResource(Res.string.professional_plan_answer_by, firstName, first) + "\n" +
            stringResource(Res.string.professional_plan_answer_by, secondName, second),
        trailing = {
            if (agreed) {
                PillChip(
                    label = stringResource(Res.string.parenting_plan_status_agreed),
                    icon = Icons.Default.CheckCircle,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}
