package com.coparently.app.presentation.onboarding

import com.coparently.app.domain.model.FamilyKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The order the wizard walks, which is a product decision rather than an accident of the enum.
 *
 * The co-parent link comes first so that a second parent inherits what the first one entered
 * instead of retyping it; the custody schedule comes last because finishing there finishes the
 * wizard. Everything a family does not have is left out of the walk rather than skipped past.
 */
class OnboardingStepTest {

    @Test
    fun `the co-parent link is the first step and the custody schedule the last`() {
        val steps = OnboardingStep.stepsFor(setOf(FamilyKind.CHILDREN, FamilyKind.PETS))
        assertEquals(OnboardingStep.CoParent, steps.first())
        assertEquals(OnboardingStep.Custody, steps.last())
        assertEquals(
            listOf(
                OnboardingStep.CoParent,
                OnboardingStep.Intro,
                OnboardingStep.Family,
                OnboardingStep.Profile,
                OnboardingStep.Child,
                OnboardingStep.Relatives,
                OnboardingStep.Pet,
                OnboardingStep.Split,
                OnboardingStep.Custody
            ),
            steps
        )
    }

    @Test
    fun `a pets-only family is never asked about children`() {
        val steps = OnboardingStep.stepsFor(setOf(FamilyKind.PETS))
        assertFalse(OnboardingStep.Child in steps)
        assertFalse(OnboardingStep.Relatives in steps)
        assertTrue(OnboardingStep.Pet in steps)
    }

    @Test
    fun `an unanswered family reads as children, so the walk always has something to ask`() {
        val steps = OnboardingStep.stepsFor(emptySet())
        assertTrue(OnboardingStep.Child in steps)
        assertFalse(OnboardingStep.Pet in steps)
    }

    @Test
    fun `the link can be declined, the name and the family answer cannot`() {
        // A parent whose co-parent does not use the app yet still gets a calendar.
        assertTrue(OnboardingStep.CoParent.isSkippable)
        assertFalse(OnboardingStep.Intro.isSkippable)
        assertFalse(OnboardingStep.Profile.isSkippable)
        assertFalse(OnboardingStep.Family.isSkippable)
        assertTrue(OnboardingStep.Custody.isSkippable)
    }
}
