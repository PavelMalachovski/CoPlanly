package com.coparently.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.common.BrandHeader
import com.coparently.app.presentation.common.FamilyMember
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.datetime.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's brand header with no members, two children, and three children and a pet (the last two
 * fold into "+2", in the room three take, as on the Calendar), over the standard variant matrix.
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandHeader(members = emptyList(), onSettingsClick = {})
                BrandHeader(members = MEMBERS.take(2), onSettingsClick = {})
                BrandHeader(members = MEMBERS, onSettingsClick = {})
            }
        }
    }

    companion object {
        private val STAMP = LocalDateTime(2026, 5, 1, 9, 0)

        /** Three children and a pet: the fourth folds into "+2" with the third. */
        private val MEMBERS = listOf("Mia", "Leo", "Eva").map {
            val child = ChildInfo(id = it, childName = it, dateOfBirth = null, createdAt = STAMP, updatedAt = STAMP)
            FamilyMember(ref = FamilyMemberRef.Child(it), name = it, child = child)
        } + FamilyMember(ref = FamilyMemberRef.Pet("Max"), name = "Max")

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
