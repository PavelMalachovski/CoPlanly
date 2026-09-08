package com.coparently.app.presentation.onboarding

import com.coparently.app.domain.model.FamilyKind

/**
 * The wizard's steps, in the order they are walked.
 *
 * **The co-parent link comes first, before a single question is asked** (September 2026, an
 * owner decision). The wizard used to end with the invitation, which made sense for the first
 * parent to install the app and none at all for the second: they were walked through every
 * child, every allergy and the custody schedule, typed it all in, and only then linked to the
 * parent who had already entered the same things. Linking first lets the questionnaire open on
 * what the co-parent has already entered — the child step with their children, the split step
 * on the agreed ratio, the custody step on the shared schedule — so the second parent checks
 * rather than retypes. For the first parent nothing is lost: the step offers an invitation and
 * a "Not now", and the questionnaire that follows is the one they always had.
 *
 * [Custody] and [CoParent] do not render a form inside the wizard: they hand off to
 * `CustodySetupScreen` and `PairingScreen`, which already do those jobs and are reachable from
 * Settings anyway. They are steps here so the progress indicator tells the truth about how much
 * is left.
 *
 * **Not every step runs.** [Family] asks whether the family is co-parenting children, pets or
 * both, and the answer decides which of [Child], [Relatives] and [Pet] follow. The wizard used
 * to walk this enum by `ordinal ± 1` with `entries.size` as the progress denominator, which is
 * exactly what a conditional flow cannot do — see [stepsFor], which is the list to walk instead.
 */
enum class OnboardingStep {
    /**
     * Hands off to `PairingScreen`, and shows what the link brought back once there is one.
     *
     * First, so that everything after it can open pre-filled. Skippable — "Not now" — because
     * the first parent to install the app has nobody to link with yet, and a link is never a
     * gate on somebody's calendar.
     */
    CoParent,

    /** Explains what is about to be asked, and why. */
    Intro,

    /** Children, pets, or both. Decides which of the record steps below are asked. */
    Family,

    /** The parent's own details. The only step with a required field. */
    Profile,

    /**
     * Each child's name, date of birth, allergies and medical profile.
     *
     * A repeatable list, not one child: the wizard used to write exactly one record, so a
     * family with two could not say so here at all.
     */
    Child,

    /**
     * Emergency contacts, saved onto a child's record so both parents may edit them.
     *
     * They belong to **one** child, and with several the step asks which. A single flat list
     * filed every contact against whichever child was written first.
     */
    Relatives,

    /** Each pet's name and species, repeatable on the same terms as [Child]. */
    Pet,

    /**
     * How a shared expense divides between the two parents.
     *
     * Here rather than only in Settings because the reporter asked for it at registration, and
     * because it is genuinely easier to agree before there is a month of expenses to re-argue.
     * With the link made first, a second parent opens this step on the ratio the pair already
     * agreed, and moving the slider is a proposal the co-parent confirms — exactly what it is
     * in Settings.
     */
    Split,

    /** Hands off to `CustodySetupScreen`. Finishing here finishes onboarding. */
    Custody;

    /**
     * True when this step may be left without answering it.
     *
     * [Intro] is excluded because it asks for nothing — there is nothing to skip past, only a
     * Next. [Profile] is excluded because the parent's name is the one field the app genuinely
     * cannot work without: every event, expense and custody day is labelled with it and
     * `ParentLabels` has no honest fallback. [Family] is excluded because skipping it would
     * leave the wizard unable to decide which steps come next — it opens pre-answered with
     * children, so there is always something to move on with.
     *
     * Everything else the wizard asks for, medical details included, is collected for the
     * parent's own benefit and must never become a gate on their calendar. That includes the
     * co-parent link: a parent whose co-parent does not use the app yet still gets a calendar.
     */
    val isSkippable: Boolean get() = this != Intro && this != Profile && this != Family

    companion object {
        /**
         * The steps this wizard will actually walk, given what the family co-parents.
         *
         * @param caresFor The answer to [Family]; an empty set is treated as children, which is
         *   what the step opens pre-answered with.
         */
        fun stepsFor(caresFor: Set<FamilyKind>): List<OnboardingStep> {
            val kinds = caresFor.ifEmpty { setOf(FamilyKind.CHILDREN) }
            return buildList {
                add(CoParent)
                add(Intro)
                add(Family)
                add(Profile)
                if (FamilyKind.CHILDREN in kinds) {
                    add(Child)
                    add(Relatives)
                }
                if (FamilyKind.PETS in kinds) add(Pet)
                add(Split)
                add(Custody)
            }
        }
    }
}
