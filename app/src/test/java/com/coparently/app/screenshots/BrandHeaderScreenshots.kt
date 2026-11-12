package com.coparently.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.common.BrandHeader
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.datetime.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The start screen's brand header with no children, two and four (the fourth folds into "+1"),
 * over the standard variant matrix.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = RobolectricTestApplication::class)
class BrandHeaderScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    @Test
    fun brandHeader() {
        snap("common_brand_header") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BrandHeader(children = emptyList(), onSettingsClick = {})
                BrandHeader(children = CHILDREN.take(2), onSettingsClick = {})
                BrandHeader(children = CHILDREN, onSettingsClick = {})
            }
        }
    }

    companion object {
        private val STAMP = LocalDateTime(2026, 5, 1, 9, 0)

        private val CHILDREN = listOf("Mia", "Leo", "Max", "Eva").map {
            ChildInfo(id = it, childName = it, dateOfBirth = null, createdAt = STAMP, updatedAt = STAMP)
        }

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
