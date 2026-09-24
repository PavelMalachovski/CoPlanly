package com.coparently.app.e2e

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.R
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.e2e.EmulatorEnvironment.step
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.UUID

/**
 * The UI tour's first-run half: the onboarding wizard, as the **second** parent meets it — Alice has
 * just been linked with Bob, who set up the children, so the steps open on what the link brought
 * back (CLAUDE.md item 22). Same switches, variant and output as [UiTourTest]; its screenshots are
 * numbered from [FIRST_NUMBER] and written to the manifest's `onboarding` section.
 *
 * The walk presses Next while it is enabled and stops at the first step that needs an answer the
 * tour does not give, or at the last one — it never presses Finish, so nothing is saved.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UiTourOnboardingTest : AliceOnScreenTest() {

    private val variant by lazy { UiTourVariant.current() }
    private val localized by lazy { variant.localized(context) }
    private val driver by lazy { UiTourDriver(composeTestRule, tabBar) }
    private val camera by lazy { UiTourCamera(composeTestRule, SECTION, FIRST_NUMBER) {} }

    override val opensOnHome: Boolean = false

    override val strings: Context
        get() = localized

    override fun assumeRunnable() = EmulatorEnvironment.assumeUiTour()

    override fun beforeLaunch() = runBlocking {
        variant.applyTo(preferencesRepository)
        // Bob's records, for the wizard's "from your co-parent" rows. Best effort, like the tour's seed.
        runCatching {
            bob.childInfoRepository.upsertChildInfo(bobsChild())
        }
        Unit
    }

    @Test
    fun onboarding() {
        runBlocking { runCatching { syncService.performFullSync() } }
        val next = hasText(string(R.string.onboarding_next)) and hasClickAction()
        val last = hasText(string(R.string.onboarding_finish)) and hasClickAction()
        for (index in 1..MAX_STEPS) {
            camera.shot("onboarding_step_$index") {
                driver.await("an onboarding step", next or last, WIZARD_WAIT_MS)
                driver.linger()
            }
            if (!driver.present(next and isEnabled())) {
                step("tour: onboarding stops at step $index")
                return
            }
            driver.press("Next", next and isEnabled())
        }
    }

    private fun bobsChild() = ChildInfo(
        id = UUID.randomUUID().toString(),
        childName = UiTourSeed.EMMA,
        dateOfBirth = LocalDateTime.of(BORN_YEAR, BORN_MONTH, BORN_DAY, 0, 0),
        allergies = listOf("Peanuts"),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        createdByFirebaseUid = bob.uid,
        lastModifiedBy = bob.uid
    )

    private companion object {
        const val SECTION = "onboarding"
        const val FIRST_NUMBER = 80
        const val MAX_STEPS = 12
        const val WIZARD_WAIT_MS = 45_000L
        const val BORN_YEAR = 2016
        const val BORN_MONTH = 4
        const val BORN_DAY = 12
    }
}
