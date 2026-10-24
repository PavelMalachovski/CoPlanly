package com.coparently.app.presentation.onboarding

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The order the wizard walks, which is a product decision rather than an accident of the enum.
 *
 * Four steps since the new-user review (October 2026, owner decision): the co-parent link first,
 * so a second parent opens on what the first one set up; notifications; a name and a country; and
 * the custody schedule with its month preview, which is what a parent installs the app for. The
 * children, contacts, pets and the split are asked later, from the Family tab.
 */
class OnboardingStepTest {

    @Test
    fun `the wizard is the link, notifications, the profile and the schedule, in that order`() {
        assertEquals(
            listOf(
                OnboardingStep.CoParent,
                OnboardingStep.Notifications,
                OnboardingStep.Profile,
                OnboardingStep.Custody
            ),
            OnboardingStep.steps
        )
    }

    @Test
    fun `only the name cannot be skipped`() {
        // A parent whose co-parent does not use the app yet still gets a calendar.
        assertTrue(OnboardingStep.CoParent.isSkippable)
        assertTrue(OnboardingStep.Notifications.isSkippable, "a parent may say no to notifications")
        assertFalse(OnboardingStep.Profile.isSkippable)
        assertTrue(OnboardingStep.Custody.isSkippable)
    }
}
