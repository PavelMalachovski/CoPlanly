package com.coparently.app.presentation.parentingplan

import androidx.compose.runtime.Composable
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.resources.Res
import com.coparently.app.resources.plan_citation_changed
import com.coparently.app.resources.plan_citation_changed_short
import com.coparently.app.resources.plan_citation_current
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The parenting plan's wording the calendar and the requests share (MON-21), out of the app's
 * `PlanReferenceCard.kt` since the calendar moved to `:shared-ui` (MON-24 phase 2, slice 11c); the
 * card itself stays with the plan's screens. A file name of its own: two modules' files of one name
 * in one package make one JVM class.
 */

/**
 * The line a proposal card shows about where the proposal came from (MON-21), or null when there
 * is nothing to say — no citation, one this build cannot read, or a question it has no wording
 * for. Never an error: a missing citation is simply an older build's proposal.
 *
 * The one wording of a [CitationStatus]: the inbox card and Home's proposal dialog both print this,
 * from the same `ChangeRequestViewModel.pendingProposalCitation`, so they cannot disagree.
 */
@Composable
fun planCitationLine(citation: CitationStatus): String? =
    citationLine(citation, changed = Res.string.plan_citation_changed)

/**
 * [planCitationLine] for a one-line surface — the calendar's proposal banner. The same status and
 * the same "current" wording; "changed since" leads with the change, so an ellipsis on a narrow
 * screen cuts the question, never the fact that the answer moved.
 */
@Composable
fun planCitationShortLine(citation: CitationStatus): String? =
    citationLine(citation, changed = Res.string.plan_citation_changed_short)

@Composable
private fun citationLine(citation: CitationStatus, changed: StringResource): String? {
    val (questionId, current) = when (citation) {
        is CitationStatus.Current -> citation.questionId to true
        is CitationStatus.Changed -> citation.questionId to false
        CitationStatus.None -> return null
    }
    val prompt = PlanStrings.questionPrompt(questionId)?.let { stringResource(it) } ?: return null
    return stringResource(if (current) Res.string.plan_citation_current else changed, prompt)
}

/**
 * The co-parent's name for the card, or the "your co-parent" fallback while none is known — the
 * same resolution the seasonal section already uses, never a role word.
 */
fun ParentNames.coParentLabel(): String =
    parents.coParent?.let { labelForUid(it.uid) } ?: coParentFallback
