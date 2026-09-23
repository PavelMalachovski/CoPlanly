package com.coparently.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.FamilySignal
import com.coparently.app.presentation.common.FamilySwitcherChip
import com.coparently.app.presentation.common.FamilySwitcherState
import com.coparently.app.presentation.common.FamilySwitcherViewModel
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.theme.ParentColors
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The top-bar family switcher (M-8) beside the two parent-dot chips the app draws in the same
 * place, in the [ScreenshotVariants.COLOUR_ONLY] set: every label is a person's name, so the
 * question is theme and palette, not translation.
 *
 * The switcher's ViewModel is a MockK stand-in holding two families, the smallest state at which
 * the chip renders at all (with one co-parent it draws nothing, by design), and news in the family
 * not on screen, so the chip carries its unread dot (M-8).
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class FamilySwitcherScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    @Test
    fun switcherChip() {
        val families = listOf(
            FamilyOption(familyId = "f1", partnerUid = ScreenshotFixtures.CO_PARENT_UID, partnerName = "Pavel"),
            FamilyOption(familyId = "f2", partnerUid = "u3", partnerName = "Marta")
        )
        val viewModel = mockk<FamilySwitcherViewModel> {
            every { state } returns MutableStateFlow(
                FamilySwitcherState(
                    families,
                    selectedFamilyId = "f1",
                    signals = mapOf("f2" to setOf(FamilySignal.CHAT))
                )
            )
        }
        snap("common_family_switcher") {
            Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
                FamilySwitcherChip(viewModel = viewModel)
                PillChip(label = "Olya", leadingDot = ParentColors.fill("mom"))
                PillChip(label = "Pavel", leadingDot = ParentColors.fill("dad"))
            }
        }
    }

    companion object {
        private val CHIP_GAP = 8.dp

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.COLOUR_ONLY)
    }
}
