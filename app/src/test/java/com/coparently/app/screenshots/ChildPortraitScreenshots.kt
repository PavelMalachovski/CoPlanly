package com.coparently.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.childinfo.ChildPortrait
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.datetime.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A child's portrait control (part 5b) without a photo and with one. A screenshot loads no image
 * and has no signed-in viewer, so the "with photo" state draws its initial: what is checked is the
 * controls (Change and Remove instead of Add) and the privacy line.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = RobolectricTestApplication::class)
class ChildPortraitScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    @Test
    fun childPortrait() {
        snap("childinfo_portrait") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ChildPortrait(child = CHILD, onPick = {}, onRemove = {})
                ChildPortrait(child = CHILD.copy(photo = PHOTO), onPick = {}, onRemove = {})
            }
        }
    }

    companion object {
        private val STAMP = LocalDateTime(2026, 5, 1, 9, 0)

        private const val PHOTO = "ph1|child_photos/fam/mia/p.jpg|image/jpeg|1|aa"

        private val CHILD = ChildInfo(
            id = "mia",
            childName = "Mia",
            dateOfBirth = null,
            createdAt = STAMP,
            updatedAt = STAMP
        )

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
